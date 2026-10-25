// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.types.OneOfType;
import dsl.flows.sde.models.types.StructType;
import dsl.flows.sde.models.types.Type;
import dsl.flows.sde.models.types.TypeReference;

import java.util.LinkedList;
import java.util.TreeMap;

class TypeSorterAndSizer {
    final CompilationErrorList compilationErrorList;
    final LinkedList<String> result = new LinkedList<>();
    final TreeMap<String, VisitStatus> visitStatusMap = new TreeMap<>();

    TypeSorterAndSizer(CompilationErrorList compilationErrorList) {
        this.compilationErrorList = compilationErrorList;
    }

    void add(SourceFilenameAndLocationRange locationRange, String name, Type type) {
        VisitStatus visitStatus = visitStatusMap.get(name);
        if (visitStatus == VisitStatus.Visited) {
            return;
        }
        if (visitStatus == VisitStatus.Visiting) {
            compilationErrorList.addError("Type '" + name + "' has circular definition", locationRange);
        } else {
            visitStatusMap.put(name, VisitStatus.Visiting);
            switch (type) {
                case StructType cast -> {
                    int totalBits = 0;
                    for (StructType.Field field : cast.fields) {
                        TypeReference fieldTypeReference = field.type();
                        if (fieldTypeReference.resolvedType != null) {
                            add(fieldTypeReference.locationRange, fieldTypeReference.name,
                                    fieldTypeReference.resolvedType);
                            if (fieldTypeReference.resolvedType.totalBits != null) {
                                totalBits += fieldTypeReference.totalBits();
                            }
                        }
                    }
                    cast.totalBits = totalBits;
                    result.add(name);
                }
                case OneOfType cast -> {
                    int maxOptionTotalBits = 0;
                    for (TypeReference option : cast.options) {
                        if (option.resolvedType != null) {
                            add(option.locationRange, option.name, option.resolvedType);
                            if (option.resolvedType.totalBits != null) {
                                maxOptionTotalBits = Math.max(maxOptionTotalBits, option.totalBits());
                            }
                        }
                    }
                    cast.totalBits = cast.optionBits + maxOptionTotalBits;
                    result.add(name);
                }
                case null, default -> {
                }
            }
        }
        visitStatusMap.put(name, VisitStatus.Visited);
    }
}
