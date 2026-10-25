// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.models.statements.Statement;
import dsl.flows.sde.models.statements.StatementIfWhile;
import dsl.flows.sde.models.statements.StatementNonbranching;
import dsl.flows.sde.models.statements.StatementStep;

import java.util.LinkedList;

public class RegionMarker {
    final CompilationErrorList compilationErrorList;
    final boolean[] visiting;
    final boolean[] inRegion;
    final LinkedList<StatementStep> allTerminations;
    boolean foundCycle;

    public RegionMarker(CompilationErrorList compilationErrorList, boolean[] visiting, boolean[] inRegion,
                        LinkedList<StatementStep> allTerminations) {
        this.compilationErrorList = compilationErrorList;
        this.visiting = visiting;
        this.inRegion = inRegion;
        this.allTerminations = allTerminations;
    }

    void markRegionAndCollectTerminations(Statement node) {
        if (visiting[node.id]) {
            compilationErrorList.addError("Cycles are not allowed within one step", node.locationRange);
            foundCycle = true;
            return;
        }
        visiting[node.id] = true;
        if (!inRegion[node.id]) {
            inRegion[node.id] = true;
            switch (node) {
                case StatementStep cast -> allTerminations.add(cast);
                case StatementIfWhile cast -> {
                    markRegionAndCollectTerminations(cast.thenSuccessor);
                    if (cast.elseSuccessor != null) {
                        markRegionAndCollectTerminations(cast.elseSuccessor);
                    }
                }
                case StatementNonbranching cast -> markRegionAndCollectTerminations(cast.unconditionalSuccessor);
                default -> {
                }
            }
        }
        visiting[node.id] = false;
    }
}
