/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */
package com.tangosol.internal.net.topic.impl.paged;

import com.oracle.coherence.common.base.Exceptions;
import com.oracle.coherence.common.base.Logger;

import com.oracle.coherence.common.util.Options;
import com.oracle.coherence.common.util.Sentry;

import com.tangosol.coherence.config.Config;

import com.tangosol.internal.net.DebouncedFlowControl;

import com.tangosol.internal.net.topic.impl.paged.agent.CloseSubscriptionProcessor;
import com.tangosol.internal.net.topic.impl.paged.agent.CommitProcessor;
import com.tangosol.internal.net.topic.impl.paged.agent.DestroySubscriptionProcessor;
import com.tangosol.internal.net.topic.impl.paged.agent.EvictSubscriber;
import com.tangosol.internal.net.topic.impl.paged.agent.HeadAdvancer;
import com.tangosol.internal.net.topic.impl.paged.agent.PollProcessor;
import com.tangosol.internal.net.topic.impl.paged.agent.SeekProcessor;
import com.tangosol.internal.net.topic.impl.paged.agent.SubscriberHeartbeatProcessor;

import com.tangosol.internal.net.topic.impl.paged.model.Page;
import com.tangosol.internal.net.topic.impl.paged.model.PageElement;
import com.tangosol.internal.net.topic.impl.paged.model.PagedPosition;
import com.tangosol.internal.net.topic.impl.paged.model.SubscriberGroupId;
import com.tangosol.internal.net.topic.impl.paged.model.SubscriberInfo;
import com.tangosol.internal.net.topic.impl.paged.model.Subscription;

import com.tangosol.io.Serializer;

import com.tangosol.net.CacheService;
import com.tangosol.net.Cluster;
import com.tangosol.net.DistributedCacheService;
import com.tangosol.net.FlowControl;
import com.tangosol.net.MemberEvent;
import com.tangosol.net.MemberListener;
import com.tangosol.net.NamedCache;
import com.tangosol.net.PartitionedService;

import com.tangosol.net.events.EventDispatcher;
import com.tangosol.net.events.EventDispatcherAwareInterceptor;

import com.tangosol.net.events.partition.cache.EntryEvent;
import com.tangosol.net.events.partition.cache.PartitionedCacheDispatcher;

import com.tangosol.net.topic.NamedTopic;
import com.tangosol.net.topic.Position;
import com.tangosol.net.topic.Subscriber;
import com.tangosol.net.topic.TopicException;

import com.tangosol.util.AbstractMapListener;
import com.tangosol.util.Base;
import com.tangosol.util.Binary;
import com.tangosol.util.CircularArrayList;
import com.tangosol.util.ExternalizableHelper;
import com.tangosol.util.Filter;
import com.tangosol.util.Filters;
import com.tangosol.util.Gate;
import com.tangosol.util.InvocableMapHelper;
import com.tangosol.util.LongArray;
import com.tangosol.util.MapEvent;
import com.tangosol.util.MapListener;
import com.tangosol.util.ServiceEvent;
import com.tangosol.util.ServiceListener;
import com.tangosol.util.SparseArray;
import com.tangosol.util.TaskDaemon;
import com.tangosol.util.ThreadGateLite;
import com.tangosol.util.ValueExtractor;

import com.tangosol.util.aggregator.ComparableMin;
import com.tangosol.util.aggregator.GroupAggregator;
import com.tangosol.util.aggregator.LongMin;

import com.tangosol.util.extractor.ReflectionExtractor;

import com.tangosol.util.filter.InKeySetFilter;

import com.tangosol.util.listener.SimpleMapListener;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import java.util.stream.Collectors;

/**
 * A subscriber of values from a paged topic.
 *
 * @author jk/mf 2015.06.15
 * @since Coherence 14.1.1
 */
@SuppressWarnings("rawtypes")
public class PagedTopicSubscriber<V>
    implements Subscriber<V>, AutoCloseable
    {
    // ----- constructors ---------------------------------------------------

    /**
     * Create a {@link PagedTopicSubscriber}.
     *
     * @param topic    the underlying {@link PagedTopic} that this subscriber is subscribed to
     * @param caches   the {@link PagedTopicCaches} managing the underlying topic data
     * @param options  the {@link Option}s controlling this {@link PagedTopicSubscriber}
     *
     * @throws NullPointerException if the {@code topic} or {@code caches} parameters are {@code null}
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <T> PagedTopicSubscriber(PagedTopic<?> topic, PagedTopicCaches caches, Option<? super T, V>... options)
        {
        f_topic  = Objects.requireNonNull(topic);
        m_caches = Objects.requireNonNull(caches, "The TopicCaches parameter cannot be null");

        Options<Subscriber.Option> optionsMap = Options.from(Subscriber.Option.class, options);
        Name                       nameOption = optionsMap.get(Name.class, null);
        String                     sName      = nameOption == null ? null : nameOption.getName();

        f_fAnonymous                 = sName == null;
        m_listenerGroupDeactivation  = new GroupDeactivationListener();
        m_listenerChannelAllocation  = new ChannelListener();
        f_serializer                 = m_caches.getSerializer();
        f_listenerNotification       = new SimpleMapListener<>().synchronous().addDeleteHandler(evt -> onChannelPopulatedNotification((int[]) evt.getOldValue()));
        f_gate                       = new ThreadGateLite<>();

        ChannelOwnershipListeners<T> listeners = optionsMap.get(ChannelOwnershipListeners.class, ChannelOwnershipListeners.none());
        m_aChannelOwnershipListener = listeners.getListeners().toArray(new ChannelOwnershipListener[0]);

        CacheService cacheService = m_caches.getCacheService();
        Cluster      cluster      = cacheService.getCluster();

        f_fCompleteOnEmpty   = optionsMap.contains(CompleteOnEmpty.class);
        f_nNotificationId    = System.identityHashCode(this); // used even if we don't wait to avoid endless channel scanning
        f_filterNotification = new InKeySetFilter<>(/*filter*/ null, m_caches.getPartitionNotifierSet(f_nNotificationId));
        f_nId                = f_fAnonymous ? 0 : createId(f_nNotificationId, cluster.getLocalMember().getId());
        f_subscriberGroupId  = f_fAnonymous ? SubscriberGroupId.anonymous() : SubscriberGroupId.withName(sName);
        f_key                = new SubscriberInfo.Key(f_subscriberGroupId, f_nId);

        Filtered filtered = optionsMap.get(Filtered.class);
        f_filter = filtered == null ? null : filtered.getFilter();

        Convert convert = optionsMap.get(Convert.class);
        f_fnConverter = convert == null ? null : convert.getFunction();

        f_daemon = new TaskDaemon("Subscriber-" + m_caches.getTopicName() + "-" + f_nId);
        f_daemon.start();

        long cBacklog = cluster.getDependencies().getPublisherCloggedCount();
        f_backlog            = new DebouncedFlowControl((cBacklog * 2) / 3, cBacklog);
        f_queueReceiveOrders = new BatchingOperationsQueue<>(this::trigger, 1,
                                        f_backlog, v -> 1, BatchingOperationsQueue.Executor.fromTaskDaemon(f_daemon));

        int cChannel = m_caches.getChannelCount();
        int cPart    = m_caches.getPartitionCount();

        f_setPolledChannels = new BitSet(cChannel);
        f_setHitChannels    = new BitSet(cChannel);

        f_aChannel = new Channel[cChannel];
        for (int nChannel = 0; nChannel < cChannel; ++nChannel)
            {
            f_aChannel[nChannel] = new Channel();
            f_aChannel[nChannel].subscriberPartitionSync = Subscription.createSyncKey(f_subscriberGroupId, nChannel,  cPart);
            }

        registerChannelAllocationListener();
        registerDeactivationListener();

        ensureConnected();

        // Note: post construction this implementation must be fully async
        }

    // ----- accessors ------------------------------------------------------

    /**
     * Returns the subscribers unique identifier.
     *
     * @return the subscribers unique identifier
     */
    public long getId()
        {
        return f_nId;
        }

    /**
     * Returns this subscriber's key.
     *
     * @return  this subscriber's key
     */
    public SubscriberInfo.Key getKey()
        {
        return f_key;
        }

    /**
     * Returns {@code true} if this is an anonymous subscriber,
     * or {@code false} if this subscriber is in a group.
     *
     * @return {@code true} if this is an anonymous subscriber,
     *         or {@code false} if this subscriber is in a group
     */
    public boolean isAnonymous()
        {
        return f_fAnonymous;
        }

    // ----- Subscriber methods ---------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <T> NamedTopic<T> getNamedTopic()
        {
        return (NamedTopic<T>) f_topic;
        }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Element<V>> receive()
        {
        ensureActive();
        return (CompletableFuture<Element<V>>) f_queueReceiveOrders.add(Request.SINGLE);
        }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<Element<V>>> receive(int cBatch)
        {
        ensureActive();
        return (CompletableFuture<List<Element<V>>>) f_queueReceiveOrders.add(new Request(true, cBatch));
        }

    @Override
    public CompletableFuture<CommitResult> commitAsync(int nChannel, Position position)
        {
        ensureActive();
        try
            {
            if (position instanceof PagedPosition)
                {
                return commitInternal(nChannel, (PagedPosition) position, null);
                }
            else
                {
                throw new IllegalArgumentException("Invalid position type");
                }
            }
        catch (Throwable t)
            {
            CompletableFuture<CommitResult> future = new CompletableFuture<>();
            future.completeExceptionally(t);
            return future;
            }
        }

    @Override
    @SuppressWarnings({"unchecked"})
    public CompletableFuture<Map<Integer, CommitResult>> commitAsync(Map<Integer, Position> mapPositions)
        {
        ensureActive();

        Map<Integer, CommitResult>  mapResult = new HashMap<>();
        Map<Integer, PagedPosition> mapCommit = new HashMap<>();

        for (Map.Entry<Integer, Position> entry : mapPositions.entrySet())
            {
            Integer  nChannel = entry.getKey();
            Position position = entry.getValue();
            if (position instanceof PagedPosition)
                {
                mapCommit.put(nChannel, (PagedPosition) position);
                }
            else
                {
                mapResult.put(nChannel, new CommitResult(nChannel, position, CommitResultStatus.Rejected));
                }
            }

        CompletableFuture<CommitResult>[] aFuture = mapCommit.entrySet()
                .stream()
                .map(e -> commitInternal(e.getKey(), e.getValue(), mapResult))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(aFuture).handle((_void, err) -> mapResult);
        }

    @Override
    public int[] getChannels()
        {
        return getChannelSet().stream()
                .mapToInt(i -> i)
                .toArray();
        }


    /**
     * Returns the current set of channels that this {@link Subscriber} owns.
     * <p>
     * Subscribers that are part of a subscriber group own a sub-set of the available channels.
     * A subscriber in a group should normally be assigned ownership of at least one channel. In the case where there
     * are more subscribers in a group that the number of channels configured for a topic, then some
     * subscribers will obviously own zero channels.
     * Anonymous subscribers that are not part of a group are always owners all of the available channels.
     *
     * @return the current set of channels that this {@link Subscriber} is the owner of, or an
     *         empty array if this subscriber has not been assigned ownership any channels
     */
    public Set<Integer> getChannelSet()
        {
        // Only have channels when connected
        if (m_nState == STATE_CONNECTED)
            {
            Gate<?> gate = f_gate;
            gate.enter(-1);
            try
                {
                return Arrays.stream(f_aChannel)
                        .filter(c -> c.m_fOwned)
                        .map(c -> c.subscriberPartitionSync.getChannelId())
                        .collect(Collectors.toSet());
                }
            finally
                {
                gate.exit();
                }
            }
        else
            {
            return Collections.emptySet();
            }
        }

    @Override
    public boolean isOwner(int nChannel)
        {
        if (m_nState == STATE_CONNECTED)
            {
            return nChannel >= 0 && f_aChannel[nChannel].m_fOwned;
            }
        return false;
        }

    @Override
    public int getChannelCount()
        {
        return m_caches.getChannelCount();
        }

    @Override
    public FlowControl getFlowControl()
        {
        return f_backlog;
        }

    @Override
    public void onClose(Runnable action)
        {
        f_listOnCloseActions.add(action);
        }

    @Override
    public boolean isActive()
        {
        return m_nState != STATE_CLOSED && m_nState != STATE_CLOSING;
        }

    @Override
    public Map<Integer, Position> getLastCommitted()
        {
        ensureActive();
        Map<Integer, Position> mapCommit  = m_caches.getLastCommitted(f_subscriberGroupId);
        int[]                  anChannels = m_aChannelOwned;
        Map<Integer, Position> mapResult  = new HashMap<>();
        for (int nChannel : anChannels)
            {
            mapResult.put(nChannel, mapCommit.getOrDefault(nChannel, PagedPosition.NULL_POSITION));
            }
        return mapResult;
        }

    @Override
    public Map<Integer, Position> getHeads()
        {
        ensureActive();

        Map<Integer, Position> mapHeads  = new HashMap<>();
        int[]                  anChannel = getChannels();

        for (int nChannel : anChannel)
            {
            mapHeads.put(nChannel, f_aChannel[nChannel].getHead());
            }

        for (CommittableElement element : m_queueValuesPrefetched)
            {
            int nChannel = element.getChannel();
            if (mapHeads.containsKey(nChannel))
                {
                Position      positionCurrent = mapHeads.get(nChannel);
                PagedPosition position        = (PagedPosition) element.getPosition();

                if (nChannel != CommittableElement.EMPTY && position != null
                        && position.getPage() != Page.EMPTY
                        && (positionCurrent == null || positionCurrent.compareTo(position) > 0))
                    {
                    mapHeads.put(nChannel, position);
                    }
                }
            }

        return mapHeads;
        }

    @Override
    public Map<Integer, Position> getTails()
        {
        Map<Integer, Position> map       = m_caches.getTails();
        Map<Integer, Position> mapTails  = new HashMap<>();
        int[]                  anChannel = getChannels();
        for (int nChannel : anChannel)
            {
            mapTails.put(nChannel, map.getOrDefault(nChannel, f_aChannel[nChannel].getHead()));
            }
        return mapTails;
        }

    @Override
    public Position seek(int nChannel, Position position)
        {
        ensureActive();

        if (!isOwner(nChannel))
            {
            throw new IllegalStateException("Subscriber is not allocated channels " + nChannel);
            }

        try
            {
            // pause receives while we seek
            f_queueReceiveOrders.pause();

            if (position == null || position instanceof PagedPosition)
                {
                return seekChannel(nChannel, (PagedPosition) position);
                }
            else
                {
                throw new IllegalArgumentException("Invalid position type");
                }
            }
        finally
            {
            // resume receives from the new position
            f_queueReceiveOrders.resetTrigger();
            }
        }

    @Override
    public Map<Integer, Position> seek(Map<Integer, Position> mapPosition)
        {
        ensureActive();

        List<Integer> listUnallocated = mapPosition.keySet().stream()
                .filter(c -> !isOwner(c))
                .collect(Collectors.toList());

        if (listUnallocated.size() > 0)
            {
            throw new IllegalStateException("Subscriber is not allocated channels " + listUnallocated);
            }

        try
            {
            // pause receives while we seek
            f_queueReceiveOrders.pause();

            Map<Integer, PagedPosition> mapSeek = new HashMap<>();
            for (Map.Entry<Integer, Position> entry : mapPosition.entrySet())
                {
                Integer  nChannel = entry.getKey();
                Position position = entry.getValue();
                if (position instanceof PagedPosition)
                    {
                    mapSeek.put(nChannel, (PagedPosition) position);
                    }
                else
                    {
                    throw new IllegalArgumentException("Invalid position type for channel " + nChannel);
                    }
                }

            Map<Integer, Position> mapResult    = new HashMap<>();
            for (Map.Entry<Integer, PagedPosition> entry : mapSeek.entrySet())
                {
                int                  nChannel     = entry.getKey();
                SeekProcessor.Result result       = seekInternal(nChannel, entry.getValue());
                Position             seekPosition = updateSeekedChannel(nChannel, result);
                mapResult.put(nChannel, seekPosition);
                }
            return mapResult;
            }
        finally
            {
            // resume receives from the new positions
            f_queueReceiveOrders.resetTrigger();
            }
        }

    @Override
    public Position seek(int nChannel, Instant timestamp)
        {
        ensureActive();

        if (!isOwner(nChannel))
            {
            throw new IllegalStateException("Subscriber is not allocated channel " + nChannel);
            }

        Objects.requireNonNull(timestamp);

        try
            {
            // pause receives while we seek
            f_queueReceiveOrders.pause();

            ValueExtractor<Object, Integer>  extractorChannel   = Page.ElementExtractor.chained(Element::getChannel);
            ValueExtractor<Object, Instant>  extractorTimestamp = Page.ElementExtractor.chained(Element::getTimestamp);
            ValueExtractor<Object, Position> extractorPosition  = Page.ElementExtractor.chained(Element::getPosition);

            PagedPosition position = m_caches.Data.aggregate(
                    Filters.equal(extractorChannel, nChannel).and(Filters.greater(extractorTimestamp, timestamp)),
                    new ComparableMin<>(extractorPosition));

            if (position == null)
                {
                // nothing found greater than the timestamp so either the topic is empty
                // or all elements are earlier, in either case seek to the tail
                return seekToTail(nChannel).get(nChannel);
                }

            PagedPosition positionSeek;
            int           nOffset = position.getOffset();

            if (nOffset == 0)
                {
                // The position found is the head of a page so we actually want to seek to the previous element
                // We don;t know the tail of that page so we can use Integer.MAX_VALUE
                positionSeek = new PagedPosition(position.getPage() - 1, Integer.MAX_VALUE);
                }
            else
                {
                // we are not at the head of a page so seek to the page and previous offset
                positionSeek = new PagedPosition(position.getPage(), nOffset - 1);
                }
            return seekChannel(nChannel, positionSeek);
            }
        finally
            {
            // resume receives from the new position
            f_queueReceiveOrders.resetTrigger();
            }
        }

    @Override
    public Map<Integer, Position> seekToHead(int... anChannel)
        {
        return seekToHeadOrTail(true, anChannel);
        }

    @Override
    public Map<Integer, Position> seekToTail(int... anChannel)
        {
        return seekToHeadOrTail(false, anChannel);
        }

    @Override
    public int getRemainingMessages()
        {
        int[] anChannel = getChannels();
        return m_caches.getRemainingMessages(f_subscriberGroupId, anChannel);
        }

    @Override
    public int getRemainingMessages(int nChannel)
        {
        if (isOwner(nChannel))
            {
            return m_caches.getRemainingMessages(f_subscriberGroupId, nChannel);
            }
        return 0;
        }

    // ----- Closeable methods ----------------------------------------------

    @Override
    public void close()
        {
        closeInternal(false);
        }

    // ----- Object methods -------------------------------------------------

    @Override
    public String toString()
        {
        if (m_nState == STATE_CLOSED)
            {
            return getClass().getSimpleName() + "(inactive)";
            }

        long cPollsNow  = m_cPolls;
        long cValuesNow = m_cValues;
        long cMissesNow = m_cMisses;
        long cCollNow   = m_cMissCollisions;
        long cWaitNow   = m_cWait;
        long cNotifyNow = m_cNotify;

        long cPoll   = cPollsNow  - m_cPollsLast;
        long cValues = cValuesNow - m_cValuesLast;
        long cMisses = cMissesNow - m_cMissesLast;
        long cColl   = cCollNow   - m_cMissCollisionsLast;
        long cWait   = cWaitNow   - m_cWaitsLast;
        long cNotify = cNotifyNow - m_cNotifyLast;

        m_cPollsLast          = cPollsNow;
        m_cValuesLast         = cValuesNow;
        m_cMissesLast         = cMissesNow;
        m_cMissCollisionsLast = cCollNow;
        m_cWaitsLast          = cWaitNow;
        m_cNotifyLast         = cNotifyNow;

        int    cChannelsPolled = f_setPolledChannels.cardinality();
        int    cChannelsHit    = f_setHitChannels.cardinality();
        String sChannelsHit    = f_setHitChannels.toString();
        f_setPolledChannels.clear();
        f_setHitChannels.clear();

        String sState;
        switch (m_nState)
            {
            case STATE_INITIAL:
                sState = "Initial";
                break;
            case STATE_CONNECTED:
                sState = "Connected";
                break;
            case STATE_DISCONNECTED:
                sState = "Disconnected";
                break;
            case STATE_CLOSED:
                sState = "Closed";
                break;
            default:
                sState = "Unknown(" + m_nState + ")";
            }

        return getClass().getSimpleName() + "(" + "topic=" + m_caches.getTopicName() +
            ", id=" + f_nId +
            ", group=" + f_subscriberGroupId +
            ", durable=" + !f_fAnonymous +
            ", state=" + sState +
            ", backlog=" + f_backlog +
            ", channelAllocation=" + (f_fAnonymous ? "[ALL]" : Arrays.toString(m_aChannelOwned)) +
            ", channelsPolled=" + sChannelsHit + cChannelsHit + "/" + cChannelsPolled  +
            ", batchSize=" + (cValues / (Math.max(1, cPoll - cMisses))) +
            ", hitRate=" + ((cPoll - cMisses) * 100 / Math.max(1, cPoll)) + "%" +
            ", colRate=" + (cColl * 100 / Math.max(1, cPoll)) + "%" +
            ", waitNotifyRate=" + (cWait * 100 / Math.max(1, cPoll)) + "/" + (cNotify * 100 / Math.max(1, cPoll)) + "%" +
            ')';
        }

    // ----- helper methods -------------------------------------------------

    /**
     * Returns the number of polls of the topic for messages.
     * <p>
     * This is typically larger than the number of messages received due to polling empty pages,
     * empty topics, etc.
     *
     * @return the number of polls of the topic for messages
     */
    public long getPolls()
        {
        return m_cPolls;
        }

    /**
     * Returns the number of messages received.
     *
     * @return the number of messages received
     */
    public long getValues()
        {
        return m_cValues;
        }

    /**
     * Returns the number of times the subscriber has waited on empty channels.
     *
     * @return the number of times the subscriber has waited on empty channels
     */
    public long getWait()
        {
        return m_cWait;
        }

    /**
     * Returns the number of times an empty channel has been polled.
     *
     * @return the number of times an empty channel has been polled
     */
    public long getMisses()
        {
        return m_cMisses;
        }

    /**
     * Returns the number of times an empty channel has been polled
     * due to a previous subscriber changing polling the channel and
     * hence this subscribers head being out of date.
     *
     * @return the number of times an empty channel has been polled
     *         due to a previous subscriber changing polling the
     *         channel and hence this subscribers head being out of
     *         date.
     */
    public long getMissCollisions()
        {
        return m_cMissCollisions;
        }

    /**
     * Returns the number of notification received that a channel has been populated.
     *
     * @return the number of notification received that a channel has been populated
     */
    public long getNotify()
        {
        return m_cNotify;
        }

    /**
     * Returns {@code true} if the specified {@link Position} has been committed in the specified
     * channel.
     * <p>
     * If the channel parameter is not a channel owned by this subscriber then even if the result
     * returned is {@code false}, the position could since have been committed by the owning subscriber.
     *
     * @param nChannel  the channel
     * @param position  the position within the channel to check
     *
     * @return {@code true} if the specified {@link Position} has been committed in the
     *         specified channel, or {@code false} if the position is not committed or
     *         this subscriber
     */
    public boolean isCommitted(int nChannel, Position position)
        {
        ensureActive();
        return m_caches.isCommitted(f_subscriberGroupId, nChannel, position);
        }

    /**
     * Initialise the subscriber.
     *
     * @throws InterruptedException if the wait for channel allocation is interrupted
     * @throws ExecutionException if the wait for channel allocation fails
     */
    protected synchronized void initialise() throws InterruptedException, ExecutionException, TimeoutException
        {
        ensureActive();
        if (m_nState == STATE_CONNECTED)
            {
            return;
            }

        // We must do initialisation under the gate lock
        try (Sentry<?> ignored = f_gate.close())
            {
            int     cChannel   = m_caches.getChannelCount();
            boolean fReconnect = m_nState == STATE_DISCONNECTED;

            if (fReconnect)
                {
                Logger.fine("Reconnecting subscriber " + this);
                }

            m_caches.ensureConnected();

            boolean fDisconnected = m_nState == STATE_DISCONNECTED;
            long[]  alHead        = m_caches.initializeSubscription(f_subscriberGroupId, f_nId, f_filter, f_fnConverter,
                                                                    fReconnect, false, fDisconnected);

            for (int nChannel = 0; nChannel < cChannel; ++nChannel)
                {
                Channel channel  = f_aChannel[nChannel];
                channel.m_lHead  = alHead[nChannel];
                channel.m_nNext  = -1; // unknown page position to start
                channel.m_fEmpty = false; // even if we could infer emptiness here it is unsafe unless we've registered for events
                }

            if (f_fAnonymous)
                {
                // anonymous so we own all channels
                List<Integer> listChannel = new ArrayList<>(cChannel);
                for (int i = 0; i < cChannel; i++)
                    {
                    listChannel.add(i);
                    }
                updateChannelOwnership(listChannel, false);
                }
            else
                {
                CompletableFuture<Subscription> future = m_caches.Subscriptions.async().get(f_aChannel[0].subscriberPartitionSync);
                Subscription subscription = null;
                try
                    {
                    // we use a timout here because a never ending get can cause a deadlock during fail-over scenarios
                    subscription = future.get(INIT_TIMEOUT_SECS, TimeUnit.SECONDS);
                    }
                catch (TimeoutException e)
                    {
                    future.cancel(true);
                    throw e;
                    }
                List<Integer> list = Arrays.stream(subscription.getChannels(f_nId, cChannel)).boxed().collect(Collectors.toList());
                updateChannelOwnership(list, false);
                }

            switchChannel();
            heartbeat();
            registerNotificationListener();

            setState(STATE_CONNECTED);
            }
        }

    /**
     * Trigger a receive loop.
     *
     * @param cBatch  the size of the batch of requests to process.
     */
    private void trigger(int cBatch)
        {
        receiveInternal(f_queueReceiveOrders, cBatch);
        }

    /**
     * Schedule another receive.
     *
     * @param queueRequest  the batching queue handling the requests
     * @param cBatch        the number of receives to schedule in this batch
     */
    private void receiveInternal(BatchingOperationsQueue<Request, ?> queueRequest, Integer cBatch)
        {
        if (isActive())
            {
            ensureConnected();
            }

        if (!queueRequest.isBatchComplete() || queueRequest.fillCurrentBatch(cBatch))
            {
            if (queueRequest.isBatchComplete())
                {
                return;
                }

            heartbeat();
            complete(queueRequest);

            int nChannel = ensureOwnedChannel();
            if (!queueRequest.isBatchComplete() && nChannel >= 0)
                {
                // we have emptied the pre-fetch queue but the batch has more in it, so fetch more
                Channel channel  = f_aChannel[nChannel];
                long    lHead    = channel.m_lHead;
                long    lVersion = channel.m_lVersion;

                int nPart = ((PartitionedService) m_caches.Subscriptions.getCacheService())
                                    .getKeyPartitioningStrategy()
                                    .getKeyPartition(new Page.Key(nChannel, lHead));

                InvocableMapHelper.invokeAsync(m_caches.Subscriptions,
                                               new Subscription.Key(nPart, nChannel, f_subscriberGroupId), m_caches.getUnitOfOrder(nPart),
                                               new PollProcessor(lHead, Integer.MAX_VALUE, f_nNotificationId, f_nId),
                                               (result, e) -> onReceiveResult(channel, lVersion, lHead, result, e))
                                  .handleAsync((r, e) ->
                                      {
                                      if (e != null)
                                          {
                                          Logger.err(e);
                                          return null;
                                          }
                                      if (!m_queueValuesPrefetched.isEmpty())
                                          {
                                          complete(queueRequest);
                                          }
                                      trigger(cBatch);
                                      return null;
                                      }, f_daemon::executeTask);
                }
            else
                {
                if (m_queueValuesPrefetched.isEmpty() && nChannel < 0)
                    {
                    // we have emptied the pre-fetch queue or the topic is empty
                    // we need to switch channel under the gate lock as we might have a concurrent notification
                    // that a channel is no longer empty (which also happens under the lock)
                    Gate<?> gate = f_gate;
                    // Wait to enter the gate
                    gate.enter(-1);
                    try
                        {
                        // now we are in the gate lock, re-try switching channel as we might have had a notification
                        if (switchChannel())
                            {
                            // we have a non-empty channel so go be round and process the receive requests again
                            receiveInternal(queueRequest, cBatch);
                            }
                        else
                            {
                            // the topic is empty
                            if (f_fCompleteOnEmpty)
                                {
                                // the complete-on-empty flag is set, so complete any outstanding requests
                                m_queueValuesPrefetched.add(getEmptyElement());
                                complete(queueRequest);
                                }
                            queueRequest.resetTrigger();
                            }
                        }
                    finally
                        {
                        // and finally exit from the gate
                        gate.exit();
                        }
                    }
                else
                    {
                    // go around again, more requests have come in
                    receiveInternal(queueRequest, cBatch);
                    }
                }
            }
        }

    /**
     * Complete as many outstanding requests as possible from the contents of the pre-fetch queue.
     *
     * @param queueRequest  the queue of requests to complete
     */
    @SuppressWarnings("unchecked")
    private void complete(BatchingOperationsQueue<Request, ?> queueRequest)
        {
        List<Request> queueBatch = queueRequest.getCurrentBatchValues();
        int           cValues    = 0;
        int           cRequest   = queueBatch.size();

        Queue<CommittableElement> queueValuesPrefetched = m_queueValuesPrefetched;

        if (isActive() && !queueValuesPrefetched.isEmpty())
            {
            LongArray          aValues = new SparseArray<>();
            CommittableElement element = queueValuesPrefetched.peek();

            if (element != null && element.isEmpty())
                {
                // we're empty, remove the empty/null element from the pre-fetch queue
                queueValuesPrefetched.poll();
                while (cValues < cRequest)
                    {
                    Request request = queueBatch.get(cValues);
                    if (!request.isBatch())
                        {
                        aValues.set(cValues, null);
                        }
                    else
                        {
                        aValues.set(cValues, Collections.emptyList());
                        }
                    cValues++;
                    }
                }
            else
                {
                while (m_nState == STATE_CONNECTED && cValues < cRequest && !queueValuesPrefetched.isEmpty())
                    {
                    Request request = queueBatch.get(cValues);
                    if (request.isBatch())
                        {
                        int cElement = request.getElementCount();
                        List<CommittableElement> list = new ArrayList<>();
                        for (int i = 0; i < cElement && !queueValuesPrefetched.isEmpty(); i++)
                            {
                            element = queueValuesPrefetched.poll();
                            // ensure we still own the channel
                            if (element != null && !element.isEmpty() && isOwner(element.getChannel()))
                                {
                                list.add(element);
                                }
                            }
                        aValues.set(cValues++, list);
                        }
                    else
                        {
                        element = queueValuesPrefetched.poll();
                        // ensure we still own the channel
                        if (element != null && !element.isEmpty() && isOwner(element.getChannel()))
                            {
                            aValues.set(cValues++, element);
                            }
                        }
                    }
                }
            queueRequest.completeElements(cValues, aValues, (err, value) -> new TopicException(err));
            }
        }

    /**
     * Asynchronously commit the specified channel and position.
     *
     * @param nChannel   the channel to commit
     * @param position   the position within the channel to commit
     * @param mapResult  the {@link Map} to add the commit result to
     *
     * @return a {@link CompletableFuture} that completes when the commit request has completed
     */
    private CompletableFuture<CommitResult> commitInternal(int nChannel, PagedPosition position, Map<Integer, CommitResult>  mapResult)
        {
        try
            {
            long lPage = position.getPage();
            int  cPart = m_caches.getPartitionCount();
            int  nPart = ((PartitionedService) m_caches.Subscriptions.getCacheService())
                                .getKeyPartitioningStrategy().getKeyPartition(new Page.Key(nChannel, lPage));

            scheduleHeadIncrement(f_aChannel[nChannel], lPage - 1).join();

            Set<Subscription.Key> setKeys = f_aChannel[nChannel].ensureSubscriptionKeys(cPart, f_subscriberGroupId);

            // We must execute against all Subscription keys for the channel and subscriber group
            CompletableFuture<Map<Subscription.Key, CommitResult>> future
                    = InvocableMapHelper.invokeAllAsync(m_caches.Subscriptions,
                                                        setKeys, m_caches.getUnitOfOrder(nPart),
                                                        new CommitProcessor(position, f_nId));

            return future.handle((map, err) ->
                            {
                            CommitResult result;
                            if (err == null)
                                {
                                // we are only interested in the result for the actual committed position
                                Subscription.Key key = new Subscription.Key(nPart, nChannel, f_subscriberGroupId);
                                result = map.get(key);
                                }
                            else
                                {
                                Logger.err("Commit failure", err);
                                result = new CommitResult(nChannel, position, CommitResultStatus.Rejected, err);
                                }

                            if (mapResult != null)
                                {
                                mapResult.put(nChannel, result);
                                }
                            return result;
                            });
            }
        catch (Throwable thrown)
            {
            CompletableFuture<CommitResult> future = new CompletableFuture<>();
            future.completeExceptionally(thrown);
            return future;
            }
        }

    private Position seekChannel(int nChannel, PagedPosition pagedPosition)
        {
        SeekProcessor.Result result       = seekInternal(nChannel, pagedPosition);
        Position             seekPosition = updateSeekedChannel(nChannel, result);
        return seekPosition == null ? PagedPosition.NULL_POSITION : seekPosition;
        }

    private Position updateSeekedChannel(int nChannel, SeekProcessor.Result result)
        {
        PagedPosition positionHead = result.getHead();
        PagedPosition seekPosition = result.getSeekPosition();

        if (positionHead != null)
            {
            f_aChannel[nChannel].m_lHead = positionHead.getPage();
            f_aChannel[nChannel].m_nNext = positionHead.getOffset();
            }

        m_queueValuesPrefetched.removeIf(e -> e.getChannel() == nChannel);
        return seekPosition;
        }

    /**
     * Move the head of the specified channel to a new position.
     *
     * @param nChannel   the channel to commit
     * @param position   the position within the channel to commit
     */
    private SeekProcessor.Result seekInternal(int nChannel, PagedPosition position)
        {
        // We must execute against all Subscription keys for the same channel and subscriber group
        Set<Subscription.Key> setKeys = f_aChannel[nChannel]
                .ensureSubscriptionKeys(m_caches.getPartitionCount(), f_subscriberGroupId);

        Map<Subscription.Key, SeekProcessor.Result> mapResult
                = m_caches.Subscriptions.invokeAll(setKeys, new SeekProcessor(position, f_nId));

        // the new head is the lowest non-null returned position
        return mapResult.values()
                .stream()
                .filter(Objects::nonNull)
                .sorted()
                .findFirst()
                .orElse(null);
        }

    /**
     * Seek to the head or tail for the specified channels.
     *
     * @param fHead      {@code true} to seek to the head, or {@code false} to seek to the tail
     * @param anChannel  the array of channels to seek
     *
     * @return a map of the new {@link Position} for each channel
     */
    protected Map<Integer, Position> seekToHeadOrTail(boolean fHead, int... anChannel)
        {
        if (anChannel == null || anChannel.length == 0)
            {
            return new HashMap<>();
            }

        List<Integer> listUnallocated = Arrays.stream(anChannel)
                .filter(c -> !isOwner(c))
                .boxed()
                .collect(Collectors.toList());

        if (listUnallocated.size() != 0)
            {
            throw new IllegalArgumentException("One or more channels are not allocated to this subscriber " + listUnallocated);
            }

        Map<Integer, Position> mapPosition;
        if (fHead)
            {
            ValueExtractor<Page, Integer> extractorChannel = new ReflectionExtractor<>("getChannelId", new Object[0], ReflectionExtractor.KEY);
            ValueExtractor<Page, Long>    extractorPage    = new ReflectionExtractor<>("getPageId", new Object[0], ReflectionExtractor.KEY);
            Map<Integer, Long>            mapHeads         = m_caches.Pages.aggregate(GroupAggregator.createInstance(extractorChannel, new LongMin<>(extractorPage)));

            mapPosition = new HashMap<>();
            for (int nChannel : anChannel)
                {
                mapPosition.put(nChannel, new PagedPosition(mapHeads.get(nChannel), -1));
                }
            }
        else
            {
            mapPosition = m_caches.getTails();
            }

        Map<Integer, Position> mapSeek  = new HashMap<>();
        for (int nChannel : anChannel)
            {
            Position position = mapPosition.get(nChannel);
            if (position != null)
                {
                mapSeek.put(nChannel, position);
                }
            }

        return seek(mapSeek);
        }

    /**
     * Ensure that the subscriber is active.
     *
     * @throws IllegalStateException if not active
     */
    private void ensureActive()
        {
        if (!isActive())
            {
            throw new IllegalStateException("The subscriber is not active");
            }
        }

    /**
     * Returns the state of the subscriber.
     *
     * @return the state of the subscriber
     */
    public int getState()
        {
        return m_nState;
        }

    /**
     * Set the state of the subscriber.
     *
     * @param nState  the state of the subscriber
     */
    protected void setState(int nState)
        {
        m_nState = nState;
        }

    /**
     * Ensure that the subscriber is connected.
     */
    protected void ensureConnected()
        {
        if (m_nState != STATE_CONNECTED)
            {
            synchronized (this)
                {
                ensureActive();
                PagedTopic.Dependencies dependencies = m_caches.getDependencies();
                long                    retry        = dependencies.getReconnectRetryMillis();
                long                    now          = System.currentTimeMillis();
                long                    timeout      = now + dependencies.getReconnectTimeoutMillis();
                Throwable               error        = null;
                if (m_nState != STATE_CONNECTED)
                    {
                    while (now < timeout)
                        {
                        try
                            {
                            m_caches.ensureConnected();
                            initialise();
                            error = null;
                            break;
                            }
                        catch (Throwable thrown)
                            {
                            error = thrown;
                            if (error instanceof TopicException)
                                {
                                break;
                                }
                            }
                        now = System.currentTimeMillis();
                        if (now < timeout)
                            {
                            Logger.info("Failed to reconnect subscriber, will retry in "
                                    + retry + " millis " + this + " due to " + error.getMessage());
                            try
                                {
                                Thread.sleep(retry);
                                }
                            catch (InterruptedException e)
                                {
                                // ignored
                                }
                            }
                        }
                    }

                if (error != null)
                    {
                    throw Exceptions.ensureRuntimeException(error);
                    }
                }
            }
        }

    /**
     * Returns {@code true} if this subscriber is disconnected from the topic.
     *
     * @return {@code true} if this subscriber is disconnected from the topic
     */
    public boolean isDisconnected()
        {
        return m_nState == STATE_DISCONNECTED;
        }

    /**
     * Returns {@code true} if this subscriber is initialising.
     *
     * @return {@code true} if this subscriber is initialising
     */
    public boolean isInitialising()
        {
        return m_nState == STATE_INITIAL;
        }

    /**
     * Disconnect this subscriber.
     * <p>
     * This will cause the subscriber to re-initialize itself on re-connection.
     */
    public void disconnect()
        {
        if (isActive() && m_nState != STATE_DISCONNECTED)
            {
            setState(STATE_DISCONNECTED);
            if (!f_fAnonymous)
                {
                // reset the channel allocation for non-anonymous subscribers, channels
                // will be reallocated when (or if) reconnection occurs
                m_listenerChannelAllocation.reset();
                }
            // clear out the pre-fetch queue because we have no idea what we'll get on reconnection
            m_queueValuesPrefetched.clear();
            Logger.fine("Disconnected Subscriber " + this);
            }
        }

    /**
     * Returns this subscriber's group identifier.
     *
     * @return this subscriber's group identifier
     */
    public SubscriberGroupId getSubscriberGroupId()
        {
        return f_subscriberGroupId;
        }

    /**
     * Notification that one or more channels that were empty now have content.
     *
     * @param anChannel  the non-empty channels
     */
    private void onChannelPopulatedNotification(int[] anChannel)
        {
        if (anChannel == null || anChannel.length == 0)
            {
            // we have no channel allocation, so we're still effectively empty
            return;
            }

        boolean fWasEmpty;

        // Channel operations are done under a lock
        try (Sentry<?> ignored = f_gate.close())
            {
            if (f_aChannel == null || !isActive())
                {
                // not initialised yet or no longer active
                return;
                }

            ++m_cNotify;

            int nChannelCurrent  = m_nChannel;
            fWasEmpty = nChannelCurrent < 0 || f_aChannel[nChannelCurrent].m_fEmpty;
            for (int nChannel : anChannel)
                {
                f_aChannel[nChannel].setPopulated();
                }
            }

        if (fWasEmpty)
            {
            // we were on the empty channel so switch and trigger a request loop
            switchChannel();
            f_queueReceiveOrders.triggerOperations();
            }
        // else; we weren't waiting so things are already scheduled
        }

    /**
     * Set the specified channel as empty.
     *
     * @param nChannel  the channel to mark as empty
     * @param lVersion  the version to use as the CAS to mark the channel
     */
    private void onChannelEmpty(int nChannel, long lVersion)
        {
        // Channel operations are done under a lock
        Gate<?> gate = f_gate;
        // Wait to enter the gate
        gate.enter(-1);
        try
            {
            if (f_aChannel == null || !isActive())
                {
                // not initialised yet or no longer active
                return;
                }
            f_aChannel[nChannel].setEmpty(lVersion);
            }
        finally
            {
            // and finally exit from the gate
            gate.exit();
            }
        }

    /**
     * Compare-and-increment the remote head pointer.
     *
     * @param lHeadAssumed  the assumed old value, increment will only occur if the actual head matches this value
     *
     * @return a {@link CompletableFuture} that completes with the new head
     */
    protected CompletableFuture<Long> scheduleHeadIncrement(Channel channel, long lHeadAssumed)
        {
        if (isActive())
            {
            // update the globally visible head page
            return InvocableMapHelper.invokeAsync(m_caches.Subscriptions, channel.subscriberPartitionSync,
                                                  m_caches.getUnitOfOrder(channel.subscriberPartitionSync.getPartitionId()),
                                                  new HeadAdvancer(lHeadAssumed + 1),
                                                  (lPriorHeadRemote, e2) ->
                {
                if (lPriorHeadRemote < lHeadAssumed + 1)
                    {
                    // our CAS succeeded, we'd already updated our local head before attempting it
                    // but we do get to clear any contention since the former winner's CAS will fail
                    channel.m_fContended = false;
                    // we'll allow the channel to be removed from the contended channel list naturally during
                    // the next nextChannel call
                    }
                else
                    {
                    // our CAS failed; i.e. the remote head was already at or beyond where we tried to set it.
                    // comparing against the prior value allows us to know if we won or lost the CAS which
                    // we can use to coordinate contention such that only the losers backoff

                    if (lHeadAssumed != Page.NULL_PAGE)
                        {
                        // we thought we knew what page we were on, but we were wrong, thus someone
                        // else had incremented it, this is a collision.  Backoff and allow them
                        // temporary exclusive access, they'll do the same for the channels we
                        // increment
                        if (!channel.m_fContended)
                            {
                            channel.m_fContended = true;
                            f_listChannelsContended.add(channel);
                            }

                        m_cHitsSinceLastCollision = 0;
                        }
                    // else; we knew we were contended, don't doubly backoff

                    if (lPriorHeadRemote > channel.m_lHead)
                        {
                        // only update if we haven't locally moved ahead; yes it is possible that we lost the
                        // CAS but have already advanced our head simply through brute force polling
                        channel.m_lHead = lPriorHeadRemote;
                        channel.m_nNext = -1; // unknown page position
                        }
                    }
                });
            }
        return CompletableFuture.completedFuture(-1L);
        }

    /**
     * If this is not an anonymous subscriber send a heartbeat to the server.
     */
    public void heartbeat()
        {
        if (!f_fAnonymous)
            {
            // we're not anonymous so send a poll heartbeat
            m_caches.Subscribers.async().invoke(f_key, new SubscriberHeartbeatProcessor());
            }
        }

    private void updateChannelOwnership(List<Integer> listChannels, boolean fLost)
        {
        Collections.sort(listChannels);
        int[] aChannel = listChannels.stream().mapToInt(i -> i).toArray();

        // channel ownership change must be done under a lock
        try (Sentry<?> ignored = f_gate.close())
            {
            if (!Arrays.equals(m_aChannelOwned, aChannel))
                {
                Set<Integer> setNew     = new HashSet<>(listChannels);
                Set<Integer> setRevoked = new HashSet<>();
                if (m_aChannelOwned != null && m_aChannelOwned.length > 0)
                    {
                    for (int nChannel : m_aChannelOwned)
                        {
                        setNew.remove(nChannel);
                        setRevoked.add(nChannel);
                        }
                    listChannels.forEach(setRevoked::remove);
                    }
                setRevoked = Collections.unmodifiableSet(setRevoked);

                Set<Integer> setAdded = new HashSet<>(listChannels);
                setAdded = Collections.unmodifiableSet(setAdded);

                Logger.fine(String.format("Subscriber %d channel allocation changed, assigned=%s revoked=%s",
                        f_nId, setAdded, setRevoked));

                m_aChannelOwned = aChannel;

                if (!f_fAnonymous)
                    {
                    // reset revoked channel heads - we'll re-sync if they are reallocated
                    setRevoked.forEach(c ->
                        {
                        Channel channel      = f_aChannel[c];
                        channel.m_fContended = false;
                        channel.m_fOwned     = false;
                        channel.setPopulated();
                        });

                    // if we're initializing and not anonymous, we do not own any channels,
                    // we'll update with the allocated ownership
                    if (m_nState == STATE_INITIAL)
                        {
                        for (Channel channel : f_aChannel)
                            {
                            channel.m_fOwned = false;
                            }
                        }

                    // re-sync added channel heads and reset empty flag
                    setNew.forEach(c ->
                        {
                        Channel channel      = f_aChannel[c];
                        channel.m_fContended = false;
                        channel.m_fOwned     = true;
                        channel.setPopulated();
                        //scheduleHeadIncrement(channel, Page.NULL_PAGE);
                        });
                    }

                if (m_aChannelOwnershipListener.length > 0)
                    {
                    for (ChannelOwnershipListener listener : m_aChannelOwnershipListener)
                        {
                        if (!setRevoked.isEmpty())
                            {
                            try
                                {
                                if (fLost)
                                    {
                                    listener.onChannelsLost(setRevoked);
                                    }
                                else
                                    {
                                    listener.onChannelsRevoked(setRevoked);
                                    }
                                }
                            catch (Throwable t)
                                {
                                Logger.err(t);
                                }
                            }
                        if (!setAdded.isEmpty())
                            {
                            try
                                {
                                listener.onChannelsAssigned(setAdded);
                                }
                            catch (Throwable t)
                                {
                                Logger.err(t);
                                }
                            }
                        }

                    }

                onChannelPopulatedNotification(m_aChannelOwned);
                }
            }
        }

    /**
     * Return the current channel to poll, ensuring this subscriber owns the channel.
     *
     * @return the current channel to poll
     */
    protected int ensureOwnedChannel()
        {
        if (m_nChannel >= 0 && f_aChannel[m_nChannel].m_fOwned)
            {
            return m_nChannel;
            }
        switchChannel();
        return m_nChannel;
        }

    /**
     * Switch to the next available channel.
     *
     * @return {@code true} if a potentially non-empty channel has been found
     *         or {@code false} iff all channels are known to be empty
     */
    protected boolean switchChannel()
        {
        // channel access must be done under a lock to ensure channel
        // state does not change while switching
        Gate<?> gate = f_gate;
        // Wait to enter the gate
        gate.enter(-1);
        try
            {
            if (m_aChannelOwned.length == 0)
                {
                m_nChannel = -1;
                return false;
                }

            if (m_aChannelOwned.length == 1)
                {
                int nChannel = m_aChannelOwned[0];
                // only one allocated channel
                if (f_aChannel[nChannel].m_fEmpty)
                    {
                    // our single channel is empty
                    m_nChannel = -1;
                    return false;
                    }
                else
                    {
                    // our single channel is not empty, switch to it
                    m_nChannel = nChannel;
                    return true;
                    }
                }

            int nChannelStart = m_nChannel;
            int nChannel      = nChannelStart;
            int i        = 0;
            for (; i < m_aChannelOwned.length; i++)
                {
                if (m_aChannelOwned[i] > nChannel)
                    {
                    break;
                    }
                if (m_aChannelOwned[i] == nChannel)
                    {
                    i++;
                    break;
                    }
                }

            if (i >= m_aChannelOwned.length)
                {
                i = 0;
                }

            // i is now the next channel index
            nChannel = m_aChannelOwned[i];

            // now ensure the channel is not empty
            int cTried = 0;
            while (nChannel != nChannelStart && cTried < f_aChannel.length && (!f_aChannel[nChannel].m_fOwned || f_aChannel[nChannel].m_fEmpty))
                {
                cTried++;
                nChannel++;
                if (nChannel == f_aChannel.length)
                    {
                    nChannel = 0;
                    }
                }

            if (f_aChannel[nChannel].m_fOwned && !f_aChannel[nChannel].m_fEmpty)
                {
                m_nChannel = nChannel;
                return true;
                }
            m_nChannel = -1;
            return false;
            }
        finally
            {
            // and finally exit from the gate
            gate.exit();
            }
        }

    /**
     * Handle the result of an async receive.
     *
     * @param channel  the associated channel
     * @param lPageId  lTail the page the receive targeted
     * @param result   the result
     * @param e        and exception
     */
    protected void onReceiveResult(Channel channel, long lVersion, long lPageId, PollProcessor.Result result, Throwable e)
        {
        int nChannel = channel.subscriberPartitionSync.getChannelId();

        // check that there is no error and we still own the channel
        if (e == null )
            {
            Queue<Binary> queueValues = result.getElements();
            int           cReceived   = queueValues.size();
            int           cRemaining  = result.getRemainingElementCount();
            int           nNext       = result.getNextIndex();

            f_setPolledChannels.set(nChannel);
            ++m_cPolls;

            if (cReceived == 0)
                {
                ++m_cMisses;

                if (channel.m_nNext != nNext && channel.m_nNext != -1) // collision
                    {
                    ++m_cMissCollisions;
                    m_cHitsSinceLastCollision = 0;
                    // don't backoff here, as it is possible all subscribers could end up backing off and
                    // the channel would be temporarily abandoned.  We only backoff as part of trying to increment the
                    // page as that is a CAS and for someone to fail, someone else must have succeeded.
                    }
                // else; spurious notify
                }
            else if (!queueValues.isEmpty())
                {
                f_setHitChannels.set(nChannel);
                ++m_cHitsSinceLastCollision;
                m_cValues += cReceived;

                // add the received elements to the pre-fetch queue
                queueValues.stream()
                        .map(bin -> new CommittableElement(bin, nChannel))
                        .forEach(m_queueValuesPrefetched::add);
                }

            channel.m_nNext = nNext;

            if (cRemaining == PollProcessor.Result.EXHAUSTED)
                {
                // we know the page is exhausted, so the new head is at least one higher
                if (lPageId >= channel.m_lHead && lPageId != Page.NULL_PAGE)
                    {
                    channel.m_lHead = lPageId + 1;
                    channel.m_nNext = 0;
                    }

                // we're actually on the EMPTY_PAGE so we'll concurrently increment the durable
                // head pointer and then update our pointer accordingly
                if (lPageId == Page.NULL_PAGE)
                    {
                    scheduleHeadIncrement(channel, lPageId);
                    }

                // switch to a new channel since we've exhausted this page
                switchChannel();
                }
            else if (cRemaining == 0 || cRemaining == PollProcessor.Result.NOT_ALLOCATED_CHANNEL)
                {
                // we received nothing or polled a channel we do not own
                if (cRemaining == 0)
                    {
                    // we received nothing, mark the channel as empty
                    onChannelEmpty(nChannel, lVersion);
                    }

                // attempt to switch to a non-empty channel
                if (!switchChannel())
                    {
                    // we've run out of channels to poll from
                    if (f_fCompleteOnEmpty)
                        {
                        // add an empty element, which signals to the completion method that we're done
                        m_queueValuesPrefetched.add(getEmptyElement());
                        }
                    else
                        {
                        // wait for non-empty;
                        // Note: automatically registered for notification as part of returning an empty result set
                        ++m_cWait;
                        }
                    }
                }
            else if (cRemaining == PollProcessor.Result.UNKNOWN_SUBSCRIBER)
                {
                // The subscriber was unknown, possibly due to a persistence snapshot recovery or the topic being
                // destroyed whilst the poll was in progress.
                // Disconnect and let reconnection sort us out
                disconnect();
                }
            }
        else // remove failed; this is fairly catastrophic
            {
            // TODO: figure out error handling
            // fail all currently (and even concurrently) scheduled removes
            f_queueReceiveOrders.handleError((err, bin) -> e, BatchingOperationsQueue.OnErrorAction.CompleteWithException);
            }
        }

    /**
     * Destroy subscriber group.
     *
     * @param pagedTopicCaches   the associated caches
     * @param subscriberGroupId  the group to destroy
     */
    static void destroy(PagedTopicCaches pagedTopicCaches, SubscriberGroupId subscriberGroupId)
        {
        if (pagedTopicCaches.isActive())
            {
            int                   cParts      = ((PartitionedService) pagedTopicCaches.Subscriptions.getCacheService()).getPartitionCount();
            Set<Subscription.Key> setSubParts = new HashSet<>(cParts);
            for (int i = 0; i < cParts; ++i)
                {
                // channel 0 will propagate the operation to all other channels
                setSubParts.add(new Subscription.Key(i, /*nChannel*/ 0, subscriberGroupId));
                }

            // see note in TopicSubscriber constructor regarding the need for locking
            boolean fNamed = subscriberGroupId.getMemberTimestamp() == 0;
            if (fNamed)
                {
                pagedTopicCaches.Subscriptions.lock(subscriberGroupId, -1);
                }

            try
                {
                InvocableMapHelper.invokeAllAsync(pagedTopicCaches.Subscriptions, setSubParts,
                                                  (key) -> pagedTopicCaches.getUnitOfOrder(key.getPartitionId()),
                                                  DestroySubscriptionProcessor.INSTANCE)
                        .join();
                }
            finally
                {
                if (fNamed)
                    {
                    pagedTopicCaches.Subscriptions.unlock(subscriberGroupId);
                    }
                }
            }
        }

    /**
     * Close and clean-up this subscriber.
     *
     * @param fDestroyed  {@code true} if this call is in response to the caches
     *                    being destroyed/released and hence just clean up local
     *                    state
     */
    private void closeInternal(boolean fDestroyed)
        {
        synchronized (this)
            {
            if (m_nState != STATE_CLOSED)
                {
                setState(STATE_CLOSING); // accept no new requests, and cause all pending ops to complete ASAP (see onReceiveResult)

                try
                    {
                    f_queueReceiveOrders.close();
                    f_queueReceiveOrders.cancelAllAndClose("Subscriber has been closed", null);

                    // flush this subscriber to wait for all of the outstanding
                    // operations to complete (or to be cancelled if we're destroying)
                    try
                        {
                        flushInternal(fDestroyed ? FlushMode.FLUSH_DESTROY : FlushMode.FLUSH).get(CLOSE_TIMEOUT_SECS, TimeUnit.SECONDS);
                        }
                    catch (TimeoutException e)
                        {
                        // too long to wait for completion; force all outstanding futures to complete exceptionally
                        flushInternal(FlushMode.FLUSH_CLOSE_EXCEPTIONALLY).join();
                        Logger.warn("Subscriber.close: timeout after waiting " + CLOSE_TIMEOUT_SECS + " seconds for completion with flush.join(), forcing complete exceptionally");
                        }
                    catch (ExecutionException | InterruptedException e)
                        {
                        // ignore
                        }

                    if (!fDestroyed)
                        {
                        // caches have not been destroyed so we're just closing this subscriber
                        unregisterDeactivationListener();
                        unregisterChannelAllocationListener();
                        unregisterNotificationListener();
                        notifyClosed(m_caches.Subscriptions, f_subscriberGroupId, f_nId);
                        removeSubscriberEntry();
                        }

                    if (!fDestroyed && f_subscriberGroupId.getMemberTimestamp() != 0)
                        {
                        // this subscriber is anonymous and thus non-durable and must be destroyed upon close
                        // Note: if close isn't the cluster will eventually destroy this subscriber once it
                        // identifies the associated member has left the cluster.
                        // If an application creates a lot of subscribers and does not close them when finished
                        // then this will cause heap consumption to rise.
                        // There used to be a To-Do comment here about cleaning up in a finalizer, but as
                        // finalizers in the JVM are not reliable that is probably not such a good idea.
                        destroy(m_caches, f_subscriberGroupId);
                        }
                    }
                finally
                    {
                    setState(STATE_CLOSED);

                    f_listOnCloseActions.forEach(action ->
                    {
                    try
                        {
                        action.run();
                        }
                    catch (Throwable t)
                        {
                        Logger.fine(this.getClass().getName() + ".close(): handled onClose exception: " +
                            t.getClass().getCanonicalName() + ": " + t.getMessage());
                        }
                    });
                    f_daemon.stop(true);
                    }
                }
            }
        }

    /**
     * Obtain a {@link CompletableFuture} that will be complete when
     * all of the currently outstanding add operations complete.
     * <p>
     * If this method is called in response to a topic destroy then the
     * outstanding operations will be completed with an exception as the underlying
     * topic caches have been destroyed so they can never complete normally.
     * <p>
     * if this method is called in response to a timeout waiting for flush to complete normally,
     * indicated by {@link FlushMode#FLUSH_CLOSE_EXCEPTIONALLY}, complete exceptionally all outstanding
     * asynchronous operations so close finishes.
     *
     * The returned {@link CompletableFuture} will always complete
     * normally, even if the outstanding operations complete exceptionally.
     *
     * @param mode  {@link FlushMode} flush mode to use
     *
     * @return a {@link CompletableFuture} that will be completed when
     *         all of the currently outstanding add operations are complete
     */
    private CompletableFuture<Void> flushInternal(FlushMode mode)
        {
        String sTopicName   = m_caches.getTopicName();
        String sDescription = null;
        switch (mode)
            {
            case FLUSH_DESTROY:
                sDescription = "Topic " + sTopicName + " was destroyed";

            case FLUSH_CLOSE_EXCEPTIONALLY:
                String sReason = sDescription != null
                        ? sDescription
                        : "Force Close of Subscriber " + f_nId + " for topic " + sTopicName;

                BiFunction<Throwable, Request, Throwable> fn  = (err, bin) -> new TopicException(sReason, err);
                Arrays.stream(f_aChannel)
                    .forEach(channel -> f_queueReceiveOrders.handleError(fn,
                        BatchingOperationsQueue.OnErrorAction.CompleteWithException));

                return CompletableFuture.completedFuture(null);

            case FLUSH:
            default:
                return f_queueReceiveOrders.flush();
            }
        }

    /**
     * Called to notify the topic that a subscriber has closed or timed-out.
     *
     * @param cache              the subscription cache
     * @param subscriberGroupId  the subscriber group identifier
     * @param nId                the subscriber identifier
     */
    static void notifyClosed(NamedCache<Subscription.Key, Subscription> cache, SubscriberGroupId subscriberGroupId, long nId)
        {
        if (!cache.isActive())
            {
            // cache is already inactive so we cannot do anything
            return;
            }

        try
            {
            DistributedCacheService service      = (DistributedCacheService) cache.getCacheService();
            int                     cParts       = service.getPartitionCount();
            List<Subscription.Key>  listSubParts = new ArrayList<>(cParts);
            for (int i = 0; i < cParts; ++i)
                {
                // Note: we unsubscribe against channel 0 in each partition, and it will in turn update all channels
                listSubParts.add(new Subscription.Key(i, /*nChannel*/ 0, subscriberGroupId));
                }

Logger.err("**** In notifyClosed() - Calling invokeAll CloseSubscriptionProcessor on cache " + cache.getCacheName());
            cache.async().invokeAll(listSubParts, new CloseSubscriptionProcessor(nId))
                    .handle((result, error) ->
                            {
                            if (error != null)
                                {
                                Logger.err("Caught exception closing subscription for subscriber "
                                    + idToString(nId) + " in group " + subscriberGroupId.getGroupName(), error);
                                }
                            return null;
                            });
Logger.err("**** In notifyClosed() - Called invokeAll CloseSubscriptionProcessor on cache " + cache.getCacheName());
            }
        catch (Throwable t)
            {
            Logger.err(t);
            }
        }
    /**
     * Called to remove the entry for this subscriber from the subscriber info cache.
     *
     */
    protected void removeSubscriberEntry()
        {
        NamedCache<SubscriberInfo.Key, SubscriberInfo> cache = m_caches.Subscribers;
        if (!cache.isActive())
            {
            // cache is already inactive so we cannot do anything
            return;
            }

        try
            {
            cache.invoke(f_key, EvictSubscriber.INSTANCE);
            }
        catch (Throwable t)
            {
            Logger.err(t);
            }
        }

    /**
     * Instantiate and register a MapListener with the topic subscriptions cache that
     * will listen for changes in channel allocations.
     */
    protected void registerChannelAllocationListener()
        {
        try
            {
            ChannelListener listenerChannel = m_listenerChannelAllocation;

            if (listenerChannel != null)
                {
                m_caches.Subscriptions.addMapListener(listenerChannel, f_aChannel[0].subscriberPartitionSync, false);
                }
            }
        catch (RuntimeException e)
            {
            Logger.err(e);
            }
        }

    /**
     * Unregister the channel allocation listener.
     */
    protected void unregisterChannelAllocationListener()
        {
        try
            {
            ChannelListener listener = m_listenerChannelAllocation;

            if (listener != null)
                {
                m_caches.Subscriptions.removeMapListener(listener, f_aChannel[0].subscriberPartitionSync);
                }
            }
        catch (RuntimeException e)
            {
            Logger.err(e);
            }
        }

    @SuppressWarnings("unchecked")
    protected void registerNotificationListener()
        {
        // register a subscriber listener in each partition, we must be completely setup before doing this
        // as the callbacks assume we're fully initialized
        if (m_caches.Notifications.isActive())
            {
            m_caches.Notifications.addMapListener(f_listenerNotification, f_filterNotification, /*fLite*/ false);
            }
        }

    @SuppressWarnings("unchecked")
    protected void unregisterNotificationListener()
        {
        // un-register the subscriber listener in each partition
        if (m_caches.Notifications.isActive())
            {
            m_caches.Notifications.removeMapListener(f_listenerNotification, f_filterNotification);
            }
        }

    /**
     * Instantiate and register a DeactivationListener with the topic subscriptions cache.
     */
    @SuppressWarnings("unchecked")
    protected void registerDeactivationListener()
        {
        try
            {
            if (!f_fAnonymous)
                {
                GroupDeactivationListener listenerGroup = m_listenerGroupDeactivation;

                // only need to register this listener for non-anonymous subscribers
                if (listenerGroup != null)
                    {
                    m_caches.Subscriptions.addMapListener(listenerGroup, f_aChannel[0].subscriberPartitionSync, true);
                    }
                }

            m_caches.addListener(f_listenerDeactivation);
            }
        catch (RuntimeException e)
            {
            // intentionally empty
            }
        }

    /**
     * Unregister cache deactivation listener.
     */
    @SuppressWarnings("unchecked")
    protected void unregisterDeactivationListener()
        {
        try
            {
            GroupDeactivationListener listenerGroup = m_listenerGroupDeactivation;

            if (listenerGroup != null)
                {
                m_caches.Subscriptions.removeMapListener(listenerGroup, f_aChannel[0].subscriberPartitionSync);
                }

            m_caches.removeListener(f_listenerDeactivation);
            }
        catch (RuntimeException e)
            {
            // intentionally empty
            }
        }

    /**
     * Returns an empty {@link CommittableElement}.
     *
     * @return an empty {@link CommittableElement}
     */
    CommittableElement getEmptyElement()
        {
        if (m_elementEmpty == null)
            {
            Binary binValue   = ExternalizableHelper.toBinary(null, f_serializer);
            Binary binElement = PageElement.toBinary(-1, 0L, 0, 0L, binValue);
            m_elementEmpty = new CommittableElement(binElement, CommittableElement.EMPTY);
            }
        return m_elementEmpty;
        }

    /**
     * Create a subscriber identifier.
     *
     * @param nNotificationId  the notification identifier
     * @param nMemberId        the cluster member id
     *
     * @return a subscriber identifier
     */
    public static long createId(long nNotificationId, long nMemberId)
        {
        return (nMemberId << 32) | (nNotificationId & 0xFFFFFFFFL);
        }

    /**
     * Parse a cluster member id from a subscriber identifier.
     *
     * @param nId  the subscriber identifier
     *
     * @return the cluster member id from the subscriber id
     */
    public static int memberIdFromId(long nId)
        {
        return (int) (nId >> 32);
        }

    /**
     * Return a string representation of a subscriber identifier.
     *
     * @param nId  the subscriber identifier
     *
     * @return a string representation of the subscriber identifier
     */
    public static String idToString(long nId)
        {
        return nId + "/" + memberIdFromId(nId);
        }

    /**
     * Return a string representation of a collection of subscriber identifiers.
     *
     * @param setId  the collection of subscriber identifiers
     *
     * @return a string representation of the collection of subscriber identifiers
     */
    public static String idToString(Collection<Long> setId)
        {
        return setId.stream()
                .map(PagedTopicSubscriber::idToString)
                .collect(Collectors.joining(","));
        }


    /**
     * Parse a subscriber notification identifier from a subscriber identifier.
     *
     * @param nId  the subscriber identifier
     *
     * @return te notification identifier parsed from the subscriber identifier
     */
    static int notificationIdFromId(long nId)
        {
        return (int) (nId & 0xFFFFFFFFL);
        }

    // ----- inner class: CommittableElement --------------------------------

    /**
     * CommittableElement is a wrapper around a {@link PageElement}
     * that makes it committable.
     */
    private class CommittableElement
        implements Element<V>
        {
        // ----- constructors -----------------------------------------------

        /**
         * Create an element
         *
         * @param binValue  the binary element value
         */
        CommittableElement(Binary binValue, int nChannel)
            {
            m_element  = PageElement.fromBinary(binValue, f_serializer);
            f_nChannel = nChannel;
            }

        // ----- Element methods --------------------------------------------

        @Override
        public V getValue()
            {
            return m_element.getValue();
            }

        @Override
        public Binary getBinaryValue()
            {
            return m_element.getBinaryValue();
            }

        @Override
        public int getChannel()
            {
            return f_nChannel;
            }

        @Override
        public Position getPosition()
            {
            return m_element.getPosition();
            }

        @Override
        public Instant getTimestamp()
            {
            return m_element.getTimestamp();
            }

        @Override
        public CompletableFuture<CommitResult> commitAsync()
            {
            try
                {
                return PagedTopicSubscriber.this.commitAsync(getChannel(), getPosition());
                }
            catch (Throwable e)
                {
                CommitResult result = new CommitResult(0, null, e);
                return CompletableFuture.completedFuture(result);
                }
            }

        public boolean isEmpty()
            {
            return getChannel() == EMPTY;
            }

        // ----- Object methods ---------------------------------------------

        @Override
        public String toString()
            {
            return "Element(" +
                    "channel=" + f_nChannel +
                    ", position=" + getPosition() +
                    ", timestamp=" + getTimestamp() +
                    ", value=" + getValue() +
                    ')';
            }

        // ----- constructors -----------------------------------------------

        /**
         * The value used for a page id in an empty element.
         */
        public static final int EMPTY = -1;

        // ----- data members -----------------------------------------------

        /**
         * The wrapped element.
         */
        private final PageElement<V> m_element;

        /**
         * The channel for this element.
         */
        private final int f_nChannel;
        }

    // ----- inner class: Channel -------------------------------------------

    /**
     * Channel is a data structure which represents the state of a channel as known
     * by this subscriber.
     */
    protected static class Channel
        {
        /**
         * Returns the current head position for the channel.
         *
         * @return the current head position for the channel
         */
        protected PagedPosition getHead()
            {
            if (m_lHead == Page.EMPTY)
                {
                return PagedPosition.NULL_POSITION;
                }
            return new PagedPosition(m_lHead, m_nNext);
            }

        /**
         * Set this channel as empty only if the channel version matches the specified version.
         *
         * @param lVersion  the channel version to use as a CAS
         *
         * @return {@code true} if the version matched and the channel was marked as empty
         */
        protected synchronized boolean setEmpty(long lVersion)
            {
            if (m_lVersion == lVersion)
                {
                m_fEmpty = true;
                return true;
                }
            return false;
            }

        /**
         * Set this channel as populated an bump the version up by one.
         */
        protected synchronized void setPopulated()
            {
            m_lVersion++;
            m_fEmpty = false;
            }

        /**
         * Return the {@link Subscription.Key} to use to execute cluster wide subscription operations
         * for this channel.
         *
         * @param nPart              the number of partitions
         * @param subscriberGroupId  the subscriber group identifier
         *
         * @return the {@link Subscription.Key} to use to execute cluster wide subscription operations
         *         for this channel
         */
        protected Set<Subscription.Key> ensureSubscriptionKeys(int nPart, SubscriberGroupId subscriberGroupId)
            {
            if (m_setSubscriptionKeys == null)
                {
                int                   nChannel = subscriberPartitionSync.getChannelId();
                Set<Subscription.Key> setKeys  = new HashSet<>();
                for (int p = 0; p < nPart; p++)
                    {
                    setKeys.add(new Subscription.Key(p, nChannel, subscriberGroupId));
                    }
                m_setSubscriptionKeys = setKeys;
                }
            return m_setSubscriptionKeys;
            }

        // ----- Object methods ---------------------------------------------

        public String toString()
            {
            return "Channel=" + subscriberPartitionSync.getChannelId() +
                    ", owned=" + m_fOwned +
                    ", empty=" + m_fEmpty +
                    ", head=" + m_lHead +
                    ", next=" + m_nNext +
                    ", contended=" + m_fContended;
            }

        // ----- data members -----------------------------------------------

        /**
         * The current head page for this subscriber, this value may safely be behind (but not ahead) of the actual head.
         *
         * volatile as it is possible it gets concurrently updated by multiple threads if the futures get completed
         * on IO threads.  We don't both going with a full blow AtomicLong as either value is suitable and worst case
         * we update to an older value and this would be harmless and just get corrected on the next attempt.
         */
        volatile long m_lHead;

        /**
         * The current version of this channel.
         * <p>
         * This is used for CAS operations on the empty flag.
         */
        volatile long m_lVersion;

        /**
         * The index of the next item in the page, or -1 for unknown
         */
        int m_nNext = -1;

        /**
         * True if the channel has been found to be empty.  Once identified as empty we don't need to poll form it again
         * until we receive an event indicating that it has seen a new insertion.
         */
        boolean m_fEmpty;

        /**
         * The key which holds the channels head for this group.
         */
        Subscription.Key subscriberPartitionSync;

        /**
         * True if contention has been detected on this channel.
         */
        boolean m_fContended;

        /**
         * True if this subscriber owns this channel.
         */
        boolean m_fOwned = true;

        Set<Subscription.Key> m_setSubscriptionKeys;
        }

    // ----- inner class: FlushMode ----------------------------------------

    enum FlushMode
        {
        /**
         *  Wait for all outstanding asynchronous operations to complete.
         */
        FLUSH,

        /**
         * Cancel all outstanding asynchronous operations due to topic being destroyed.
         */
        FLUSH_DESTROY,

        /**
         * Complete exceptionally all outstanding asynchronous operations due to timeout during initial {@link #FLUSH} during close.
         */
        FLUSH_CLOSE_EXCEPTIONALLY
        }

    // ----- inner class: Request -------------------------------------------

    /**
     * A receive request.
     */
    protected static class Request
        {
        /**
         * Create a receive request.
         *
         * @param fBatch    {@code true} if this is a batch receive
         * @param cElement  the number of elements to receive
         */
        protected Request(boolean fBatch, int cElement)
            {
            f_fBatch   = fBatch;
            f_cElement = cElement;
            }

        // ----- accessors --------------------------------------------------

        /**
         * Returns {@code true} if this is a batch request.
         *
         * @return {@code true} if this is a batch request
         */
        public boolean isBatch()
            {
            return f_fBatch;
            }

        /**
         * Returns the number of elements to receive.
         *
         * @return the number of elements to receive
         */
        public int getElementCount()
            {
            return f_cElement;
            }

        // ----- constructors -----------------------------------------------

        /**
         * A singleton, non-batch, receive request.
         */
        public static final Request SINGLE = new Request(false, 1);

        // ----- data members -----------------------------------------------

        /**
         * A flag indicating whether this is a batch request.
         */
        private final boolean f_fBatch;

        /**
         * The number of elements to receive if this is a batch request.
         */
        private final int f_cElement;
        }

    // ----- inner class: DeactivationListener ------------------------------

    /**
     * A {@link PagedTopicCaches.Listener} to detect the subscribed topic deactivation.
     */
    protected class DeactivationListener
        implements PagedTopicCaches.Listener
        {
        @Override
        public void onConnect()
            {
            }

        @Override
        public void onDisconnect()
            {
            disconnect();
            }

        @Override
        public void onDestroy()
            {
            Logger.fine("Detected release of topic "
                                + m_caches.getTopicName() + ", closing subscriber "
                                + PagedTopicSubscriber.this);
            closeInternal(false);
            }

        @Override
        public void onRelease()
            {
            Logger.fine("Detected destroy of topic "
                                + m_caches.getTopicName() + ", closing subscriber "
                                + PagedTopicSubscriber.this);
            closeInternal(true);
            }
        }

    // ----- inner class: GroupDeactivationListener -------------------------

    /**
     * A {@link AbstractMapListener} to detect the removal of the subscriber group
     * that the subscriber is subscribed to.
     */
    protected class GroupDeactivationListener
        extends AbstractMapListener
        {
        @Override
        @SuppressWarnings("rawtypes")
        public void entryDeleted(MapEvent evt)
            {
            // destroy subscriber group
            Logger.fine("Detected removal of subscriber group "
                + f_subscriberGroupId.getGroupName() + ", closing subscriber "
                + PagedTopicSubscriber.this);
            closeInternal(true);
            }
        }

    // ----- inner class: ChannelListener -----------------------------------

    /**
     * A {@link MapListener} that tracks changes to the channels owned by this subscriber.
     */
    protected class ChannelListener
            implements MapListener<Subscription.Key, Subscription>
        {
        public ChannelListener()
            {
            m_latch = new CountDownLatch(1);
            }

        // ----- MapListener methods ----------------------------------------

        @Override
        public void entryInserted(MapEvent<Subscription.Key, Subscription> evt)
            {
            onChannelAllocation(evt);
            }

        @Override
        public void entryUpdated(MapEvent<Subscription.Key, Subscription> evt)
            {
            onChannelAllocation(evt);
            }

        @Override
        public void entryDeleted(MapEvent<Subscription.Key, Subscription> evt)
            {
            onChannelAllocation(evt);
            }

        // ----- Object methods ---------------------------------------------

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object o)
            {
            if (this == o)
                {
                return true;
                }
            if (o == null || getClass() != o.getClass())
                {
                return false;
                }
            ChannelListener that = (ChannelListener) o;
            return getId() == that.getId();
            }

        @Override
        public int hashCode()
            {
            return Objects.hash(getId());
            }

        // ----- helper methods ---------------------------------------------

        /**
         * Reset this listener.
         */
        public void reset()
            {
            if (m_latch.getCount() == 0)
                {
                // effectively revokes all channels
                updateChannelOwnership(Collections.emptyList(), true);
                m_latch = new CountDownLatch(1);
                }
            }

        private void onChannelAllocation(MapEvent<Subscription.Key, Subscription> evt)
            {
            if (evt.isDelete())
                {
                updateChannelOwnership(Collections.emptyList(), true);
                }
            else
                {
                Subscription subscription = evt.getNewValue();
                if (subscription.hasSubscriber(f_nId))
                    {
                    List<Integer> list = Arrays.stream(subscription.getChannels(f_nId, m_caches.getChannelCount()))
                            .boxed()
                            .collect(Collectors.toList());

                    updateChannelOwnership(list, false);
                    m_latch.countDown();
                    }
                else if (isActive() && !f_fAnonymous && !isDisconnected() && !isInitialising())
                    {
                    Logger.fine("Disconnecting Subscriber due to subscriber timeout "
                                        + PagedTopicSubscriber.this);

                    updateChannelOwnership(Collections.emptyList(), true);
                    disconnect();
                    }
                }
            }

        private long getId()
            {
            return PagedTopicSubscriber.this.f_nId;
            }

        // ----- data members -----------------------------------------------

        /**
         * A latch that is triggered when channel ownership is initialized.
         */
        private CountDownLatch m_latch;
        }

    // ----- inner class: TopicServiceListener ------------------------------

    protected class TopicServiceListener
            implements MemberListener, ServiceListener
        {
        @Override
        public void serviceStarting(ServiceEvent evt)
            {
            }

        @Override
        public void serviceStarted(ServiceEvent evt)
            {
            }

        @Override
        public void serviceStopping(ServiceEvent evt)
            {
            }

        @Override
        public void serviceStopped(ServiceEvent evt)
            {
            if (!isDisconnected())
                {
                Logger.fine("Disconnecting Subscriber due to Service stopped event "
                        + PagedTopicSubscriber.this);
                disconnect();
                }
            }

        @Override
        public void memberJoined(MemberEvent evt)
            {
            }

        @Override
        public void memberLeaving(MemberEvent evt)
            {
            }

        @Override
        public void memberLeft(MemberEvent evt)
            {
            DistributedCacheService cacheService = (DistributedCacheService) m_caches.getCacheService();
            if (cacheService.getOwnershipEnabledMembers().isEmpty() && !isDisconnected())
                {
                Logger.fine("Disconnecting Subscriber due to departure of all storage members "
                        + PagedTopicSubscriber.this);
                disconnect();
                }
            }
        }

    // ----- inner class: TimeoutInterceptor --------------------------------

    /**
     * A server side interceptor used to detect removal of {@link SubscriberInfo} entries
     * from the subscriber {@link PagedTopicCaches#Subscribers} when a subscriber is closed
     * or is evicted due to timeout.
     */
    public static class TimeoutInterceptor
            implements EventDispatcherAwareInterceptor<EntryEvent<SubscriberInfo.Key, SubscriberInfo>>
        {
        public TimeoutInterceptor()
            {
            f_executor = Executors.newSingleThreadScheduledExecutor(runnable ->
                {
                String sName = "SubscriberTimeoutInterceptor:" + f_instance.incrementAndGet();
                return Base.makeThread(null, runnable, sName);
                });
            }

        @Override
        public void introduceEventDispatcher(String sIdentifier, EventDispatcher dispatcher)
            {
            if (dispatcher instanceof PartitionedCacheDispatcher)
                {
                String sCacheName = ((PartitionedCacheDispatcher) dispatcher).getCacheName();
                if (PagedTopicCaches.Names.SUBSCRIBERS.equals(PagedTopicCaches.Names.fromCacheName(sCacheName)))
                    {
                    dispatcher.addEventInterceptor(sIdentifier, this, Collections.singleton(EntryEvent.Type.REMOVED), true);
                    }
                }
            }

        @Override
        public void onEvent(EntryEvent<SubscriberInfo.Key, SubscriberInfo> event)
            {
            if (event.getType() == EntryEvent.Type.REMOVED)
                {
                SubscriberInfo.Key key            = event.getKey();
                long               nId            = key.getSubscriberId();
                SubscriberGroupId  groupId        = key.getGroupId();

                Logger.fine(String.format(
                        "Cleaning up subscriber %d in group '%s' owned by member %d",
                        key.getSubscriberId(), groupId.getGroupName(), memberIdFromId(nId)));

                // we MUST process the event on another thread so as not to block the event dispatcher thread.
                f_executor.execute(() -> processSubscriberRemoval(event));
                }
            }

        @SuppressWarnings({"unchecked"})
        private void processSubscriberRemoval(EntryEvent<SubscriberInfo.Key, SubscriberInfo> event)
            {
            SubscriberInfo.Key key            = event.getKey();
            SubscriberInfo     info           = event.getOriginalValue();
            long               nId            = key.getSubscriberId();
            SubscriberGroupId  groupId        = key.getGroupId();
            String             sTopicName     = PagedTopicCaches.Names.getTopicName(event.getCacheName());
            String             sSubscriptions = PagedTopicCaches.Names.SUBSCRIPTIONS.cacheNameForTopicName(sTopicName);

            if (event.getEntry().isSynthetic())
                {
                Logger.fine(String.format(
                        "Subscriber expired or closed - id=%d, groupId='%s', memberId=%d, notificationId=%d, last heartbeat at %s",
                        nId, groupId.getGroupName(), memberIdFromId(nId), notificationIdFromId(nId), info.getLastHeartbeat()));
                }
            else
                {
                Logger.fine(String.format(
                        "Subscriber %d in group '%s' removed due to departure of member %d",
                        nId, groupId.getGroupName(), memberIdFromId(nId)));
                }

            notifyClosed(event.getService().ensureCache(sSubscriptions, null), groupId, nId);
            }

        // ----- constants --------------------------------------------------

        private static final AtomicInteger f_instance = new AtomicInteger();

        // ----- data members -----------------------------------------------

        private final Executor f_executor;
        }

    // ----- constants ------------------------------------------------------

    /**
     * Value of the initial subscriber state.
     */
    public static final int STATE_INITIAL = 0;

    /**
     * Value of the subscriber state when connected.
     */
    public static final int STATE_CONNECTED = 1;

    /**
     * Value of the subscriber state when disconnected.
     */
    public static final int STATE_DISCONNECTED = 2;

    /**
     * Value of the subscriber state when closing.
     */
    public static final int STATE_CLOSING = 3;

    /**
     * Value of the subscriber state when closed.
     */
    public static final int STATE_CLOSED = 4;

    /**
     * Subscriber close timeout on first flush attempt. After this time is exceeded, all outstanding asynchronous operations will be completed exceptionally.
     */
    public static final long CLOSE_TIMEOUT_SECS = TimeUnit.MILLISECONDS.toSeconds(Base.parseTime(Config.getProperty("coherence.topic.subscriber.close.timeout", "30s"), Base.UNIT_S));

    /**
     * Subscriber initialise timeout.
     */
    public static final long INIT_TIMEOUT_SECS = TimeUnit.MILLISECONDS.toSeconds(Base.parseTime(Config.getProperty("coherence.topic.subscriber.init.timeout", "30s"), Base.UNIT_S));

    // ----- data members ---------------------------------------------------

    /**
     * The underlying {@link NamedTopic} being subscribed to.
     */
    private final NamedTopic<?> f_topic;

    /**
     * The {@link PagedTopicCaches} instance managing the caches for the topic
     * being consumed.
     */
    protected PagedTopicCaches m_caches;

    /**
     * Flag indicating whether this subscriber is part of a group or is anonymous.
     */
    protected final boolean f_fAnonymous;

    /**
     * This subscribers cluster wide unique identifier.
     */
    protected final long f_nId;

    /**
     * The {@link SubscriberInfo.Key} to use to send heartbeats.
     */
    protected SubscriberInfo.Key f_key;

    /**
     * The optional {@link Filter} to use to filter messages.
     */
    protected final Filter<V>   f_filter;

    /**
     * The optional function to use to transform the payload of the message on the server.
     */
    protected final Function<V, ?> f_fnConverter;

    /**
     * The cache's serializer.
     */
    protected final Serializer f_serializer;

    /**
     * The identifier for this {@link PagedTopicSubscriber}.
     */
    protected SubscriberGroupId f_subscriberGroupId;

    /**
     * This subscriber's notification id.
     */
    protected final int f_nNotificationId;

    /**
     * The filter used to register the notification listener.
     */
    protected final Filter<Object> f_filterNotification;

    /**
     * True if configured to complete when empty
     */
    protected final boolean f_fCompleteOnEmpty;

    /**
     * The {@link Gate} controlling access to the channel operations.
     */
    private final Gate<?> f_gate;

    /**
     * The state of the subscriber.
     */
    private volatile int m_nState = STATE_INITIAL;

    /**
     * Optional queue of prefetched values which can be used to fulfil future receive requests.
     */
    protected Queue<CommittableElement> m_queueValuesPrefetched = new ConcurrentLinkedDeque<>();

    /**
     * Queue of pending receive awaiting values.
     */
    protected final BatchingOperationsQueue<Request, ?> f_queueReceiveOrders;

    /**
     * Subscriber flow control object.
     */
    protected final DebouncedFlowControl f_backlog;

    /**
     * The state for the channels.
     */
    protected final Channel[] f_aChannel;

    /**
     * The owned channels.
     */
    protected volatile int[] m_aChannelOwned;

    /**
     * The current channel.
     */
    protected volatile int m_nChannel;

    /**
     * The daemon used to complete subscriber futures so that they are not on the service thread.
     */
    protected final TaskDaemon f_daemon;

    /**
     * The listener that receives notifications for non-empty channels.
     */
    @SuppressWarnings("rawtypes")
    private final SimpleMapListener f_listenerNotification;

    /**
     * The listener that will update channel allocations.
     */
    protected ChannelListener m_listenerChannelAllocation;

    /**
     * The array of {@link ChannelOwnershipListener listeners} to be notified when channel allocations change.
     */
    protected final ChannelOwnershipListener[] m_aChannelOwnershipListener;

    /**
     * The number of poll requests.
     */
    protected long m_cPolls;

    /**
     * The last value of m_cPolls used within {@link #toString} stats.
     */
    protected long m_cPollsLast;

    /**
     * The number of values received.
     */
    protected long m_cValues;

    /**
     * The last value of m_cValues used within {@link #toString} stats.
     */
    protected long m_cValuesLast;

    /**
     * The number of times this subscriber has waited.
     */
    protected long m_cWait;

    /**
     * The last value of m_cWait used within {@link #toString} stats.
     */
    protected long m_cWaitsLast;

    /**
     * The number of misses;
     */
    protected long m_cMisses;

    /**
     * The last value of m_cMisses used within {@link #toString} stats.
     */
    protected long m_cMissesLast;

    /**
     * The number of times a miss was attributable to a collision
     */
    protected long m_cMissCollisions;

    /**
     * The last value of m_cMissCollisions used within {@link #toString} stats.
     */
    protected long m_cMissCollisionsLast;

    /**
     * The number of times this subscriber has been notified.
     */
    protected long m_cNotify;

    /**
     * The last value of m_cNotify used within {@link #toString} stats.
     */
    protected long m_cNotifyLast;

    /**
     * The number of hits since our last miss.
     */
    protected int m_cHitsSinceLastCollision;

    /**
     * List of contended channels, ordered such that those checked longest ago are at the front of the list
     */
    @SuppressWarnings("unchecked")
    protected final List<Channel> f_listChannelsContended = new CircularArrayList();

    /**
     * BitSet of polled channels since last toString call.
     */
    protected final BitSet f_setPolledChannels;

    /**
     * BitSet of channels which hit since last toString call.
     */
    protected final BitSet f_setHitChannels;

    /**
     * The deactivation listener.
     */
    protected final DeactivationListener f_listenerDeactivation = new DeactivationListener();

    /**
     * The NamedCache deactivation listener.
     */
    protected GroupDeactivationListener m_listenerGroupDeactivation;

    /**
     * A {@link List} of actions to run when this publisher closes.
     */
    private final List<Runnable> f_listOnCloseActions = new ArrayList<>();

    /**
     * An empty committable element.
     */
    private CommittableElement m_elementEmpty;
    }
