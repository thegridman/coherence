/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */
package topics;

import com.oracle.bedrock.OptionsByType;

import com.oracle.bedrock.junit.SessionBuilders;

import com.oracle.bedrock.runtime.LocalPlatform;

import com.oracle.bedrock.runtime.coherence.CoherenceCluster;
import com.oracle.bedrock.runtime.coherence.CoherenceClusterBuilder;
import com.oracle.bedrock.runtime.coherence.CoherenceClusterMember;
import com.oracle.bedrock.runtime.coherence.callables.IsServiceRunning;
import com.oracle.bedrock.runtime.coherence.options.CacheConfig;
import com.oracle.bedrock.runtime.coherence.options.ClusterName;
import com.oracle.bedrock.runtime.coherence.options.LocalStorage;
import com.oracle.bedrock.runtime.coherence.options.Logging;
import com.oracle.bedrock.runtime.coherence.options.OperationalOverride;
import com.oracle.bedrock.runtime.coherence.options.RoleName;

import com.oracle.bedrock.runtime.java.options.SystemProperty;

import com.oracle.bedrock.runtime.options.DisplayName;
import com.oracle.bedrock.runtime.options.StabilityPredicate;

import com.oracle.bedrock.testsupport.MavenProjectFileUtils;
import com.oracle.bedrock.testsupport.deferred.Eventually;

import com.oracle.bedrock.testsupport.junit.TestLogs;

import com.oracle.coherence.common.base.Exceptions;
import com.oracle.coherence.common.base.Logger;

import com.oracle.coherence.common.base.Timeout;
import com.tangosol.io.ExternalizableLite;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.Cluster;
import com.tangosol.net.ConfigurableCacheFactory;
import com.tangosol.net.DistributedCacheService;

import com.tangosol.net.GuardSupport;
import com.tangosol.net.topic.NamedTopic;
import com.tangosol.net.topic.Publisher;
import com.tangosol.net.topic.Subscriber;

import com.tangosol.util.ExternalizableHelper;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import util.PartitionedCacheServiceIsBalanced;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tangosol.net.topic.Subscriber.Name.inGroup;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;

/**
 * This test verifies that topic publishers and subscribers recover from
 * a total loss of storage. In this case storage has active persistence
 * enabled and is removed via a clean shutdown (as would happen in k8s
 * using the operator).
 */
public class TopicsStorageRecoveryTests
    {
    @BeforeClass
    public static void setup() throws Exception
        {
        System.setProperty(LocalStorage.PROPERTY, "false");
        System.setProperty(Logging.PROPERTY, "9");
        System.setProperty(OperationalOverride.PROPERTY, "common-tangosol-coherence-override.xml");
        System.setProperty(CacheConfig.PROPERTY, "simple-persistence-bdb-cache-config.xml");
        }

    @Before
    public void setupTest()
        {
        f_watcher.println(">>>>> Starting @Before for test: " + f_testName.getMethodName());
        // make sure persistence files are not left from a previous test
        File filePersistence = new File("target/store-bdb-active/" + TopicsStorageRecoveryTests.class.getSimpleName());
        if (filePersistence.exists())
            {
            MavenProjectFileUtils.recursiveDelete(filePersistence);
            }

        s_storageCluster = startCluster("initial");
        PartitionedCacheServiceIsBalanced isBalanced = new PartitionedCacheServiceIsBalanced("DistributedTopicPersistence");
        for (CoherenceClusterMember member : s_storageCluster)
            {
            Eventually.assertDeferred(() -> member.invoke(isBalanced), is(true));
            }
        OptionsByType options = OptionsByType.of(s_options);
        options.add(RoleName.of("client"));
        options.add(LocalStorage.disabled());

        s_ccf = SessionBuilders.storageDisabledMember().build(LocalPlatform.get(), s_storageCluster, options);

        Cluster cluster = CacheFactory.ensureCluster();
        Eventually.assertDeferred(cluster::isRunning, is(true));
        Eventually.assertDeferred(() -> cluster.getMemberSet().size(), is(3));

        s_count.incrementAndGet();
        f_watcher.println(">>>>> Completed @Before for test: " + f_testName.getMethodName());
        }

    @After
    public void cleanupTest()
        {
        f_watcher.println(">>>>> Starting @After for test: " + f_testName.getMethodName());
        if (m_topic != null)
            {
            try
                {
                m_topic.close();
                }
            catch (Exception e)
                {
                // ignored
                }
            }

        s_storageCluster.close();
        s_storageCluster = null;
        s_ccf.dispose();
        s_ccf = null;
        CacheFactory.shutdown();
        f_watcher.println(">>>>> Completed @After for test: " + f_testName.getMethodName());
        }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRecoverAfterCleanStorageRestart() throws Exception
        {
f_watcher.println(">>>>> Entered shouldRecoverAfterCleanStorageRestart()");
        NamedTopic<Message>     topic        = ensureTopic("test");
        String                  sGroup       = "group-one";
        DistributedCacheService service      = (DistributedCacheService) topic.getService();
        Cluster                 cluster      = service.getCluster();
        String                  sServiceName = service.getInfo().getServiceName();

        // the test should not be storage enabled
        assertThat(service.isLocalStorageEnabled(), is(false));

        // create a subscriber group so that published messages are not lost before the subscriber subscribes
        topic.ensureSubscriberGroup(sGroup);

        try (Publisher<Message> publisher = topic.createPublisher())
            {
            AtomicBoolean fPublish    = new AtomicBoolean(true);
            AtomicBoolean fSubscribe  = new AtomicBoolean(true);
            AtomicBoolean fPublishing = new AtomicBoolean(false);
            AtomicBoolean fSubscribed = new AtomicBoolean(false);
            AtomicInteger cPublished  = new AtomicInteger(0);
            AtomicInteger cReceived   = new AtomicInteger(0);

            // A runnable to do the publishing
            Runnable runPublisher = () ->
                {
                try
                    {
                    for (int i = 0; i < 50 && fPublish.get(); i++)
                        {
                        publisher.publish(new Message(i, "Message-" + i))
                                .handle((v, err) ->
                                        {
                                        if (err != null)
                                            {
                                            err.printStackTrace();
                                            }
                                        else
                                            {
                                            cPublished.incrementAndGet();
                                            }
                                        return null;
                                        });
                        if (i > 5)
                            {
                            fPublishing.set(true);
                            }
                        pause();
                        }
                    }
                catch (Throwable t)
                    {
                    Logger.err("Error in publish loop", t);
                    }
                };

            // start the publisher thread
            Thread threadPublish = new Thread(runPublisher, "Test-Publisher");
            threadPublish.setDaemon(true);
f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - starting publisher thread");
            threadPublish.start();
f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - started publisher thread");

            // wait until a few messages have been published
            Eventually.assertDeferred(fPublishing::get, is(true));

            // start the subscriber runnable
            Runnable runSubscriber = () ->
                {
                for (int i = 0; i < 10 && fSubscribe.get(); i++)
                    {
                    try (Subscriber<Message> subscriber =  topic.createSubscriber(inGroup(sGroup)))
                        {
                        for (int j = 0; j < 5; j++)
                            {
                            try
                                {
                                Subscriber.Element<Message> element = subscriber.receive().get(1, TimeUnit.MINUTES);
                                element.commit();
                                //element.commitAsync().get(5, TimeUnit.MINUTES);
                                cReceived.incrementAndGet();
                                if (i >= 5)
                                    {
                                    fSubscribed.set(true);
                                    }
                                }
                            catch (Throwable t)
                                {
                                t.printStackTrace();
                                }
                            }
                        }
                    }
                };

            // start the subscriber thread
            Thread threadSubscribe = new Thread(runSubscriber, "Test-Subscriber");
            threadSubscribe.setDaemon(true);
f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - starting subscriber thread");
            threadSubscribe.start();
f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - started subscriber thread");

            // wait until we have received some messages
            Eventually.assertDeferred(fSubscribed::get, is(true));

            // Get one of the storage members
            CoherenceClusterMember member = s_storageCluster.stream().findAny().orElse(null);
            assertThat(member, is(notNullValue()));

            // Suspend the services - we do this via the storage member like the Operator would
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Suspending service " + sServiceName + " published=" + cPublished.get());
            CompletableFuture<Boolean> future = member.submit(() -> suspend(sServiceName));
            boolean fSuspended = future.get(5, TimeUnit.MINUTES);
            assertThat(fSuspended, is(true));
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Suspended service " + sServiceName + " published=" + cPublished.get());

            // shutdown the storage members
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Stopping storage. published=" + cPublished.get());
            s_storageCluster.close();

            // we should eventually have a single cluster member (which is this test JVM)
            Eventually.assertDeferred(() -> cluster.getMemberSet().size(), is(1));
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Stopped storage. published=" + cPublished.get());

            // restart the storage cluster
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Restarting storage. published=" + cPublished.get());
            s_storageCluster = startCluster("restarted");

            // we should eventually have three cluster members
            Eventually.assertDeferred(() -> cluster.getMemberSet().size(), is(3));
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Restarted storage. published=" + cPublished.get());

            IsServiceRunning isRunning = new IsServiceRunning(sServiceName);
            for (CoherenceClusterMember m : s_storageCluster)
                {
                Eventually.assertDeferred(() -> m.invoke(isRunning), is(true));
                }
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Restarted service " + sServiceName + " on all members");

            // The cache service should still be suspended so resume it via a storage member like the operator would
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Resuming service " + sServiceName + " published=" + cPublished.get());
            member = s_storageCluster.stream().findAny().orElse(null);
            assertThat(member, is(notNullValue()));
            Boolean fResumed = member.invoke(() -> resume(sServiceName));
            assertThat(fResumed, is(true));
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Resumed service " + sServiceName + " published=" + cPublished.get());
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - Awake. published=" + cPublished.get());

            // wait for the publisher and subscriber to finish
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - waiting for publisher thread");
            threadPublish.join(600000);
            f_watcher.println(">>>>> In shouldRecoverAfterCleanStorageRestart() - waiting for subscriber thread");
            threadSubscribe.join(120000);
            // we should have received at least as many as published (could be more if a commit did not succeed
            // during fail-over and the messages was re-received, but that is ok)
            f_watcher.println("Test complete: published=" + cPublished.get() + " received=" + cReceived.get());
            assertThat(cReceived.get(), is(greaterThanOrEqualTo(cPublished.get())));
            }
        catch (TimeoutException e)
            {
            for (CoherenceClusterMember member : s_storageCluster)
                {
                CompletableFuture<Void> f = member.invoke(() ->
                    {
                    Logger.info(GuardSupport.getThreadDump());
                    return null;
                    });
                try
                    {
                    f.get(5, TimeUnit.MINUTES);
                    }
                catch (Throwable ex)
                    {
                    ex.printStackTrace();
                    }
                }
            throw Exceptions.ensureRuntimeException(e);
            }
        catch (Throwable e)
            {
            throw Exceptions.ensureRuntimeException(e);
            }
        }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRecoverAfterStorageRestart() throws Exception
        {
        NamedTopic<Message>     topic        = ensureTopic("test-two");
        String                  sGroup       = "group-one";
        DistributedCacheService service      = (DistributedCacheService) topic.getService();
        Cluster                 cluster      = service.getCluster();
        String                  sServiceName = service.getInfo().getServiceName();

        // the test should not be storage enabled
        assertThat(service.isLocalStorageEnabled(), is(false));

        // create a subscriber group so that published messages are not lost before the subscriber subscribes
        topic.ensureSubscriberGroup(sGroup);

        try (Publisher<Message> publisher = topic.createPublisher())
            {
            AtomicBoolean fPublish    = new AtomicBoolean(true);
            AtomicBoolean fSubscribe  = new AtomicBoolean(true);
            AtomicBoolean fPublishing = new AtomicBoolean(false);
            AtomicBoolean fSubscribed = new AtomicBoolean(false);
            AtomicInteger cPublished  = new AtomicInteger(0);
            AtomicInteger cReceived   = new AtomicInteger(0);

            // A runnable to do the publishing
            Runnable runPublisher = () ->
                {
                try
                    {
                    for (int i = 0; i < 50 && fPublish.get(); i++)
                        {
                        publisher.publish(new Message(i, "Message-" + i))
                                .handle((v, err) ->
                                        {
                                        if (err != null)
                                            {
                                            err.printStackTrace();
                                            }
                                        cPublished.incrementAndGet();
                                        return null;
                                        });
                        if (i > 5)
                            {
                            fPublishing.set(true);
                            }
                        pause();
                        }
                    }
                catch (Throwable t)
                    {
                    Logger.err("Error in publish loop", t);
                    }
                };

            // start the publisher thread
            Thread threadPublish = new Thread(runPublisher, "Test-Publisher");
            threadPublish.setDaemon(true);
            threadPublish.start();

            // wait until a few messages have been published
            Eventually.assertDeferred(fPublishing::get, is(true));

            // start the subscriber runnable
            Runnable runSubscriber = () ->
                {
                try (Subscriber<Message> subscriber = topic.createSubscriber(inGroup(sGroup)))
                    {
                    while (fSubscribe.get())
                        {
                        try
                            {
                            Subscriber.Element<Message> element = subscriber.receive().get(30, TimeUnit.SECONDS);
                            element.commit();
                            //element.commitAsync().get(5, TimeUnit.MINUTES);
                            int c = cReceived.incrementAndGet();
                            if (c >= 5)
                                {
                                fSubscribed.set(true);
                                }
                            }
                        catch (Throwable t)
                            {
                            t.printStackTrace();
                            }
                        }
                    }
                };

            // start the subscriber thread
            Thread threadSubscribe = new Thread(runSubscriber, "Test-Subscriber");
            threadSubscribe.setDaemon(true);
            threadSubscribe.start();

            // wait until we have received some messages
            Eventually.assertDeferred(fSubscribed::get, is(true));

            // Get one of the storage members
            CoherenceClusterMember member = s_storageCluster.stream().findAny().orElse(null);
            assertThat(member, is(notNullValue()));

            // shutdown the storage members
            Logger.info("Stopping storage. published=" + cPublished.get());
            s_storageCluster.close();

            // we should eventually have a single cluster member (which is this test JVM)
            Eventually.assertDeferred(() -> cluster.getMemberSet().size(), is(1));
            Logger.info("Stopped storage. published=" + cPublished.get());

            // restart the storage cluster
            Logger.info("Restarting storage. published=" + cPublished.get());
            s_storageCluster = startCluster("restarted");

            // we should eventually have three cluster members
            Eventually.assertDeferred(() -> cluster.getMemberSet().size(), is(3));
            Logger.info("Restarted storage. published=" + cPublished.get());

            IsServiceRunning isRunning = new IsServiceRunning(sServiceName);
            for (CoherenceClusterMember m : s_storageCluster)
                {
                Eventually.assertDeferred(() -> m.invoke(isRunning), is(true));
                }
            Logger.info("Restarted service " + sServiceName + " on all members");

            // wait for the publisher and subscriber to finish
            threadPublish.join(600000);

            // should eventually have received all the messages
            Eventually.assertDeferred(cReceived::get, is(greaterThanOrEqualTo(cPublished.get())));

            // stop the subscriber
            fSubscribe.set(false);
            threadSubscribe.join(120000);

            // we should have received at least as many as published (could be more if a commit did not succeed
            // during fail-over and the messages was re-received, but that is ok)
            Logger.info("Test complete: published=" + cPublished.get() + " received=" + cReceived.get());
            }
        }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRecoverWaitingSubscriberAfterCleanStorageRestart() throws Exception
        {
        NamedTopic<Message>     topic        = ensureTopic("test-three");
        String                  sGroup       = "group-one";
        DistributedCacheService service      = (DistributedCacheService) topic.getService();
        Cluster                 cluster      = service.getCluster();
        String                  sServiceName = service.getInfo().getServiceName();

        // the test should not be storage enabled
        assertThat(service.isLocalStorageEnabled(), is(false));

        // create a subscriber group so that published messages are not lost before the subscriber subscribes
        topic.ensureSubscriberGroup(sGroup);

        try (Subscriber<Message> subscriberOne   = topic.createSubscriber(inGroup(sGroup));
             Subscriber<Message> subscriberTwo   = topic.createSubscriber(inGroup(sGroup));
             Subscriber<Message> subscriberThree = topic.createSubscriber(inGroup(sGroup)))
            {
            // topic is empty, futures will not complete yet...
            CompletableFuture<Subscriber.Element<Message>> futureOne = subscriberOne.receive();
            CompletableFuture<Subscriber.Element<Message>> futureTwo = subscriberTwo.receive();

            // Get one of the storage members
            CoherenceClusterMember member = s_storageCluster.stream().findAny().orElse(null);
            assertThat(member, is(notNullValue()));

            // Suspend the services - we do this via the storage member like the Operator would
            Logger.info(" Suspending service " + sServiceName);
            Boolean fSuspended = member.invoke(() -> suspend(sServiceName));
            assertThat(fSuspended, is(true));
            Logger.info("Suspended service " + sServiceName);

            // futures should not be completed
            assertThat(futureOne.isDone(), is(false));
            assertThat(futureTwo.isDone(), is(false));
            
            // shutdown the storage members
            Logger.info("Stopping storage.");
            s_storageCluster.close();

            // we should eventually have a single cluster member (which is this test JVM)
            Eventually.assertDeferred(() -> cluster.getMemberSet().size(), is(1));
            Logger.info("Stopped storage.");

            // futures should not be completed
            assertThat(futureOne.isDone(), is(false));
            assertThat(futureTwo.isDone(), is(false));

            // restart the storage cluster
            Logger.info("Restarting storage.");
            s_storageCluster = startCluster("restarted");

            // we should eventually have three cluster members
            Eventually.assertDeferred(() -> cluster.getMemberSet().size(), is(3));
            Logger.info("Restarted storage.");

            IsServiceRunning isRunning = new IsServiceRunning(sServiceName);
            for (CoherenceClusterMember m : s_storageCluster)
                {
                Eventually.assertDeferred(() -> m.invoke(isRunning), is(true));
                }
            Logger.info("Restarted service " + sServiceName + " on all members");

            // futures should not be completed
            assertThat(futureOne.isDone(), is(false));
            assertThat(futureTwo.isDone(), is(false));

            // The topics cache service should still be suspended so resume it via a storage member like the operator would
            Logger.info("Resuming service " + sServiceName);
            member = s_storageCluster.stream().findAny().orElse(null);
            assertThat(member, is(notNullValue()));
            Boolean fResumed = member.invoke(() -> resume(sServiceName));
            assertThat(fResumed, is(true));
            Logger.info("Resumed service " + sServiceName);

            // futures should not be completed
            assertThat(futureOne.isDone(), is(false));
            assertThat(futureTwo.isDone(), is(false));

            // subscriber from third subscriber
            CompletableFuture<Subscriber.Element<Message>> futureThree = subscriberThree.receive();

            int cChannel;
            try (Publisher<Message> publisher = topic.createPublisher(Publisher.OrderBy.roundRobin()))
                {
                // publish to every channel to ensure the subscriber receive a message
                cChannel = publisher.getChannelCount();
                Logger.info("Publishing " + cChannel + " messages");
                for (int i = 0; i < cChannel; i++)
                    {
                    Message message = new Message(i, "Message-" + i);
                    publisher.publish(message).get(1, TimeUnit.MINUTES);
                    Logger.info("Published " + message);
                    }
                }

            Eventually.assertDeferred(futureOne::isDone, is(true));
            Eventually.assertDeferred(futureTwo::isDone, is(true));
            Eventually.assertDeferred(futureThree::isDone, is(true));

            assertThat(futureOne.isCompletedExceptionally(), is(false));
            assertThat(futureTwo.isCompletedExceptionally(), is(false));
            assertThat(futureThree.isCompletedExceptionally(), is(false));

            List<Integer> listChannel = new ArrayList<>();
            Arrays.stream(subscriberOne.getChannels()).forEach(listChannel::add);
            Arrays.stream(subscriberTwo.getChannels()).forEach(listChannel::add);
            Arrays.stream(subscriberThree.getChannels()).forEach(listChannel::add);
            assertThat(listChannel.size(), is(cChannel));
            assertThat(listChannel.stream().distinct().count(), is((long) cChannel));
            }
        }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRecoverWaitingSubscriberAfterStorageRestart() throws Exception
        {
        NamedTopic<Message>     topic        = ensureTopic("test-four");
        String                  sGroup       = "group-one";
        DistributedCacheService service      = (DistributedCacheService) topic.getService();
        Cluster                 cluster      = service.getCluster();
        String                  sServiceName = service.getInfo().getServiceName();

        // the test should not be storage enabled
        assertThat(service.isLocalStorageEnabled(), is(false));

        // create a subscriber group so that published messages are not lost before the subscriber subscribes
        topic.ensureSubscriberGroup(sGroup);

        try (Subscriber<Message> subscriberOne   = topic.createSubscriber(inGroup(sGroup));
             Subscriber<Message> subscriberTwo   = topic.createSubscriber(inGroup(sGroup));
             Subscriber<Message> subscriberThree = topic.createSubscriber(inGroup(sGroup)))
            {
            // topic is empty, futures will not complete yet...
            CompletableFuture<Subscriber.Element<Message>> futureOne = subscriberOne.receive();
            CompletableFuture<Subscriber.Element<Message>> futureTwo = subscriberTwo.receive();

            // Get one of the storage members
            CoherenceClusterMember member = s_storageCluster.stream().findAny().orElse(null);
            assertThat(member, is(notNullValue()));

            // shutdown the storage members
            Logger.info("Stopping storage.");
            s_storageCluster.close();

            // we should eventually have a single cluster member (which is this test JVM)
            Eventually.assertDeferred(() -> cluster.getMemberSet().size(), is(1));
            Logger.info("Stopped storage.");

            // futures should not be completed
            assertThat(futureOne.isDone(), is(false));
            assertThat(futureTwo.isDone(), is(false));

            // restart the storage cluster
            Logger.info("Restarting storage.");
            s_storageCluster = startCluster("restarted");

            // we should eventually have three cluster members
            Eventually.assertDeferred(() -> cluster.getMemberSet().size(), is(3));
            Logger.info("Restarted storage.");

            IsServiceRunning isRunning = new IsServiceRunning(sServiceName);
            for (CoherenceClusterMember m : s_storageCluster)
                {
                Eventually.assertDeferred(() -> m.invoke(isRunning), is(true));
                }
            Logger.info("Restarted service " + sServiceName + " on all members");

            // futures should not be completed
            assertThat(futureOne.isDone(), is(false));
            assertThat(futureTwo.isDone(), is(false));

            // subscriber from third subscriber
            CompletableFuture<Subscriber.Element<Message>> futureThree = subscriberThree.receive();

            int cChannel;
            try (Publisher<Message> publisher = topic.createPublisher(Publisher.OrderBy.roundRobin()))
                {
                // publish to every channel to ensure the subscriber receive a message
                cChannel = publisher.getChannelCount();
                Logger.info("Publishing " + cChannel + " messages");
                for (int i = 0; i < cChannel; i++)
                    {
                    Message message = new Message(i, "Message-" + i);
                    publisher.publish(message).get(1, TimeUnit.MINUTES);
                    Logger.info("Published " + message);
                    }
                }

            Eventually.assertDeferred(futureOne::isDone, is(true));
            Eventually.assertDeferred(futureTwo::isDone, is(true));
            Eventually.assertDeferred(futureThree::isDone, is(true));

            assertThat(futureOne.isCompletedExceptionally(), is(false));
            assertThat(futureTwo.isCompletedExceptionally(), is(false));
            assertThat(futureThree.isCompletedExceptionally(), is(false));

            List<Integer> listChannel = new ArrayList<>();
            Arrays.stream(subscriberOne.getChannels()).forEach(listChannel::add);
            Arrays.stream(subscriberTwo.getChannels()).forEach(listChannel::add);
            Arrays.stream(subscriberThree.getChannels()).forEach(listChannel::add);
            assertThat(listChannel.size(), is(cChannel));
            assertThat(listChannel.stream().distinct().count(), is((long) cChannel));
            }
        }

    // ----- helper methods -------------------------------------------------

    static Boolean suspend(String sName)
        {
        System.err.println("**** TopicsStorageRecoveryTests Suspending service " + sName);
        System.err.flush();
        Cluster cluster = CacheFactory.ensureCluster();
        try
            {
            try (Timeout timeout = Timeout.after(2, TimeUnit.MINUTES))
                {
                cluster.suspendService(sName);
                }
            }
        catch (InterruptedException e)
            {
            System.err.println("Timeout suspending services");
            System.err.println(GuardSupport.getThreadDump());
            System.err.flush();
            throw new IllegalStateException("Timed out suspending service " + sName);
            }
        System.err.println("**** TopicsStorageRecoveryTests Suspended service " + sName);
        System.err.flush();
        return true;
        }

    static Boolean resume(String sName)
        {
        Logger.info("Resuming service " + sName);
        CacheFactory.ensureCluster().resumeService(sName);
        Logger.info("Resumed service " + sName);
        return true;
        }

    private void pause()
        {
        try
            {
            Thread.sleep(100);
            }
        catch (InterruptedException e)
            {
            // ignored
            }
        }

    @SuppressWarnings("unchecked")
    private <V> NamedTopic<V> ensureTopic(String sPrefix)
        {
        if (m_topic == null)
            {
            int cTopic = f_cTopic.getAndIncrement();
            m_topic = s_ccf.ensureTopic(getCacheName("simple-persistent-topic-" + sPrefix + "-" + cTopic));

//            String sSuffix = f_testName.getMethodName();
//            m_topic = s_ccf.ensureTopic("simple-persistent-topic-" + sSuffix);
            }
        return (NamedTopic<V>) m_topic;
        }

    private String getCacheName(String sPrefix)
        {
        return sPrefix + "-" + s_count.get();
        }

    private CoherenceCluster startCluster(String suffix)
        {
        CoherenceClusterBuilder builder = new CoherenceClusterBuilder();
        OptionsByType options = OptionsByType.of(s_options)
                        .addAll(LocalStorage.enabled(),
                                StabilityPredicate.none(),
                                Logging.at(9),
                                DisplayName.of(f_testName.getMethodName() + "-" + suffix),
                                s_testLogs.builder());

        builder.include(2, CoherenceClusterMember.class, options.asArray());

        return builder.build(LocalPlatform.get());
        }

    // ----- inner class: Message -------------------------------------------

    public static class Message
            implements ExternalizableLite
        {
        public Message()
            {
            }

        public Message(int id, String sValue)
            {
            m_id     = id;
            m_sValue = sValue;
            }

        @Override
        public void readExternal(DataInput in) throws IOException
            {
            m_id     = in.readInt();
            m_sValue = ExternalizableHelper.readUTF(in);
            }

        @Override
        public void writeExternal(DataOutput out) throws IOException
            {
            out.writeInt(m_id);
            ExternalizableHelper.writeUTF(out, m_sValue);
            }

        @Override
        public String toString()
            {
            return "Message(" +
                    "id=" + m_id +
                    ", value='" + m_sValue + '\'' +
                    ')';
            }

        private int m_id;

        private String m_sValue;
        }

    // ----- inner class: Watcher -------------------------------------------

    public static class Watcher
            extends TestWatcher
        {
        public Watcher()
            {
            try
                {
                File folder = MavenProjectFileUtils.ensureTestOutputBaseFolder(TopicsStorageRecoveryTests.class);
                f_out = new PrintWriter(new FileOutputStream(new File(folder, "TopicsStorageRecoveryTests.log"), true), true);
                }
            catch (FileNotFoundException e)
                {
                throw new RuntimeException(e);
                }
            }

        @Override
        protected void succeeded(Description description)
            {
            println(">>>>> Test Succeeded: " + description.getMethodName());
            }

        @Override
        protected void failed(Throwable e, Description description)
            {
            println(">>>>> Test Failed: " + description.getMethodName());
            }

        @Override
        protected void skipped(AssumptionViolatedException e, Description description)
            {
            println(">>>>> Test Skipped: " + description.getMethodName());
            }

        @Override
        protected void starting(Description description)
            {
            println(">>>>> Test Started: " + description.getMethodName());
            }

        @Override
        protected void finished(Description description)
            {
            println(">>>>> Test Finished: " + description.getMethodName());
            }

        private void println(String s)
            {
            System.err.println(s);
            System.err.flush();
            f_out.println(s);
            f_out.flush();
            }

        private final PrintWriter f_out;
        }

    // ----- constants ------------------------------------------------------

    private static final AtomicInteger s_count = new AtomicInteger();

    // ----- data members ---------------------------------------------------

    @ClassRule
    public static TestLogs s_testLogs = new TestLogs(TopicsRecoveryTests.class);

    @Rule
    public final Watcher f_watcher = new Watcher();

    @Rule
    public final TestName f_testName = new TestName();

    private static final OptionsByType s_options = OptionsByType.of(
            SystemProperty.of("coherence.guard.timeout", 60000),
            CacheConfig.of("simple-persistence-bdb-cache-config.xml"),
            OperationalOverride.of("common-tangosol-coherence-override.xml"),
            ClusterName.of(TopicsStorageRecoveryTests.class.getSimpleName())
    );

    private static CoherenceCluster s_storageCluster;

    private static ConfigurableCacheFactory s_ccf;

    private final AtomicInteger f_cTopic = new AtomicInteger(0);

    private NamedTopic<?> m_topic;
    }
