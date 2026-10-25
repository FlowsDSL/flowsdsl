// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.terminals;

import dsl.flows.sde.models.statements.Statement;
import dsl.flows.sde.models.statements.StatementStep;

public final class VersionTerminalMerged extends VersionTerminal {
    public final StatementStep region;
    public final Statement statement;

    public VersionTerminalMerged(StatementStep region, Statement statement) {
        this.region = region;
        this.statement = statement;
    }

    @Override
    public int compareTo(VersionTerminal other) {
        if (other instanceof VersionTerminalMerged cast) {
            int result = region.getId() - cast.region.getId();
            if (result == 0) {
                result = statement.getId() - cast.statement.getId();
            }
            return result;
        }
        return getClass().getName().compareTo(other.getClass().getName());
    }
}
