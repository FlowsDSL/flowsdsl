// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.expressions.Expression;

public class StatementExpression extends StatementNonbranching {
    public final SourceFilenameAndLocationRange assignmentTargetLocationRange;
    public final String assignmentTargetName;
    public final Expression expression;
    public VariableOrPort resolvedAssignmentTarget;
    public boolean oneOfTypeWrapper;

    public StatementExpression(SourceFilenameAndLocationRange locationRange, Expression expression,
                               String syntheticVariableSuffix, int id) {
        super(locationRange, syntheticVariableSuffix, id);
        this.assignmentTargetLocationRange = null;
        this.assignmentTargetName = null;
        this.expression = expression;
    }

    public StatementExpression(SourceFilenameAndLocationRange locationRange,
                               SourceFilenameAndLocationRange assignmentTargetLocationRange,
                               String assignmentTargetName, Expression expression, String syntheticVariableSuffix,
                               int id) {
        super(locationRange, syntheticVariableSuffix, id);
        this.assignmentTargetLocationRange = assignmentTargetLocationRange;
        this.assignmentTargetName = assignmentTargetName;
        this.expression = expression;
    }

    public StatementExpression(SourceFilenameAndLocationRange locationRange, VariableOrPort resolvedAssignmentTarget,
                               Expression expression, String syntheticVariableSuffix, int id) {
        super(locationRange, syntheticVariableSuffix, id);
        this.assignmentTargetLocationRange = resolvedAssignmentTarget.getLocationRange();
        this.assignmentTargetName = resolvedAssignmentTarget.getName();
        this.expression = expression;
        this.resolvedAssignmentTarget = resolvedAssignmentTarget;
    }
}
