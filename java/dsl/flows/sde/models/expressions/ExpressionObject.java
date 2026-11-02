// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.parser.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.types.TypeReference;

import java.util.Map;
import java.util.function.Consumer;

public class ExpressionObject extends Expression {
    public final Map<String, FieldOrParameter> fields;

    public ExpressionObject(SourceFilenameAndLocationRange locationRange, TypeReference typeReference,
                            Map<String, FieldOrParameter> fields, int id) {
        super(locationRange, id);
        this.resolvedTypeReference = typeReference;
        this.fields = fields;
    }

    @Override
    public void forEachChild(Consumer<Expression> consumer) {
        for (FieldOrParameter field : fields.values()) {
            consumer.accept(field.expression());
        }
    }
}
