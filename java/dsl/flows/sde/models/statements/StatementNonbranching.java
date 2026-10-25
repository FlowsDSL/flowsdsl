// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;

public class StatementNonbranching extends Statement {
    public Statement unconditionalSuccessor;

    protected StatementNonbranching(SourceFilenameAndLocationRange locationRange, String syntheticVariableSuffix,
                                    int id) {
        super(locationRange, syntheticVariableSuffix, id);
    }
}
