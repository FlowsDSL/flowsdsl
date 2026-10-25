// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.models.terminals.VersionTerminal;

import java.util.LinkedList;
import java.util.TreeMap;

class TerminalListBuilder {
    TreeMap<VersionTerminal, Integer> map = new TreeMap<>();
    LinkedList<VersionTerminal> list = new LinkedList<>();

    TerminalListBuilder() {
        list.add(null);
        list.add(null);
        list.add(null);
        list.add(null);
    }

    int add(VersionTerminal terminal) {
        return map.computeIfAbsent(terminal, terminalArgument -> {
            int result = ~list.size();
            list.add(terminalArgument);
            return result;
        });
    }
}
