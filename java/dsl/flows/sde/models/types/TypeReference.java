// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.types;

import dsl.flows.sde.SourceFilenameAndLocationRange;

import java.util.Comparator;

public class TypeReference {
    public final static Comparator<TypeReference> COMPARATOR_BY_NAME = Comparator.comparing(a -> a.name);

    public final SourceFilenameAndLocationRange locationRange;
    public final String name;
    public final Integer arraySize;
    public Type resolvedType;

    public TypeReference(SourceFilenameAndLocationRange locationRange, String name, Type resolvedType,
                         Integer arraySize) {
        this.locationRange = locationRange;
        this.name = name;
        this.resolvedType = resolvedType;
        this.arraySize = arraySize;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(name);
        if (arraySize != null) {
            result.append("[").append(arraySize).append("]");
        }
        return result.toString();
    }

    public String toSVString() {
        StringBuilder result = new StringBuilder();
        result.append(resolvedType instanceof BitType ? "bit" : name);
        if (arraySize != null) {
            result.append("[").append(arraySize - 1).append(":0]");
        }
        return result.toString();
    }

    public int totalBits() {
        return resolvedType.totalBits * (arraySize != null ? arraySize.intValue() : 1);
    }
}
