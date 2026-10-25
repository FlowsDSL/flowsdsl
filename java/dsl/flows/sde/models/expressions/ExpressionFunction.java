// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.SourceFilenameAndLocationRange;

import java.util.Map;

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
}
