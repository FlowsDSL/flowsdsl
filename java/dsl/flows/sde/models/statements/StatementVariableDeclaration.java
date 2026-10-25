// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.types.BitType;
import dsl.flows.sde.models.types.TypeReference;

import java.util.TreeSet;

public class StatementVariableDeclaration extends StatementNonbranching implements VariableOrPort {
    public final SourceFilenameAndLocationRange macroScope;
    public final TypeReference typeReference;
    public final SourceFilenameAndLocationRange nameLocationRange;
    public final String name;
    public TreeSet<StatementExpression> assignments = new TreeSet<>(Statement.COMPARATOR);

    public StatementVariableDeclaration(SourceFilenameAndLocationRange locationRange,
                                        SourceFilenameAndLocationRange macroScope, TypeReference typeReference,
                                        SourceFilenameAndLocationRange nameLocationRange, String name, int id) {
        super(locationRange, null, id);
        this.macroScope = macroScope;
        this.typeReference = typeReference;
        this.name = name;
        this.nameLocationRange = nameLocationRange;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getName() {
        return (macroScope == null ? 'S' : 'M' + macroScope.start.getNameSuffixForVariables()) + name;
    }

    @Override
    public TypeReference getTypeReference(BitType bitType) {
        return typeReference;
    }

    @Override
    public TreeSet<StatementExpression> getAssignments() {
        return assignments;
    }

    @Override
    public SourceFilenameAndLocationRange getLocationRange() {
        return locationRange;
    }

    @Override
    public int compareTo(VariableOrPort other) {
        return getId() - other.getId();
    }
}
