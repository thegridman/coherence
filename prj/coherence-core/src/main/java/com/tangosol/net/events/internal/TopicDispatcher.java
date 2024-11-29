/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */
package com.tangosol.net.events.internal;

import com.oracle.coherence.common.base.Continuation;
import com.tangosol.internal.net.ConfigurableCacheFactorySession;

import com.tangosol.net.TopicService;

import com.tangosol.net.events.EventDispatcher;

import com.tangosol.net.events.partition.cache.CacheLifecycleEvent;
import com.tangosol.net.events.topics.TopicLifecycleEvent;
import com.tangosol.net.events.topics.TopicLifecycleEventDispatcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of {@link TopicLifecycleEventDispatcher}.
 *
 * @author Jonathan Knight  2024.11.26
 */
public class TopicDispatcher
        extends AbstractEventDispatcher
        implements TopicLifecycleEventDispatcher
    {
    // ----- constructors ---------------------------------------------------

    /**
     * Construct a dispatcher for the specified topic name and service.
     *
     * @param sTopicName  the name of the topic dispatching events
     * @param service     the name of the associated service
     */
    public TopicDispatcher(String sTopicName, TopicService service)
        {
        super(EVENT_TYPES_TOPIC);

        f_sTopicName = sTopicName;
        f_service    = service;
        }

    // ----- TopicLifecycleEventDispatcher interface ---------------------------

    @Override
    public String getTopicName()
        {
        return f_sTopicName;
        }

    @Override
    public String getServiceName()
        {
        return f_service.getInfo().getServiceName();
        }

    @Override
    public String getScopeName()
        {
        return f_service.getTopicBackingMapManager().getCacheFactory().getScopeName();
        }

    /**
     * Return a continuation whose completion will cause a {@link
     * TopicLifecycleEvent} to be dispatched.
     *
     * @param eventType     the {@link TopicLifecycleEvent.Type} to raise
     * @param continuation  the continuation to complete after dispatching
     *
     * @return a continuation whose completion will post an invocation event
     */
    @SuppressWarnings("rawtypes")
    public Continuation getCacheLifecycleEventContinuation(TopicLifecycleEvent.Type eventType, Continuation continuation)
        {
        return getDispatchContinuation(new LifecycleEvent(this, eventType), continuation);
        }

    // ----- inner class: AbstractEvent -------------------------------------

    /**
     * An event implementation providing
     * access to the dispatcher.
     */
    protected abstract static class AbstractEvent<T extends Enum<T>>
            extends com.tangosol.net.events.internal.AbstractEvent<T>
        {

        // ----- constructors -----------------------------------------------

        /**
         * Construct an AbstractEvent with the provided dispatcher
         * and event type.
         *
         * @param dispatcher  the dispatcher that raised this event
         * @param eventType   the event type
         */
        public AbstractEvent(EventDispatcher dispatcher, T eventType)
            {
            super(dispatcher, eventType);
            }

        // ----- Event interface --------------------------------------------

        @Override
        public TopicDispatcher getDispatcher()
            {
            return (TopicDispatcher) m_dispatcher;
            }
        }

    // ----- inner class: LifecycleEvent ------------------------------------

    /**
     * {@link TopicLifecycleEvent} implementation raised by this dispatcher.
     */
    protected static class LifecycleEvent
            extends AbstractEvent<TopicLifecycleEvent.Type>
            implements TopicLifecycleEvent
        {
        // ----- constructors -----------------------------------------------

        /**
         * Construct a topic truncate event.
         *
         * @param dispatcher  the dispatcher that raised this event
         * @param eventType   the event type
         */
        protected LifecycleEvent(TopicDispatcher dispatcher, Type eventType)
            {
            super(dispatcher, eventType);
            }

        public TopicService getService()
            {
            return getDispatcher().f_service;
            }

        // ----- AbstractEvent methods --------------------------------------

        @Override
        protected boolean isMutableEvent()
            {
            return false;
            }

        @Override
        protected String getDescription()
            {
            return super.getDescription() +
                   ", Service=" + getServiceName() +
                   ", Topic=" + getTopicName();
            }

        @Override
        public TopicLifecycleEventDispatcher getEventDispatcher()
            {
            return (TopicLifecycleEventDispatcher) m_dispatcher;
            }

        @Override
        public String getTopicName()
            {
            return getDispatcher().getTopicName();
            }

        @Override
        public String getServiceName()
            {
            return getDispatcher().getServiceName();
            }

        @Override
        public String getScopeName()
            {
            return getDispatcher().getScopeName();
            }

        @Override
        public String getSessionName()
            {
            return getService()
                    .getTopicBackingMapManager()
                    .getCacheFactory()
                    .getResourceRegistry()
                    .getResource(String.class, ConfigurableCacheFactorySession.SESSION_NAME);
            }
        }


    // ----- constants and data members -------------------------------------

    /**
     * The event types raised by this dispatcher when it is storage disabled.
     */
    @SuppressWarnings("rawtypes")
    protected static final Set<Enum> EVENT_TYPES_TOPIC = new HashSet<>();

    /**
     * The name of the topic.
     */
    protected final String f_sTopicName;

    /**
     * The service this dispatcher is associated with.
     */
    protected final TopicService f_service;

    // ----- static initializer ---------------------------------------------

    static
        {
        EVENT_TYPES_TOPIC.addAll(Arrays.asList(TopicLifecycleEvent.Type.values()));
        }
    }