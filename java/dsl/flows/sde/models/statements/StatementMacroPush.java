// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.Transform;
import dsl.flows.sde.models.expressions.Expression;

public class StatementMacroPush extends StatementMacro {
    public enum Kind {
        Asserting, Blocking
    }

    public final Kind kind;
    public final String portGroupName;
    public final Expression what;
    public Transform.PortGroup portGroupResolved;

    public StatementMacroPush(SourceFilenameAndLocationRange locationRange, Kind kind, String portGroupName,
                              Expression what, int id) {
        super(locationRange, null, id);
        this.portGroupName = portGroupName;
        this.what = what;
        this.kind = kind;
    }
}
