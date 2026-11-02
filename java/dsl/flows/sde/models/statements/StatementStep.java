// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.models.terminals.VersionTerminal;
import dsl.flows.sde.parser.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.BinaryDecisionTreeAndVariableTerminals;

import java.util.LinkedList;
import java.util.TreeMap;

public class StatementStep extends StatementNonbranching {
    public static class TerminatingRegionAndVersion {
        public final StatementStep terminatingRegion;
        public final TreeMap<VariableOrPort, VersionTerminal> versionByVariableOrPort;

        public TerminatingRegionAndVersion(StatementStep terminatingRegion) {
            this.terminatingRegion = terminatingRegion;
            this.versionByVariableOrPort = new TreeMap<>();
        }
    }

    public TreeMap<VariableOrPort, BinaryDecisionTreeAndVariableTerminals> nextStateByVariable = new TreeMap<>(
            VariableOrPort.COMPARATOR);
    public LinkedList<Statement> mergePoints = new LinkedList<>();
    public LinkedList<TerminatingRegionAndVersion> terminatingRegions = new LinkedList<>();

    public StatementStep(SourceFilenameAndLocationRange locationRange, String syntheticVariableSuffix, int id) {
        super(locationRange, syntheticVariableSuffix, id);
    }

    public String getAtVariableName() {
        return "A" + suffixForVariables;
    }
}
