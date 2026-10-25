// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.SourceFilenameAndLocationRange;

public class ExpressionPart extends Expression {
    public final Expression sub;
    public final String name;

    public ExpressionPart(SourceFilenameAndLocationRange locationRange, Expression sub, String name, int id) {
        super(locationRange, id);
        this.sub = sub;
        this.name = name;
    }
}
