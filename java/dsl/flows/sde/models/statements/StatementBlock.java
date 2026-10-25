// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;

import java.util.List;

public class StatementBlock {
    public final SourceFilenameAndLocationRange locationRange;
    public List<Statement> statements;

    public StatementBlock(SourceFilenameAndLocationRange locationRange, List<Statement> statements) {
        this.locationRange = locationRange;
        this.statements = statements;
    }
}
