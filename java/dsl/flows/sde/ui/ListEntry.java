// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

public record ListEntry(String name, boolean valid) {
    @Override
    public String toString() {
        return name;
    }
}
