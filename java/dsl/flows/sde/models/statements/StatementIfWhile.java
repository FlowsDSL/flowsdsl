// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.expressions.Expression;

public class StatementIfWhile extends Statement {
    public enum Kind {If, While}

    public final Kind kind;
    public final Expression condition;
    public final StatementBlock thenBranch;
    public final StatementBlock elseBranch;
    public Statement thenSuccessor;
    public Statement elseSuccessor;

    public StatementIfWhile(SourceFilenameAndLocationRange locationRange, Kind kind, Expression condition,
                            StatementBlock thenBranch, StatementBlock elseBranch, String syntheticVariableSuffix,
                            int id) {
        super(locationRange, syntheticVariableSuffix, id);
        this.kind = kind;
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }
}
