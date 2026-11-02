// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.parser.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.types.TypeReference;

import java.util.function.Consumer;

public abstract class Expression {
    public static final Expression[] EMPTY_ARRAY = new Expression[0];

    public final SourceFilenameAndLocationRange locationRange;
    public final int id;
    public TypeReference resolvedTypeReference;

    protected Expression(SourceFilenameAndLocationRange locationRange, int id) {
        this.locationRange = locationRange;
        this.id = id;
    }

    public abstract void forEachChild(Consumer<Expression> consumer);
}
