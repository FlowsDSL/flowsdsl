// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.SourceFilenameAndLocationRange;

public class ExpressionConstant extends Expression {
    public final long value;
    public final int width;

    public ExpressionConstant(SourceFilenameAndLocationRange locationRange, long value, int width, int id) {
        super(locationRange, id);
        this.value = value;
        this.width = width;
    }
}
