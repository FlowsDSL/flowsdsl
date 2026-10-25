// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.NamedEntity;

import java.util.Comparator;
import java.util.TreeSet;

public interface VariableOrPort extends NamedEntity, Comparable<VariableOrPort> {
    Comparator<VariableOrPort> COMPARATOR = Comparator.comparingInt(VariableOrPort::getId);

    int getId();

    String getName();

    TreeSet<StatementExpression> getAssignments();

    SourceFilenameAndLocationRange getLocationRange();
}
