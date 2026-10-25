// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.types.TypeReference;

import java.util.Map;

public class ExpressionObject extends Expression {
    public final Map<String, FieldOrParameter> fields;

    public ExpressionObject(SourceFilenameAndLocationRange locationRange, TypeReference typeReference,
                            Map<String, FieldOrParameter> fields, int id) {
        super(locationRange, id);
        this.resolvedTypeReference = typeReference;
        this.fields = fields;
    }
}
