// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.build;

import dsl.flows.sde.models.BinaryDecisionTree;
import dsl.flows.sde.models.BinaryDecisionTreeAndVariableTerminals;
import dsl.flows.sde.models.Transform;
import dsl.flows.sde.models.expressions.Expression;
import dsl.flows.sde.models.expressions.ExpressionName;
import dsl.flows.sde.models.statements.*;
import dsl.flows.sde.models.terminals.VersionTerminal;
import dsl.flows.sde.models.terminals.VersionTerminalAssigned;
import dsl.flows.sde.models.terminals.VersionTerminalBeginningOfCycle;
import dsl.flows.sde.models.terminals.VersionTerminalMerged;
import dsl.flows.sde.parser.SourceLocationRange;
import dsl.flows.sde.ui.SourceGraphEdge;

import java.util.LinkedList;
import java.util.TreeMap;
import java.util.function.Consumer;

public class DependencyGraphTracer {
    record NodeKey(VariableOrPort variable, Statement statement, boolean merged) implements Comparable<NodeKey> {
        @Override
        public int compareTo(NodeKey o) {
            int c = variable.compareTo(o.variable);
            if (c == 0) {
                c = statement.id - o.statement.id;
            }
            if (c == 0) {
                c = Boolean.compare(merged, o.merged);
            }
            return c;
        }
    }

    final StatementStep region;
    final VariableOrPort startVariable;
    final TreeMap<NodeKey, Boolean> reachesStart;
    final LinkedList<SourceGraphEdge> dependencyGraph;

    DependencyGraphTracer(StatementStep region, VariableOrPort startVariable, Transform transform) {
        this.region = region;
        this.startVariable = startVariable;
        this.reachesStart = new TreeMap<>();
        dependencyGraph = new LinkedList<>();
    }

    boolean traceTree(VariableOrPort variable, BinaryDecisionTreeAndVariableTerminals treeAndTerminals,
                      SourceLocationRange destination) {
        boolean result = false;
        for (VersionTerminal terminal : treeAndTerminals.terminals()) {
            if (traceVersion(variable, terminal)) {
                result = true;
                dependencyGraph.add(new SourceGraphEdge(definitionLocation(terminal), destination));
            }
        }
        for (BinaryDecisionTree.Node node : treeAndTerminals.tree().nodes()) {
            result |= traceReads(node.condition(), node.condition().condition);
        }
        return result;
    }

    boolean traceVersion(VariableOrPort variable, VersionTerminal terminal) {
        if (terminal instanceof VersionTerminalBeginningOfCycle) {
            return variable == startVariable;
        }
        NodeKey key;
        if (terminal instanceof VersionTerminalAssigned cast && cast.region == region) {
            key = new NodeKey(variable, cast.statement, false);
        } else if (terminal instanceof VersionTerminalMerged cast && cast.region == region) {
            key = new NodeKey(variable, cast.statement, true);
        } else {
            return false;
        }
        Boolean memoized = reachesStart.get(key);
        if (memoized != null) {
            return memoized;
        }
        boolean reaches;
        if (key.merged) {
            reaches = traceTree(variable,
                    key.statement.regionAttributes.get(region).mergedValueByVariableOrPort.get(variable),
                    key.statement.locationRange);
        } else {
            reaches = traceReads(key.statement, ((StatementExpression) key.statement).expression);
        }
        reachesStart.put(key, reaches);
        return reaches;
    }

    boolean traceReads(Statement statement, Expression expression) {
        TreeMap<VariableOrPort, VersionTerminal> versions = statement.regionAttributes.get(
                region).versionAtEntryByVariableOrPort;
        boolean[] result = new boolean[1];
        forEachExpressionName(expression, name -> {
            if (name.resolvedEntity instanceof VariableOrPort castVariableOrPort) {
                VersionTerminal version = versions.get(castVariableOrPort);
                if (traceVersion(castVariableOrPort, version)) {
                    result[0] = true;
                    dependencyGraph.add(new SourceGraphEdge(definitionLocation(version), name.locationRange));
                }
            }
        });
        return result[0];
    }

    void forEachExpressionName(Expression expression, Consumer<ExpressionName> consumer) {
        if (expression instanceof ExpressionName cast) {
            consumer.accept(cast);
        }
        expression.forEachChild(child -> forEachExpressionName(child, consumer));
    }

    SourceLocationRange definitionLocation(VersionTerminal terminal) {
        if (terminal instanceof VersionTerminalAssigned cast) {
            return cast.statement.assignmentTargetLocationRange;
        }
        if (terminal instanceof VersionTerminalMerged cast) {
            return cast.statement.locationRange;
        }
        return region.locationRange;
    }
}
