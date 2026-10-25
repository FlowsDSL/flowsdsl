// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

import dsl.flows.sde.models.terminals.VersionTerminal;

public record BinaryDecisionTreeAndVariableTerminals(BinaryDecisionTree tree, VersionTerminal[] terminals) {
}
