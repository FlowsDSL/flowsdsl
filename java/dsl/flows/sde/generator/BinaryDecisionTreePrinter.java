// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.generator;

import dsl.flows.sde.models.BinaryDecisionTree;
import dsl.flows.sde.models.BinaryDecisionTreeAndVariableTerminals;
import dsl.flows.sde.models.statements.VariableOrPort;
import dsl.flows.sde.models.terminals.VersionTerminal;

class BinaryDecisionTreePrinter {
    final VariableOrPort variableOrPort;
    final String namePrefix, conditionPrefix;
    final BinaryDecisionTree tree;
    final VersionTerminal[] terminals;
    final int[] references;

    BinaryDecisionTreePrinter(VariableOrPort variableOrPort, String namePrefix, String conditionPrefix,
                              BinaryDecisionTreeAndVariableTerminals binaryDecisionTreeAndVariableTerminals) {
        this.variableOrPort = variableOrPort;
        this.namePrefix = namePrefix;
        this.conditionPrefix = conditionPrefix;
        this.tree = binaryDecisionTreeAndVariableTerminals.tree();
        this.terminals = binaryDecisionTreeAndVariableTerminals.terminals();
        references = new int[tree.getSize()];
        for (int index = 0; index < references.length; index++) {
            BinaryDecisionTree.Node node = tree.get(index);
            if (node.thenBranch >= 0) {
                references[node.thenBranch]++;
            }
            if (node.elseBranch >= 0) {
                references[node.elseBranch]++;
            }
        }
    }

    String getSubtree(int nodeIndex) {
        BinaryDecisionTree.Node node = tree.get(nodeIndex);
        if (variableOrPort != null) {
            if (node.thenBranch == BinaryDecisionTree.NOT_REACHED_TERMINAL) {
                return getSubtreeBranch(node.elseBranch);
            }
            if (node.elseBranch == BinaryDecisionTree.NOT_REACHED_TERMINAL) {
                return getSubtreeBranch(node.thenBranch);
            }
        }
        return "(condition" + conditionPrefix + "_" + node.condition.suffixForVariables + " ? " +
                getSubtreeBranch(node.thenBranch) + " : " + getSubtreeBranch(node.elseBranch) + ")";
    }

    String getSubtreeBranch(int nodeIndex) {
        if (nodeIndex < 0) {
            if (variableOrPort == null) {
                if (nodeIndex == BinaryDecisionTree.NOT_REACHED_TERMINAL) {
                    return "0";
                }
                if (nodeIndex == BinaryDecisionTree.REACHED_TERMINAL) {
                    return "1";
                }
                throw new IllegalArgumentException("Unhandled case");
            }
            return Generator.getVersionTerminal(terminals[~nodeIndex], variableOrPort);
        }
        return references[nodeIndex] > 1 ? getNodeName(nodeIndex) : getSubtree(nodeIndex);
    }

    String getNodeName(int nodeIndex) {
        return namePrefix + "_" + nodeIndex + (variableOrPort != null ? "_" + variableOrPort.getName() : "");
    }
}
