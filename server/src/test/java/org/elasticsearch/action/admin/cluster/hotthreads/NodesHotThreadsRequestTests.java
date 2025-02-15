/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.cluster.hotthreads;

import org.elasticsearch.Version;
import org.elasticsearch.action.admin.cluster.node.hotthreads.NodesHotThreadsRequest;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.monitor.jvm.HotThreads;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.VersionUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class NodesHotThreadsRequestTests extends ESTestCase {

    public void testBWCSerialization() throws IOException {
        TimeValue sampleInterval = new TimeValue(50, TimeUnit.MINUTES);

        NodesHotThreadsRequest request = new NodesHotThreadsRequest("123");
        request.threads(4);
        request.ignoreIdleThreads(false);
        request.type(HotThreads.ReportType.BLOCK);
        request.interval(sampleInterval);
        request.snapshots(3);

        Version latest = Version.CURRENT;
        Version previous = VersionUtils.randomVersionBetween(random(),
            VersionUtils.getFirstVersion(), VersionUtils.getPreviousVersion(Version.CURRENT));

        try (BytesStreamOutput out = new BytesStreamOutput()) {
            out.setVersion(latest);
            request.writeTo(out);
            try (StreamInput in = out.bytes().streamInput()) {
                in.setVersion(previous);
                NodesHotThreadsRequest deserialized = new NodesHotThreadsRequest(in);
                assertEquals(request.threads(), deserialized.threads());
                assertEquals(request.ignoreIdleThreads(), deserialized.ignoreIdleThreads());
                assertEquals(request.type(), deserialized.type());
                assertEquals(request.interval(), deserialized.interval());
                assertEquals(request.snapshots(), deserialized.snapshots());

            }
        }

        try (BytesStreamOutput out = new BytesStreamOutput()) {
            out.setVersion(previous);
            request.writeTo(out);
            try (StreamInput in = out.bytes().streamInput()) {
                in.setVersion(previous);
                NodesHotThreadsRequest deserialized = new NodesHotThreadsRequest(in);
                assertEquals(request.threads(), deserialized.threads());
                assertEquals(request.ignoreIdleThreads(), deserialized.ignoreIdleThreads());
                assertEquals(request.type(), deserialized.type());
                assertEquals(request.interval(), deserialized.interval());
                assertEquals(request.snapshots(), deserialized.snapshots());
            }
        }
    }
}
