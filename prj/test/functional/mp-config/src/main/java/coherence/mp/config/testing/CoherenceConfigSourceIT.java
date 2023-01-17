/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */
package coherence.mp.config.testing;

import com.oracle.bedrock.testsupport.deferred.Eventually;

import com.oracle.coherence.cdi.CoherenceExtension;
import com.oracle.coherence.cdi.server.CoherenceServerExtension;

import com.oracle.coherence.mp.config.CoherenceConfigSource;
import com.oracle.coherence.mp.config.ConfigPropertyChanged;
import org.eclipse.microprofile.config.Config;

import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

import org.hamcrest.MatcherAssert;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import jakarta.inject.Inject;

import static com.oracle.bedrock.deferred.DeferredHelper.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link CoherenceConfigSource}.
 *
 * @author Aleks Seovic  2019.10.12
 */
@ExtendWith(WeldJunit5Extension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoherenceConfigSourceIT
    {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                        .addExtension(new CoherenceExtension())
                                                        .addPackages(CoherenceExtension.class)
                                                        .addExtension(new CoherenceServerExtension())
                                                        .addPackages(CoherenceServerExtension.class)
                                                        .addPackages(CoherenceConfigSource.class)
                                                        .addBeanClass(TestObserver.class));

    @BeforeAll
    static void setup()
        {
        System.setProperty("coherence.member", "sysprop01");
        System.setProperty("config.value", "sysprop");
        }

    @BeforeEach
    void clearSystemProperties()
        {
        MatcherAssert.assertThat(System.getProperty("coherence.config.ordinal"), is(nullValue()));

        config = getConfig();
        source = getCoherenceSource(config);
        source.getConfigMap().truncate();
        }

    private static Config getConfig()
        {
        ConfigProviderResolver resolver = ConfigProviderResolver.instance();
        ConfigBuilder          builder  = resolver.getBuilder();

        return builder.addDefaultSources()
                .addDiscoveredSources()
                .build();
        }

    private static CoherenceConfigSource getCoherenceSource(Config config)
        {
        for (ConfigSource source : config.getConfigSources())
            {
            if (source instanceof CoherenceConfigSource)
                {
                return (CoherenceConfigSource) source;
                }
            }
        throw new IllegalStateException("CoherenceConfigSource is not in a list of sources");
        }

    // ----- test methods ---------------------------------------------

    @Test
    void testDefaults()
        {
        source.setValue("config.value", "value");

        assertThat(source.getProperties().entrySet(), hasSize(1));
        assertThat(source.getProperties(), hasKey("config.value"));
        MatcherAssert.assertThat(source.getPropertyNames(), hasSize(1));
        MatcherAssert.assertThat(source.getPropertyNames(), hasItem("config.value"));
        assertThat(source.getValue("config.value"), is("value"));
        assertThat(source.getOrdinal(), is(500));
        assertThat(source.getName(), is("CoherenceConfigSource"));
        }

    @Test
    void testDefaultPriority()
        {
        source.setValue("config.value", "cache");

        MatcherAssert.assertThat(config.getValue("coherence.cluster", String.class), is("test"));
        MatcherAssert.assertThat(config.getValue("coherence.role", String.class), is("proxy"));
        MatcherAssert.assertThat(config.getValue("coherence.member", String.class), is("sysprop01"));
        MatcherAssert.assertThat(config.getValue("coherence.distributed.localstorage", String.class), is("true"));
        MatcherAssert.assertThat(config.getValue("config.value", String.class), is("cache"));
        }

    @Test
    void testLowPriority()
        {
        System.setProperty("coherence.config.ordinal", "100");

        Config config = getConfig();
        MatcherAssert.assertThat(config.getValue("config.value", String.class), is("sysprop"));

        System.clearProperty("coherence.config.ordinal");
        MatcherAssert.assertThat(System.getProperty("coherence.config.ordinal"), is(nullValue()));
        }

    @Test
    void testChangeNotification()
        {
        source.setValue("config.value", "one");
        Eventually.assertDeferred(() -> observer.getLatestValue(), is("one"), within(2, TimeUnit.MINUTES));

        source.setValue("config.value", "two");
        Eventually.assertDeferred(() -> observer.getLatestValue(), is("two"), within(2, TimeUnit.MINUTES));

        source.getConfigMap().remove("config.value");
        Eventually.assertDeferred(() -> observer.getLatestValue(), is(nullValue()), within(2, TimeUnit.MINUTES));
        }

    @ApplicationScoped
    static class TestObserver
        {
        String latestValue;

        public String getLatestValue()
            {
            return latestValue;
            }

        void observer(@Observes ConfigPropertyChanged event)
            {
            System.out.println("[TestObserver.observer] : " + event);
            latestValue = event.getValue();
            }
        }

    // ----- data members ---------------------------------------------

    @Inject
    private TestObserver observer;

    private Config config;
    private CoherenceConfigSource source;
    }
