// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.models.BinaryDecisionTree;
import dsl.flows.sde.models.statements.*;

public class ReachabilityTreeBuilder {
    final StatementStep region;
    final boolean[] inRegion;

    public ReachabilityTreeBuilder(StatementStep region, boolean[] inRegion) {
        this.region = region;
        this.inRegion = inRegion;
    }

    void build(Statement node) {
        if (node.regionAttributes.containsKey(region)) {
            return;
        }
        BinaryDecisionTree tree = BinaryDecisionTree.NOT_REACHED;
        int totalPredecessorsInRegion = 0;
        Statement onlyPredecessor = null;
        for (StatementNonbranching predecessor : node.unconditionalPredecessors) {
            if (predecessor instanceof StatementStep) {
                if (predecessor == region) {
                    tree = BinaryDecisionTree.REACHED;
                    totalPredecessorsInRegion++;
                    onlyPredecessor = predecessor;
                }
            } else if (inRegion[predecessor.id]) {
                build(predecessor);
                tree = BinaryDecisionTree.merge(tree, predecessor.regionAttributes.get(region).reachabilityTree);
                totalPredecessorsInRegion++;
                onlyPredecessor = predecessor;
            }
        }
        for (StatementIfWhile predecessor : node.thenPredecessors) {
            if (inRegion[predecessor.id]) {
                build(predecessor);
                BinaryDecisionTree predecessorReachabilityTree = predecessor.regionAttributes.get(
                        region).reachabilityTree;
                tree = BinaryDecisionTree.merge(tree,
                        BinaryDecisionTree.replaceReachedWithCondition(predecessorReachabilityTree, predecessor,
                                BinaryDecisionTree.REACHED_TERMINAL, BinaryDecisionTree.NOT_REACHED_TERMINAL));
                totalPredecessorsInRegion++;
                onlyPredecessor = predecessor;
            }
        }
        for (StatementIfWhile predecessor : node.elsePredecessors) {
            if (inRegion[predecessor.id]) {
                build(predecessor);
                BinaryDecisionTree predecessorReachabilityTree = predecessor.regionAttributes.get(
                        region).reachabilityTree;
                tree = BinaryDecisionTree.merge(tree,
                        BinaryDecisionTree.replaceReachedWithCondition(predecessorReachabilityTree, predecessor,
                                BinaryDecisionTree.NOT_REACHED_TERMINAL, BinaryDecisionTree.REACHED_TERMINAL));
                totalPredecessorsInRegion++;
                onlyPredecessor = predecessor;
            }
        }
        node.regionAttributes.put(region,
                new StatementRegionAttributes(tree, totalPredecessorsInRegion == 1 ? onlyPredecessor : null));
        if (totalPredecessorsInRegion > 1) {
            region.mergePoints.add(node);
        }
    }
}
