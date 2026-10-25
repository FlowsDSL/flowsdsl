// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.models.statements.StatementVariableDeclaration;

record VariableWithScope(StatementVariableDeclaration variable, Scope scope) {
}
