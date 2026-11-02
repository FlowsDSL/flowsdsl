// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.parser.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.types.TypeReference;

import java.util.function.Consumer;

public class ExpressionIs extends Expression {
    public final Expression sub;
    public final TypeReference checkedType;

    public ExpressionIs(SourceFilenameAndLocationRange locationRange, Expression sub, TypeReference checkedType,
                        int id) {
        super(locationRange, id);
        this.sub = sub;
        this.checkedType = checkedType;
    }

    @Override
    public void forEachChild(Consumer<Expression> consumer) {
        consumer.accept(sub);
    }
}
