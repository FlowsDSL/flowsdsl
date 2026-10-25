// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.models.Transform;
import dsl.flows.sde.models.expressions.ExpressionConstant;
import dsl.flows.sde.models.statements.*;

import java.util.LinkedList;

public class SuccessorAndPredecessorListBuilder {
    final Transform transform;
    final CompilationErrorList compilationErrorList;

    public SuccessorAndPredecessorListBuilder(Transform transform, CompilationErrorList compilationErrorList) {
        this.transform = transform;
        this.compilationErrorList = compilationErrorList;
    }

    public Statement buildSuccessorAndPredecessorLists() {
        LinkedList<StatementNonbranching> unconditional = new LinkedList<>();
        unconditional.add(transform.entry);
        LinkedList<StatementIfWhile> thenList = new LinkedList<>();
        LinkedList<StatementIfWhile> elseList = new LinkedList<>();
        buildSuccessorAndPredecessorLists(transform.body, unconditional, thenList, elseList, null, null);
        for (Transform.PortGroup portGroup : transform.portGroups) {
            if (portGroup.portA != null && portGroup.portA.direction == Transform.Direction.Output) {
                transform.variablesOrPorts.add(portGroup.portA);
            }
            if (portGroup.portB != null && portGroup.portB.direction == Transform.Direction.Output) {
                transform.variablesOrPorts.add(portGroup.portB);
            }
        }
        Statement exitStatement;
        if (!unconditional.isEmpty()) {
            exitStatement = unconditional.getFirst();
        } else if (!thenList.isEmpty()) {
            exitStatement = thenList.getFirst();
        } else if (!elseList.isEmpty()) {
            exitStatement = elseList.getFirst();
        } else {
            exitStatement = null;
        }
        return exitStatement;
    }

    void buildSuccessorAndPredecessorLists(StatementBlock body,
                                           LinkedList<StatementNonbranching> unconditionalPredecessors,
                                           LinkedList<StatementIfWhile> thenPredecessors,
                                           LinkedList<StatementIfWhile> elsePredecessors,
                                           StatementIfWhile innermostWhileStatement,
                                           LinkedList<StatementNonbranching> innermostWhileElsePredecessors) {
        for (Statement statement : body.statements) {
            statement.unconditionalPredecessors = new LinkedList<>(unconditionalPredecessors);
            statement.thenPredecessors = new LinkedList<>(thenPredecessors);
            statement.elsePredecessors = new LinkedList<>(elsePredecessors);
            while (!unconditionalPredecessors.isEmpty()) {
                unconditionalPredecessors.removeFirst().unconditionalSuccessor = statement;
            }
            while (!thenPredecessors.isEmpty()) {
                thenPredecessors.removeFirst().thenSuccessor = statement;
            }
            while (!elsePredecessors.isEmpty()) {
                elsePredecessors.removeFirst().elseSuccessor = statement;
            }
            switch (statement) {
                case StatementIfWhile cast -> {
                    LinkedList<StatementIfWhile> subThenThenPredecessors = new LinkedList<>();
                    subThenThenPredecessors.add(cast);
                    LinkedList<StatementIfWhile> subThenElsePredecessors = new LinkedList<>();
                    LinkedList<StatementNonbranching> subElsePredecessors = new LinkedList<>();
                    buildSuccessorAndPredecessorLists(cast.thenBranch, unconditionalPredecessors,
                            subThenThenPredecessors, subThenElsePredecessors,
                            cast.kind == StatementIfWhile.Kind.While ? cast : innermostWhileStatement,
                            cast.kind == StatementIfWhile.Kind.While ? subElsePredecessors :
                                    innermostWhileElsePredecessors);
                    if (cast.kind == StatementIfWhile.Kind.While) {
                        if (cast.condition instanceof ExpressionConstant castExpression && castExpression.value == 1) {
                            cast.elseSuccessor = null;
                        } else {
                            elsePredecessors.add(cast);
                        }
                        statement.unconditionalPredecessors.addAll(unconditionalPredecessors);
                        while (!unconditionalPredecessors.isEmpty()) {
                            unconditionalPredecessors.removeFirst().unconditionalSuccessor = statement;
                        }
                        statement.thenPredecessors.addAll(subThenThenPredecessors);
                        while (!subThenThenPredecessors.isEmpty()) {
                            subThenThenPredecessors.removeFirst().thenSuccessor = statement;
                        }
                        statement.elsePredecessors.addAll(subThenElsePredecessors);
                        while (!subThenElsePredecessors.isEmpty()) {
                            subThenElsePredecessors.removeFirst().elseSuccessor = statement;
                        }
                    } else {
                        thenPredecessors.addAll(subThenThenPredecessors);
                        elsePredecessors.addAll(subThenElsePredecessors);
                        if (cast.elseBranch != null) {
                            LinkedList<StatementIfWhile> subElseElsePredecessors = new LinkedList<>();
                            subElseElsePredecessors.add(cast);
                            buildSuccessorAndPredecessorLists(cast.elseBranch, subElsePredecessors, thenPredecessors,
                                    subElseElsePredecessors, innermostWhileStatement, innermostWhileElsePredecessors);
                            elsePredecessors.addAll(subElseElsePredecessors);
                        } else {
                            elsePredecessors.add(cast);
                        }
                    }
                    unconditionalPredecessors.addAll(subElsePredecessors);
                    transform.ifWhiles.add(cast);
                }
                case StatementLoopControl cast -> {
                    if (innermostWhileElsePredecessors == null) {
                        compilationErrorList.addError("No enclosing while loop found", cast.locationRange);
                    } else {
                        if (cast.kind == StatementLoopControl.Kind.Break) {
                            innermostWhileElsePredecessors.add(cast);
                        } else if (cast.kind == StatementLoopControl.Kind.Continue) {
                            innermostWhileStatement.unconditionalPredecessors.add(cast);
                            cast.unconditionalSuccessor = innermostWhileStatement;
                        }
                    }
                }
                case StatementNonbranching cast -> {
                    if (statement instanceof StatementVariableDeclaration castVariable) {
                        transform.variablesOrPorts.add(castVariable);
                    }
                    if (statement instanceof StatementExpression castExpression) {
                        if (castExpression.resolvedAssignmentTarget instanceof StatementVariableDeclaration castVariable) {
                            castVariable.assignments.add(castExpression);
                        }
                        if (castExpression.resolvedAssignmentTarget instanceof Transform.Port castPort) {
                            castPort.assignments.add(castExpression);
                        }
                    }
                    if (statement instanceof StatementStep castStep) {
                        transform.regions.add(castStep);
                    }
                    unconditionalPredecessors.add(cast);
                }
                default -> {
                }
            }
        }
    }
}
