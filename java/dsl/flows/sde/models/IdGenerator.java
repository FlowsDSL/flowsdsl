// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

public class IdGenerator {
    private int counter = 0;

    public int generateId() {
        return counter++;
    }

    public int getCounter() {
        return counter;
    }
}
