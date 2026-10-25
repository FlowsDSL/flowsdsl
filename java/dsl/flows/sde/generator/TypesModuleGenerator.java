// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.generator;

import dsl.flows.sde.models.CompilationElements;
import dsl.flows.sde.models.SystemArchitecture;
import dsl.flows.sde.models.types.OneOfType;
import dsl.flows.sde.models.types.StructType;
import dsl.flows.sde.models.types.Type;
import dsl.flows.sde.models.types.TypeReference;

import java.io.IOException;
import java.io.PrintStream;

public class TypesModuleGenerator {
    static void generateTypesModule(String outputDirectory, CompilationElements compilationElements,
                                    InterfaceDigest digest) throws IOException {
        try (PrintStream types = new PrintStream(outputDirectory + "/Types.sv")) {
            Generator.include(types, "verilog/TypesPreamble.sv");
            types.format("""
                            integer InterfaceDigestA = 32'h%02x%02x%02x%02x;
                            integer InterfaceDigestB = 32'h%02x%02x%02x%02x;
                            """, digest.value()[0] & 0x7f, digest.value()[1], digest.value()[2], digest.value()[3],
                    digest.value()[4] & 0x7f, digest.value()[5], digest.value()[6], digest.value()[7]);
            Generator.enumerateErrorCodes(true, (name, value) -> {
                types.println("integer Error" + name + " = {" + (SystemArchitecture.TO_HOST_MESSAGE_WORD_WIDTH -
                        compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth()) + "'d" +
                        compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + ", " +
                        compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth() + "'d" + value +
                        "};");
            }, compilationElements);
            types.println("integer BlankValue = {" + (SystemArchitecture.TO_HOST_MESSAGE_WORD_WIDTH -
                    compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth()) + "'d" +
                    compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + ", {" +
                    compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth() + "{1'b1}}};");
            for (String name : compilationElements.typeTopologicalOrder) {
                Type type = compilationElements.types.get(name);
                if (type.totalBits != 0) {
                    if (type instanceof StructType cast) {
                        types.println("typedef struct packed {");
                        for (StructType.Field field : cast.fields) {
                            if (field.type().resolvedType.totalBits != 0) {
                                types.println("  " + field.type().toSVString() + " " + field.name() + ";");
                            }
                        }
                        types.println("} " + name + ";");
                    }
                    if (type instanceof OneOfType cast) {
                        for (int index = 0; index < cast.options.length; index++) {
                            types.println(
                                    "integer " + name + "Option" + cast.options[index].name + " = " + index + ";");
                        }
                        types.println("typedef struct packed {");
                        types.println("  bit[" + (cast.optionBits - 1) + ":0] Option;");
                        if (cast.totalBits != cast.optionBits) {
                            types.println("  union packed {");
                            for (TypeReference option : cast.options) {
                                if (option.resolvedType.totalBits != 0) {
                                    int padding = cast.totalBits - cast.optionBits - option.resolvedType.totalBits;
                                    types.println("    struct packed { " + option.name + " Value; " +
                                            (padding != 0 ? "bit[" + (padding - 1) + ":0] Padding; " : "") + "} " +
                                            option.name + ";");
                                }
                            }
                            types.println("  } Value;");
                        }
                        types.println("} " + name + ";");
                    }
                }
            }
            types.println("endpackage");
        }
    }
}
