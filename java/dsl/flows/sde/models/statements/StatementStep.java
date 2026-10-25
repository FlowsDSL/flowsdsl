// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.BinaryDecisionTreeAndVariableTerminals;

import java.util.LinkedList;
import java.util.TreeMap;

public class StatementStep extends StatementNonbranching {
    public TreeMap<VariableOrPort, BinaryDecisionTreeAndVariableTerminals> variableNextState = new TreeMap<>(
            VariableOrPort.COMPARATOR);
    public LinkedList<Statement> mergePoints = new LinkedList<>();

    public StatementStep(SourceFilenameAndLocationRange locationRange, String syntheticVariableSuffix, int id) {
        super(locationRange, syntheticVariableSuffix, id);
    }

    public String getAtVariableName() {
        return "A" + suffixForVariables;
    }
}
