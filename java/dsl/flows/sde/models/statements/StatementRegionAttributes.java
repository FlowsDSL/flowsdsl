// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.models.BinaryDecisionTree;
import dsl.flows.sde.models.BinaryDecisionTreeAndVariableTerminals;
import dsl.flows.sde.models.terminals.VersionTerminal;

import java.util.TreeMap;

public class StatementRegionAttributes {
    public final BinaryDecisionTree reachabilityTree;
    public final TreeMap<VariableOrPort, BinaryDecisionTreeAndVariableTerminals> mergedValueByVariableOrPort;
    public final Statement onlyPredecessor;
    public final TreeMap<VariableOrPort, VersionTerminal> versionAtEntryByVariableOrPort;

    public StatementRegionAttributes(BinaryDecisionTree reachabilityTree, Statement onlyPredecessor) {
        this.reachabilityTree = reachabilityTree;
        this.mergedValueByVariableOrPort = onlyPredecessor == null ? new TreeMap<>() : null;
        this.onlyPredecessor = onlyPredecessor;
        this.versionAtEntryByVariableOrPort = new TreeMap<>();
    }
}
