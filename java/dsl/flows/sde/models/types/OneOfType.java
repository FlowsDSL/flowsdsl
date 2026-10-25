// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.types;

public class OneOfType extends Type {
    public final TypeReference[] options;
    public final byte optionBits;

    public OneOfType(TypeReference[] options) {
        this.options = options;
        this.optionBits = (byte) (32 - Integer.numberOfLeadingZeros(options.length - 1));
    }

    public boolean isOneOfOptions(Type type) {
        boolean found = false;
        for (TypeReference option : options) {
            if (option.resolvedType == type) {
                found = true;
                break;
            }
        }
        return found;
    }
}
