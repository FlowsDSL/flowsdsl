// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.parser.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.NamedEntity;
import dsl.flows.sde.models.statements.VariableOrPort;
import dsl.flows.sde.models.types.BitType;

import java.util.function.Consumer;

public class ExpressionName extends Expression {
    public final String name;
    public NamedEntity resolvedEntity;

    public ExpressionName(SourceFilenameAndLocationRange locationRange, String name, int id) {
        super(locationRange, id);
        this.name = name;
    }

    public ExpressionName(SourceFilenameAndLocationRange locationRange, VariableOrPort variableOrPort, BitType bitType,
                          int id) {
        super(locationRange, id);
        this.name = variableOrPort.getName();
        this.resolvedEntity = variableOrPort;
        this.resolvedTypeReference = variableOrPort.getTypeReference(bitType);
    }

    @Override
    public void forEachChild(Consumer<Expression> consumer) {
    }
}
