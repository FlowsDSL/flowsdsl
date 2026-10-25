// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.Transform;
import dsl.flows.sde.models.expressions.Expression;

public class StatementMacroWrite extends StatementMacro {
    public enum Kind {
        FixedLength, VariableLength
    }

    public final SourceFilenameAndLocationRange portGroupLocationRange;
    public final String portGroupName;
    public final Expression what;
    public final Kind kind;
    public Transform.PortGroup portGroupResolved;

    public StatementMacroWrite(SourceFilenameAndLocationRange locationRange,
                               SourceFilenameAndLocationRange portGroupLocationRange, String portGroupName,
                               Expression what, Kind kind, int id) {
        super(locationRange, null, id);
        this.portGroupLocationRange = portGroupLocationRange;
        this.portGroupName = portGroupName;
        this.what = what;
        this.kind = kind;
    }
}
