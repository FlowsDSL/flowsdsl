// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.Transform;
import dsl.flows.sde.models.expressions.ExpressionName;

public class StatementMacroPop extends StatementMacro {
    public enum Kind {
        Asserting, Blocking
    }

    public final Kind kind;
    public final String portGroupName;
    public final ExpressionName what;
    public Transform.PortGroup portGroupResolved;

    public StatementMacroPop(SourceFilenameAndLocationRange locationRange, Kind kind, String portGroupName,
                             ExpressionName what, int id) {
        super(locationRange, null, id);
        this.kind = kind;
        this.portGroupName = portGroupName;
        this.what = what;
    }
}
