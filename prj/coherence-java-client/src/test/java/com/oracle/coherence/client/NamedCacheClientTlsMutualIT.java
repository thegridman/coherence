/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */

package com.oracle.coherence.client;

import com.oracle.coherence.grpc.Requests;
import io.netty.handler.ssl.ClientAuth;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * An integration test for {@link NamedCacheClient} that creates instances of {@link NamedCacheClient}
 * with a TLS enabled server and client with mutual certification.
 *
 * @author Jonathan Knight  2019.11.07
 * @since 20.06
 */
class NamedCacheClientTlsMutualIT
        extends BaseNamedCacheClientIT
    {
    public NamedCacheClientTlsMutualIT()
        {
        super(s_serverHelper);
        }

    @RegisterExtension
    static ServerHelper s_serverHelper = new ServerHelper()
            .setProperty("coherence.ttl", "0")
            .setProperty("coherence.clustername", "GrpcServer")
            .setProperty("coherence.override", "coherence-json-override.xml")
            .setProperty("coherence.pof.config", "test-pof-config.xml")
            .setProperty("coherence.cacheconfig", "coherence-config.xml")
            .setProperty(Requests.PROP_CREDENTIALS, "tls")
            .setProperty(Requests.PROP_TLS_CERT, "ssl/server1.pem")
            .setProperty(Requests.PROP_TLS_KEY, "ssl/server1.key")
            .setProperty(Requests.PROP_TLS_CA, "ssl/ca.pem")
            .setProperty(Requests.PROP_TLS_CLIENT_AUTH, ClientAuth.REQUIRE.name())
            .setProperty("coherence.grpc.channels.default.credentials", Requests.CREDENTIALS_TLS)
            .setProperty("coherence.grpc.channels.default.tls.key", "ssl/client.key")
            .setProperty("coherence.grpc.channels.default.tls.cert", "ssl/client.pem")
            .setProperty("coherence.grpc.channels.default.tls.ca", "ssl/ca.pem")
            .setProperty("coherence.grpc.channels.default.tls.authority", "foo.test.google.fr")
            ;
    }
