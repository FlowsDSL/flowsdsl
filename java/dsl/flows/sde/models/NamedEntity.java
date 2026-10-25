// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

import dsl.flows.sde.models.types.BitType;
import dsl.flows.sde.models.types.TypeReference;

public interface NamedEntity {
    TypeReference getTypeReference(BitType bitType);
}
