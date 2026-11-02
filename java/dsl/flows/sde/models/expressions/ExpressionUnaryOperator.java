// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.parser.SourceFilenameAndLocationRange;

import java.util.function.Consumer;

public class ExpressionUnaryOperator extends Expression {
    public enum Kind {BitwiseNot, LogicalNot}

    public final Kind kind;
    public final Expression sub;

    public ExpressionUnaryOperator(SourceFilenameAndLocationRange locationRange, Kind kind, Expression sub, int id) {
        super(locationRange, id);
        this.kind = kind;
        this.sub = sub;
    }

    @Override
    public void forEachChild(Consumer<Expression> consumer) {
        consumer.accept(sub);
    }
}
