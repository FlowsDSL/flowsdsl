// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.types;

public class StructType extends Type {
    public record Field(String name, TypeReference type) {
        public static final Field[] EMPTY_ARRAY = new Field[0];
    }

    public final Field[] fields;

    public StructType(Field[] fields) {
        this.fields = fields;
    }

    public Field findField(String name) {
        for (Field field : fields) {
            if (field.name.equals(name)) {
                return field;
            }
        }
        return null;
    }
}
