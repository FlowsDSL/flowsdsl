// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.types.BitType;
import dsl.flows.sde.models.types.TypeReference;

public class ConstantDeclaration implements NamedEntity {
    public final SourceFilenameAndLocationRange locationRange;
    public final long value;

    public ConstantDeclaration(SourceFilenameAndLocationRange locationRange, long value) {
        this.locationRange = locationRange;
        this.value = value;
    }

    @Override
    public TypeReference getTypeReference(BitType bitType) {
        return new TypeReference(null, "Bit", bitType, 64);
    }
}
