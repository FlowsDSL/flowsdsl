// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.terminals;

public class VersionTerminalBeginningOfCycle extends VersionTerminal {
    public static final VersionTerminalBeginningOfCycle INSTANCE = new VersionTerminalBeginningOfCycle();

    private VersionTerminalBeginningOfCycle() {
    }

    @Override
    public int compareTo(VersionTerminal other) {
        if (other instanceof VersionTerminalBeginningOfCycle) {
            return 0;
        }
        return getClass().getName().compareTo(other.getClass().getName());
    }
}
