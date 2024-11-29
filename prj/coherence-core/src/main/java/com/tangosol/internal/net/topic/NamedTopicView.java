/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */

package com.tangosol.internal.net.topic;

import com.tangosol.internal.net.topic.impl.paged.model.SubscriberGroupId;
import com.tangosol.net.Service;
import com.tangosol.net.TopicService;

import com.tangosol.net.events.internal.TopicDispatcher;

import com.tangosol.net.topic.NamedTopic;
import com.tangosol.net.topic.NamedTopicEvent;
import com.tangosol.net.topic.NamedTopicListener;
import com.tangosol.net.topic.Publisher;
import com.tangosol.net.topic.Subscriber;

import com.tangosol.util.Filter;
import com.tangosol.util.Listeners;
import com.tangosol.util.ValueExtractor;

import java.util.Objects;
import java.util.Set;

import java.util.stream.Collectors;

/**
 * An implementation of a {@link NamedTopic} that uses a {@link Connector}
 * to connect to remote topic resources.
 *
 * @param <V>  the type of the topic values
 *
 * @author Jonathan Knight  2024.11.26
 */
@SuppressWarnings("unchecked")
public class NamedTopicView<V>
        implements NamedTopic<V>, PublisherConnector.Factory<V>, SubscriberConnector.Factory<V>
    {
    /**
     * Create a {@link NamedTopicView}.
     *
     * @param connector  the {@link Connector} to use
     *
     * @throws NullPointerException if the {@link Connector} is {@code null}
     */
    public NamedTopicView(Connector<V> connector)
        {
        connector.setConnectedNamedTopic(this);
        f_connector  = Objects.requireNonNull(connector);

        f_sName      = connector.getName();
        f_listeners  = new Listeners();
        f_dispatcher = new TopicDispatcher(f_sName, connector.getTopicService());
        }

    /**
     * Obtain the {@link Connector} being used.
     *
     * @return the {@link Connector} being used
     */
    public Connector<V> getConnector()
        {
        return f_connector;
        }

    /**
     * Dispatch an event to registered listeners.
     *
     * @param type  the type of the event
     */
    public void dispatchEvent(NamedTopicEvent.Type type)
        {
        NamedTopicEvent event = new NamedTopicEvent(this, type);
        event.dispatch(f_listeners);
        }

    // ----- NamedTopic methods ---------------------------------------------

    @Override
    public boolean isActive()
        {
        return f_connector.isActive();
        }

    @Override
    public boolean isDestroyed()
        {
        return f_connector.isDestroyed();
        }

    @Override
    public boolean isReleased()
        {
        return f_connector.isReleased();
        }

    @Override
    public TopicService getTopicService()
        {
        return f_connector.getTopicService();
        }

    @Override
    public void close()
        {
        f_connector.close();
        }

    @Override
    public Publisher<V> createPublisher(Publisher.Option<? super V>... options)
        {
        PublisherConnector<V> connector = f_connector.createPublisher(options);
        return new NamedTopicPublisher<>(this, connector, options);
        }

    @Override
    public <U> Subscriber<U> createSubscriber(Subscriber.Option<? super V, U>... options)
        {
        return f_connector.createSubscriber(options);
        }

    @Override
    public void ensureSubscriberGroup(String sGroup, Filter<?> filter, ValueExtractor<?, ?> extractor)
        {
        f_connector.ensureSubscriberGroup(sGroup, filter, extractor);
        }

    @Override
    public void destroySubscriberGroup(String sGroup)
        {
        f_connector.destroySubscriberGroup(sGroup);
        }

    @Override
    public Set<String> getSubscriberGroups()
        {
        return getTopicService().getSubscriberGroups(f_sName)
                .stream()
                .filter(id -> !id.isAnonymous())
                .map(SubscriberGroupId::getGroupName)
                .collect(Collectors.toSet());
        }

    @Override
    public int getChannelCount()
        {
        return getTopicService().getChannelCount(f_sName);
        }

    @Override
    public int getRemainingMessages(String sSubscriberGroup, int... anChannel)
        {
        return f_connector.getRemainingMessages(sSubscriberGroup, anChannel);
        }

    @Override
    public String getName()
        {
        return f_sName;
        }

    @Override
    public Service getService()
        {
        return f_connector.getTopicService();
        }

    @Override
    public void destroy()
        {
        f_connector.destroy();
        }

    @Override
    public void release()
        {
        f_connector.release();
        }

    @Override
    public void addListener(NamedTopicListener listener)
        {
        f_listeners.add(listener);
        }

    @Override
    public void removeListener(NamedTopicListener listener)
        {
        f_listeners.remove(listener);
        }

    @Override
    public PublisherConnector<V> createPublisherConnector(Publisher.Option<? super V>[] options)
        {
        return f_connector.createPublisherConnector(options);
        }

    @Override
    public <U> SubscriberConnector<U> createSubscriberConnector(Subscriber.Option<? super V, U>[] options)
        {
        return f_connector.createSubscriberConnector(options);
        }

    // ----- object methods -------------------------------------------------

    @Override
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
        NamedTopicView<?> that = (NamedTopicView<?>) o;
        return Objects.equals(f_connector, that.f_connector);
        }

    @Override
    public int hashCode()
        {
        return Objects.hash(f_connector);
        }

    @Override
    public String toString()
        {
        return getClass().getSimpleName()
                + "(name=" + f_sName
                + ", connector=" + f_connector
                + ")";
        }

    // ----- inner interface: Connector -------------------------------------

    /**
     * A connector used by a {@link NamedTopicView} to connect
     * to remote topic resources.
     *
     * @param <V>  the type of the topic values
     */
    public interface Connector<V>
            extends PublisherConnector.Factory<V>, SubscriberConnector.Factory<V>
        {
        /**
         * Determine whether the underlying topic is active.
         *
         * @return {@code true} if the underlying topic is active
         */
        boolean isActive();

        /**
         * Determine whether the underlying topic is destroyed.
         *
         * @return {@code true} if the underlying topic is destroyed
         */
        boolean isDestroyed();

        /**
         * Determine whether the underlying topic is released.
         *
         * @return {@code true} if the underlying topic is released
         */
        boolean isReleased();

        /**
         * Obtain the count of remaining messages for a subscriber group.
         *
         * @param sSubscriberGroup  the name of the subscriber group
         * @param anChannel         the array of channels to obtain counts for
         *
         * @return the total remaining unread messages
         */
        int getRemainingMessages(String sSubscriberGroup, int[] anChannel);

        /**
         * Obtain the {@link TopicService} that manages this topic.
         *
         * @return the {@link TopicService} that manages this topic
         */
        TopicService getTopicService();

        /**
         * Close the underlying topic.
         */
        void close();

        /**
         * Obtain the name of the underlying topic.
         *
         * @return the name of the underlying topic
         */
        String getName();

        /**
         * Destroy the underlying topic.
         */
        void destroy();

        /**
         * Release the underlying topic.
         */
        void release();

        /**
         * Ensure that the specified subscriber group exists for this topic.
         *
         * @param sSubscriberGroup  the name of the subscriber group
         * @param filter            the {@link Filter} used to filter messages to be received by subscribers in the group
         * @param extractor         the {@link ValueExtractor} used to convert messages to be received by subscribers in the group
         *
         * @throws IllegalStateException if the subscriber group already exists with a different filter
         *                               or converter extractor
         */
        void ensureSubscriberGroup(String sSubscriberGroup, Filter<?> filter, ValueExtractor<?, ?> extractor);

        /**
         * Destroy the {@link Subscriber.Name named} subscriber group for the associated topic.
         * <p>
         * Releases storage and stops accumulating topic values for destroyed subscriber group.
         * This operation will impact all {@link Subscriber members} of the subscriber group.
         *
         * @param sSubscriberGroup  the name of the subscriber group
         */
        void destroySubscriberGroup(String sSubscriberGroup);

        /**
         * Create a {@link PublisherConnector} that can publish values into this {@link NamedTopic}.
         *
         * @param options  the {@link Publisher.Option}s controlling the {@link PublisherConnector}
         *
         * @return a {@link PublisherConnector} that can publish values into this {@link NamedTopic}
         */
        PublisherConnector<V> createPublisher(Publisher.Option<? super V>[] options);

        /**
         * Create a {@link SubscriberConnector} to subscribe to this {@link NamedTopic}.
         *
         * @param options  the {@link Subscriber.Option}s controlling the {@link SubscriberConnector}
         *
         * @return a {@link SubscriberConnector} to subscribe to this {@link NamedTopic}
         */
        <U> NamedTopicSubscriber<U> createSubscriber(Subscriber.Option<? super V, U>[] options);

        /**
         * Set the instance of the {@link NamedTopicView}.
         *
         * @param namedTopicView  the {@link NamedTopicView}
         */
        void setConnectedNamedTopic(NamedTopicView<V> namedTopicView);
        }

    // ----- data members ---------------------------------------------------

    /**
     * The {@link Connector} to use to connect to remote topic resources.
     */
    private final Connector<V> f_connector;

    /**
     * The name of the topic.
     */
    private final String f_sName;

    /**
     * The listeners registered with this topic.
     */
    private final Listeners f_listeners;

    /**
     * The {@link TopicDispatcher} to dispatch lifecycle events.
     */
    private final TopicDispatcher f_dispatcher;
    }
