/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */

package com.oracle.coherence.grpc;

import com.google.protobuf.Message;

/**
 * A {@link GrpcServiceProtocol} to support the {@link com.tangosol.net.topic.NamedTopic} API.
 *
 * @param <Req>   the request type
 * @param <Resp>  the response type
 *
 * @author Jonathan Knight  2025.01.25
 */
public interface NamedTopicProtocol<Req extends Message, Resp extends Message>
        extends GrpcServiceProtocol<Req, Resp>
    {
    @Override
    default String getProtocol()
        {
        return PROTOCOL_NAME;
        }

    @Override
    default int getVersion()
        {
        return VERSION;
        }

    @Override
    default int getSupportedVersion()
        {
        return SUPPORTED_VERSION;
        }

    // ----- constants ------------------------------------------------------

    /**
     * The protocol name.
     */
    String PROTOCOL_NAME = "TopicService";

    /**
     * The current protocol version.
     */
    int VERSION = 1;

    /**
     * The minimum supported protocol version.
     */
    int SUPPORTED_VERSION = 1;
    }
