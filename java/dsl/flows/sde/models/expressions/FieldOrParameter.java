// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.expressions;

import dsl.flows.sde.SourceFilenameAndLocationRange;

public record FieldOrParameter(SourceFilenameAndLocationRange keyLocationRange, Expression expression) {
}
