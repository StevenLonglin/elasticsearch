/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.transform.transforms;

import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.index.query.QueryBuilder;

public interface SyncConfig extends ToXContentObject, NamedWriteable {

    String getField();

    QueryBuilder getRangeQuery(TransformCheckpoint newCheckpoint);

    QueryBuilder getRangeQuery(TransformCheckpoint oldCheckpoint, TransformCheckpoint newCheckpoint);
}
