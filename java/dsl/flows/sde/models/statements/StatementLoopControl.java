// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;

public class StatementLoopControl extends StatementNonbranching {
    public enum Kind {Break, Continue}

    public final Kind kind;

    public StatementLoopControl(SourceFilenameAndLocationRange locationRange, Kind kind, String syntheticVariableSuffix,
                                int id) {
        super(locationRange, syntheticVariableSuffix, id);
        this.kind = kind;
    }
}
