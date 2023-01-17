/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */
package com.tangosol.internal.net.topic.impl.paged;

import com.tangosol.internal.net.topic.impl.paged.model.PagedTopicSubscription;
import com.tangosol.internal.net.topic.impl.paged.model.SubscriberGroupId;
import com.tangosol.internal.net.topic.impl.paged.model.SubscriberId;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.stream.Collectors;

/**
 * A config map utility class for a {@link com.tangosol.net.PagedTopicService}.
 *
 * @author Jonathan Knight
 * @since 22.06.4
 */
public abstract class PagedTopicConfigMap
    {
    /**
     * Return a {@link Set} of topic names known to this config map.
     *
     * @return a {@link Set} of topic names known to this config map
     */
    public static Set<String> getTopicNames(Map<?, ?> configMap)
        {
        return configMap.keySet().stream()
                .filter(key -> key instanceof String)
                .map(String.class::cast)
                .collect(Collectors.toSet());
        }

    /**
     * Return a {@link Set} of {@link SubscriberGroupId subscriber group identifiers}
     * for a specific topic known to this config map.
     *
     * @param sTopicName  the name of the topic to get the groups for
     *
     * @return a {@link Set} of {@link SubscriberGroupId subscriber group identifiers}
     *         for a specific topic known to this config map
     */
    public static Set<SubscriberGroupId> getSubscriberGroups(Map<?, ?> configMap, String sTopicName)
        {
        return configMap.keySet().stream()
                .filter(v -> v instanceof PagedTopicSubscription.Key)
                .map(PagedTopicSubscription.Key.class::cast)
                .filter(key -> key.getTopicName().equals(sTopicName))
                .map(PagedTopicSubscription.Key::getGroupId)
                .collect(Collectors.toSet());
        }

    /**
     * Return a {@link Set} of {@link SubscriberId subscriber identifiers} for subscribers
     * that sre subscribed to a specific subscriber group for a specific topic known to
     * this config map.
     *
     * @param sTopicName  the name of the topic to get the groups for
     *
     * @return a {@link Set} of {@link SubscriberId subscriber identifiers} for subscribers
     *         that sre subscribed to a specific subscriber group for a specific topic known to
     *         this config map
     */
    public static Set<SubscriberId> getSubscribers(Map<?, ?> configMap, String sTopicName, SubscriberGroupId groupId)
        {
        PagedTopicSubscription subscription = getSubscription(configMap, sTopicName, groupId);
        if (subscription != null)
            {
            return new HashSet<>(subscription.getSubscribers().values());
            }
        return Collections.emptySet();
        }

    /**
     * Return an {@link Iterable} of {@link PagedTopicSubscription subscriptions}
     * known to this config map.
     *
     * @return an {@link Iterable} of {@link PagedTopicSubscription subscriptions}
     *         known to this config map
     */
    public static Iterable<PagedTopicSubscription> getSubscriptions(Map<?, ?> configMap)
        {
        return configMap.values().stream()
                .filter(v -> v instanceof PagedTopicSubscription)
                .map(PagedTopicSubscription.class::cast)
                .collect(Collectors.toList());
        }

    /**
     * Update this config map with the specified {@link PagedTopicSubscription subscription}.
     *
     * @param subscription  the {@link PagedTopicSubscription subscription} to update
     */
    public static void updateSubscription(Map<Object, Object> configMap, PagedTopicSubscription subscription)
        {
        configMap.put(subscription.getKey(), subscription);
        }

    /**
     * Return a {@link PagedTopicSubscription subscription}.
     *
     * @param lSubscriptionId  the identifier of the subscription
     *
     * @return the {@link PagedTopicSubscription subscription} or {@code null}
     *         if no {@link PagedTopicSubscription subscription} exists
     *         for the specified identifier
     */
    public static PagedTopicSubscription getSubscription(Map<?, ?> configMap, long lSubscriptionId)
        {
        return configMap.values().stream()
                .filter(PagedTopicSubscription.class::isInstance)
                .map(PagedTopicSubscription.class::cast)
                .filter(s -> s.getSubscriptionId() == lSubscriptionId)
                .findFirst()
                .orElse(null);
        }

    /**
     * Return a {@link PagedTopicSubscription subscription}.
     *
     * @param sTopicName  the name of the topic
     * @param groupId     the subscriber group identifier
     *
     * @return the {@link PagedTopicSubscription subscription} or {@code null}
     *         if no {@link PagedTopicSubscription subscription} exists
     *         for the specified topic and subscriber group identifier
     */
    public static PagedTopicSubscription getSubscription(Map<?, ?> configMap, String sTopicName, SubscriberGroupId groupId)
        {
        return (PagedTopicSubscription) configMap.get(new PagedTopicSubscription.Key(sTopicName, groupId));
        }

    /**
     * Return a {@link PagedTopicSubscription subscription} identifier.
     *
     * @param sTopicName  the name of the topic
     * @param groupId     the subscriber group identifier
     *
     * @return the {@link PagedTopicSubscription subscription} identifier or
     *         zero if no {@link PagedTopicSubscription subscription} exists
     *         for the specified topic and subscriber group identifier
     */
    public static long getSubscriptionId(Map<?, ?> configMap, String sTopicName, SubscriberGroupId groupId)
        {
        PagedTopicSubscription subscription = getSubscription(configMap, sTopicName, groupId);
        return subscription == null ? 0 : subscription.getSubscriptionId();
        }

    /**
     * Remove a subscription from this config map.
     *
     * @param key  the {@link PagedTopicSubscription.Key key} of the subscription to remove
     */
    public static void removeSubscription(Map<Object, Object> configMap, PagedTopicSubscription.Key key)
        {
        configMap.computeIfPresent(key, (k, v) -> null);
        }

    /**
     * Returns {@code true} if the specified subscription exists with the
     * specified subscriber subscribed to it.
     * 
     * @param lSubscriptionId  the subscription identifier
     * @param subscriberId     the subscriber identifier
     *
     * @return  {@code true} if the specified subscription exists with the
     *          specified subscriber subscribed to it
     */
    public static boolean hasSubscription(Map<?, ?> configMap, long lSubscriptionId, SubscriberId subscriberId)
        {
        PagedTopicSubscription subscription = getSubscription(configMap, lSubscriptionId);
        if (subscription != null)
            {
            return subscriberId == null
                    || subscription.hasSubscriber(subscriberId);
            }
        return false;
        }

    /**
     * Remove configurations for the specified topic name.
     *
     * @param sTopicName  the name of the topic to remove
     */
    public static void removeTopic(Map<?, ?> configMap, String sTopicName)
        {
        // remove the topic entry
        configMap.remove(sTopicName);
        // remove all the subscriptions for the topic
        Set<PagedTopicSubscription.Key> setKeys   = configMap.keySet().stream()
                .filter(v -> v instanceof PagedTopicSubscription.Key)
                .map(PagedTopicSubscription.Key.class::cast)
                .filter(key -> key.getTopicName().equals(sTopicName))
                .collect(Collectors.toSet());
        setKeys.forEach(configMap::remove);
        }
    }
