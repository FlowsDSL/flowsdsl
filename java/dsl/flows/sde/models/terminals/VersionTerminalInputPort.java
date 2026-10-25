// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.terminals;

public class VersionTerminalInputPort extends VersionTerminal {
    public static final VersionTerminalInputPort INSTANCE = new VersionTerminalInputPort();

    private VersionTerminalInputPort() {
    }

    @Override
    public int compareTo(VersionTerminal other) {
        return getClass().getName().compareTo(other.getClass().getName());
    }
}
