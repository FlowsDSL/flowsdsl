// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.parser.SourceFilenameAndLocationRange;

import java.util.Map;
import java.util.function.Consumer;

public class ExpressionFunction extends Expression {
    public final String name;
    public final Expression[] positionalParameters;
    public final Map<String, FieldOrParameter> namedParameters;

    public ExpressionFunction(SourceFilenameAndLocationRange locationRange, String name,
                              Expression[] positionalParameters, Map<String, FieldOrParameter> namedParameters,
                              int id) {
        super(locationRange, id);
        this.name = name;
        this.positionalParameters = positionalParameters;
        this.namedParameters = namedParameters;
    }

    @Override
    public void forEachChild(Consumer<Expression> consumer) {
        for (Expression positionalParameter : positionalParameters) {
            consumer.accept(positionalParameter);
        }
        for (FieldOrParameter namedParameter : namedParameters.values()) {
            consumer.accept(namedParameter.expression());
        }
    }
}
