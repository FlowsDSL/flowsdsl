// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

import dsl.flows.sde.models.statements.StatementIfWhile;

import java.util.ArrayList;
import java.util.Arrays;

public class BinaryDecisionTree {
    public final static class Node {
        public final StatementIfWhile condition;
        public final int thenBranch, elseBranch;

        public Node(StatementIfWhile condition, int thenBranch, int elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }
    }

    public static final int NOT_REACHED_TERMINAL = -3;
    public static final int REACHED_TERMINAL = -4;

    public static final BinaryDecisionTree NOT_REACHED = new BinaryDecisionTree(new Node[0], NOT_REACHED_TERMINAL);
    public static final BinaryDecisionTree REACHED = new BinaryDecisionTree(new Node[0], REACHED_TERMINAL);

    private final Node[] nodes;
    private final int root;

    public BinaryDecisionTree(Node[] nodes, int root) {
        this.nodes = nodes;
        this.root = root;
    }

    public int getRoot() {
        return root;
    }

    public Node get(int index) {
        return nodes[index];
    }

    public static BinaryDecisionTree merge(BinaryDecisionTree a, BinaryDecisionTree b) {
        if (a.root == NOT_REACHED_TERMINAL) {
            return b;
        }
        if (b.root == NOT_REACHED_TERMINAL) {
            return a;
        }
        if (a.root < 0 || b.root < 0) {
            throw new IllegalArgumentException("Unhandled case");
        }
        ArrayList<Node> newNodes = new ArrayList<>();
        int[] aMapping = new int[a.nodes.length];
        int[] abMapping = new int[a.nodes.length];
        int[] bMapping = new int[b.nodes.length];
        Arrays.fill(aMapping, -1);
        Arrays.fill(bMapping, -1);
        int newRoot = mergeInto(a.nodes, a.root, aMapping, b.nodes, b.root, bMapping, abMapping, newNodes);
        return new BinaryDecisionTree(newNodes.toArray(new Node[newNodes.size()]), newRoot);
    }

    private static int mergeInto(Node[] aNodes, int aIndex, int[] aMapping, Node[] bNodes, int bIndex, int[] bMapping,
                                 int[] abMapping, ArrayList<Node> newNodes) {
        if (aIndex == NOT_REACHED_TERMINAL) {
            if (bIndex < 0) {
                return bIndex;
            }
            if (bMapping[bIndex] == -1) {
                bMapping[bIndex] = newNodes.size();
                newNodes.add(null);
                Node bNode = bNodes[bIndex];
                int thenBranch = mergeInto(aNodes, aIndex, aMapping, bNodes, bNode.thenBranch, bMapping, abMapping,
                        newNodes);
                int elseBranch = mergeInto(aNodes, aIndex, aMapping, bNodes, bNode.elseBranch, bMapping, abMapping,
                        newNodes);
                newNodes.set(bMapping[bIndex], new Node(bNodes[bIndex].condition, thenBranch, elseBranch));
            }
            if (bMapping[bIndex] == -2) {
                throw new IllegalArgumentException("Unhandled case");
            }
            return bMapping[bIndex];
        }
        if (bIndex == NOT_REACHED_TERMINAL) {
            if (aIndex < 0) {
                return aIndex;
            }
            if (aMapping[aIndex] == -1) {
                aMapping[aIndex] = newNodes.size();
                newNodes.add(null);
                Node aNode = aNodes[aIndex];
                int thenBranch = mergeInto(aNodes, aNode.thenBranch, aMapping, bNodes, bIndex, bMapping, abMapping,
                        newNodes);
                int elseBranch = mergeInto(aNodes, aNode.elseBranch, aMapping, bNodes, bIndex, bMapping, abMapping,
                        newNodes);
                newNodes.set(aMapping[aIndex], new Node(aNodes[aIndex].condition, thenBranch, elseBranch));
            }
            return aMapping[aIndex];
        }
        if (aIndex < 0 || bIndex < 0) {
            throw new IllegalArgumentException("Unhandled case");
        }
        Node aNode = aNodes[aIndex];
        Node bNode = bNodes[bIndex];
        if (aNode.condition != bNode.condition) {
            throw new IllegalArgumentException("Unhandled case");
        }
        if (aMapping[aIndex] == -1) {
            if (bMapping[bIndex] != -1) {
                throw new IllegalArgumentException("Unhandled case");
            }
            int thenBranch = mergeInto(aNodes, aNode.thenBranch, aMapping, bNodes, bNode.thenBranch, bMapping,
                    abMapping, newNodes);
            int elseBranch = mergeInto(aNodes, aNode.elseBranch, aMapping, bNodes, bNode.elseBranch, bMapping,
                    abMapping, newNodes);
            abMapping[aIndex] = bIndex;
            bMapping[bIndex] = -2;
            if (thenBranch == elseBranch) {
                aMapping[aIndex] = thenBranch;
            } else {
                aMapping[aIndex] = newNodes.size();
                newNodes.add(aMapping[aIndex], new Node(aNode.condition, thenBranch, elseBranch));
            }
        } else if (abMapping[aIndex] != bIndex) {
            throw new IllegalArgumentException("Unhandled case");
        }
        return aMapping[aIndex];
    }

    public static BinaryDecisionTree replaceReachedWithTerminal(BinaryDecisionTree tree, int replacement) {
        if (tree.root == REACHED_TERMINAL) {
            return new BinaryDecisionTree(new Node[0], replacement);
        }
        Node[] newNodes = new Node[tree.nodes.length];
        for (int index = 0; index < tree.nodes.length; index++) {
            Node node = tree.nodes[index];
            newNodes[index] = new Node(node.condition,
                    node.thenBranch == REACHED_TERMINAL ? replacement : node.thenBranch,
                    node.elseBranch == REACHED_TERMINAL ? replacement : node.elseBranch);
        }
        return new BinaryDecisionTree(newNodes, tree.root);
    }

    public static BinaryDecisionTree replaceReachedWithCondition(BinaryDecisionTree tree, StatementIfWhile condition,
                                                                 int thenBranch, int elseBranch) {
        if (tree.root == REACHED_TERMINAL) {
            return new BinaryDecisionTree(new Node[]{new Node(condition, thenBranch, elseBranch)}, 0);
        }
        if (tree.root < 0) {
            return tree;
        }
        Node[] newNodes = new Node[tree.nodes.length + 1];
        for (int index = 0; index < tree.nodes.length; index++) {
            Node node = tree.nodes[index];
            newNodes[index] = new Node(node.condition,
                    node.thenBranch == REACHED_TERMINAL ? tree.nodes.length : node.thenBranch,
                    node.elseBranch == REACHED_TERMINAL ? tree.nodes.length : node.elseBranch);
        }
        newNodes[tree.nodes.length] = new Node(condition, thenBranch, elseBranch);
        return new BinaryDecisionTree(newNodes, tree.root);
    }

    public int getSize() {
        return nodes.length;
    }
}
