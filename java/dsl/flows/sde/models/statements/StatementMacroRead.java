// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.Transform;
import dsl.flows.sde.models.expressions.ExpressionName;

public class StatementMacroRead extends StatementMacro {
    public enum Kind {
        FixedLength, VariableLength
    }

    public final SourceFilenameAndLocationRange portGroupLocationRange;
    public final String portGroupName;
    public final ExpressionName what;
    public final ExpressionName complete;
    public final Kind kind;
    public Transform.PortGroup portGroupResolved;

    public StatementMacroRead(SourceFilenameAndLocationRange locationRange,
                              SourceFilenameAndLocationRange portGroupLocationRange, String portGroupName,
                              ExpressionName what, ExpressionName complete, Kind kind, int id) {
        super(locationRange, null, id);
        this.portGroupLocationRange = portGroupLocationRange;
        this.portGroupName = portGroupName;
        this.what = what;
        this.kind = kind;
        this.complete = complete;
    }
}
