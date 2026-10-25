// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.SourceFilenameAndLocationRange;

public class ExpressionBinaryOperator extends Expression {
    public enum Kind {
        Equals, NotEquals, BooleanAnd, BooleanOr, Less, More, LessOrEqual, MoreOrEqual, BitwiseOr, IntegerSum,
        IntegerDifference, IntegerProduct
    }

    public final Kind kind;
    public final Expression a, b;

    public ExpressionBinaryOperator(SourceFilenameAndLocationRange locationRange, Kind kind, Expression a, Expression b,
                                    int id) {
        super(locationRange, id);
        this.kind = kind;
        this.a = a;
        this.b = b;
    }
}
