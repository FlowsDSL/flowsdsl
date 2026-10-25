// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

import dsl.flows.sde.models.types.*;

import java.util.TreeMap;

public class CompilationElements {
    public final StructType BLANK_TYPE = new StructType(new StructType.Field[0]);
    public final BitType BIT_TYPE = new BitType();
    public final TreeMap<String, Type> types = new TreeMap<>();
    public final TreeMap<String, Transform> transforms = new TreeMap<>();
    public final TreeMap<String, ConstantDeclaration> constants = new TreeMap<>();
    public final TreeMap<String, TreeMap<String, Boolean>> oneOfWrappers = new TreeMap<>();
    public final boolean includeTracing, includeRawEthernetFrames;
    public SystemArchitecture systemArchitecture;

    public String[] typeTopologicalOrder;

    public CompilationElements(boolean includeTracing, boolean includeRawEthernetFrames) {
        this.includeTracing = includeTracing;
        this.includeRawEthernetFrames = includeRawEthernetFrames;
        types.put("Blank", BLANK_TYPE);
        types.put("Bit", BIT_TYPE);
    }

    public void addOneOfWrapper(TypeReference oneOfTypeReference, TypeReference optionTypeReference) {
        OneOfType oneOfTypeResolved = (OneOfType) oneOfTypeReference.resolvedType;
        oneOfWrappers.computeIfAbsent(oneOfTypeReference.name, _ -> new TreeMap<>())
                .computeIfAbsent(optionTypeReference.name, _ -> oneOfTypeResolved.totalBits !=
                        optionTypeReference.resolvedType.totalBits + oneOfTypeResolved.optionBits);
    }
}
