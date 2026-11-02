// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import dsl.flows.sde.parser.SourceLocationRange;

public record SourceGraphEdge(SourceLocationRange source, SourceLocationRange destination) {
    public static final SourceGraphEdge[] EMPTY_ARRAY = new SourceGraphEdge[0];
}
