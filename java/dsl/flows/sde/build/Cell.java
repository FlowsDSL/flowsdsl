// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.build;

import java.util.ArrayList;

class Cell {
    final String type, name;
    Long truthTable;
    ArrayList<Cell> inputs = new ArrayList<>();
    Integer onPathInputIndex;

    Cell(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public boolean isRegister() {
        return type.equals("FDRE") || type.equals("FDSE");
    }
}
