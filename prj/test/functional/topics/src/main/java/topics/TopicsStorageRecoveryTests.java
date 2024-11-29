/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */
package topics;

import com.oracle.bedrock.junit.CoherenceBuilder;

/**
 * This test verifies that topic publishers and subscribers recover from
 * a total loss of storage. In this case storage has active persistence
 * enabled and is removed via a clean shutdown (as would happen in k8s
 * using the operator).
 */
public class TopicsStorageRecoveryTests
    extends AbstractTopicsStorageRecoveryTests
    {
    @Override
    protected String getClientConfig()
        {
        return "simple-persistence-bdb-cache-config.xml";
        }

    @Override
    protected CoherenceBuilder getCoherenceBuilder()
        {
        return CoherenceBuilder.clusterMember();
        }
    }
