// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.generator;

import dsl.flows.sde.models.*;
import dsl.flows.sde.models.statements.*;
import dsl.flows.sde.models.terminals.*;
import dsl.flows.sde.resources.ResourceAnchor;

import java.io.*;
import java.util.Map;
import java.util.function.BiConsumer;

public final class Generator {
    public final String outputDirectory;
    public final CompilationElements compilationElements;

    public Generator(String outputDirectory, CompilationElements compilationElements) {
        this.outputDirectory = outputDirectory + (compilationElements.includeTracing ? "/withTrace" : "/withoutTrace");
        this.compilationElements = compilationElements;
    }

    public void generate() throws IOException, IllegalAccessException {
        new File(outputDirectory).mkdirs();
        TypesModuleGenerator.generateTypesModule(outputDirectory, compilationElements,
                MessageEncoders.generateMessageEncoders(compilationElements, outputDirectory));
        copy("tcl/Compile.tcl", outputDirectory + "/Compile.tcl");
        copy("verilog/Top.sv", outputDirectory + "/Top.sv");
        copy("verilog/FlashProgrammerBridge.sv", outputDirectory + "/FlashProgrammerBridge.sv");
        copy("verilog/BlockRam.sv", outputDirectory + "/BlockRam.sv");
        copy("verilog/ScramblerUnscrambler.sv", outputDirectory + "/ScramblerUnscrambler.sv");
        copy("verilog/CRC.sv", outputDirectory + "/CRC.sv");
        copy("verilog/Queue.sv", outputDirectory + "/Queue.sv");
        copy("verilog/EthernetPCSAndMAC.sv", outputDirectory + "/EthernetPCSAndMAC.sv");
        copy("verilog/CMSBridgeTransform.sv", outputDirectory + "/CMSBridgeTransform.sv");
        copy("xdc/Top.xdc", outputDirectory + "/Top.xdc");
        CoreModuleGenerator.generateCoreModule(outputDirectory, compilationElements);
        for (Map.Entry<String, Transform> entry : compilationElements.transforms.entrySet()) {
            if ((entry.getValue().body != null && entry.getValue().isInstantiated) ||
                    entry.getValue() == compilationElements.systemArchitecture.hostMessageRouter.resolvedTransform) {
                TransformGenerator.generateTransform(outputDirectory, compilationElements, entry.getKey(),
                        entry.getValue());
            }
        }
    }

    static void include(PrintStream output, String filename) throws IOException {
        try (InputStream is = ResourceAnchor.class.getResourceAsStream(filename)) {
            output.write(is.readAllBytes());
        }
    }

    static void copy(String resourceName, String outputFileName) throws IOException {
        try (PrintStream output = new PrintStream(outputFileName)) {
            include(output, resourceName);
        }
    }

    public static String getAllFiles(CompilationElements architecture) {
        StringBuilder allSourceFiles = new StringBuilder(
                "Top.sv Types.sv EthernetPCSAndMAC.sv CRC.sv ScramblerUnscrambler.sv FlashProgrammerBridge.sv Core.sv BlockRam.sv HostMessageRouterTransform.sv CMSBridgeTransform.sv Queue.sv");
        for (Map.Entry<String, Transform> entry : architecture.transforms.entrySet()) {
            if (entry.getValue().body != null && entry.getValue().isInstantiated) {
                allSourceFiles.append(' ').append(entry.getKey()).append(".sv");
            }
        }
        return allSourceFiles.toString();
    }

    static int enumerateErrorCodes(boolean includeBasicErrors, BiConsumer<String, Integer> consumer,
                                   CompilationElements compilationElements) {
        int counter = 0;
        for (ErrorCode value : ErrorCode.values()) {
            if (includeBasicErrors) {
                consumer.accept(value.name(), counter);
            }
            counter++;
        }
        for (String innerEntry : compilationElements.systemArchitecture.instances.keySet()) {
            consumer.accept(innerEntry, counter++);
        }
        for (Connection connection : compilationElements.systemArchitecture.connections) {
            if (connection.kind == Connection.Kind.Queue) {
                consumer.accept(connection.getFullName(), counter++);
            }
        }
        return counter;
    }

    static String getVersionTerminal(VersionTerminal terminal, VariableOrPort variableOrPort) {
        if (terminal instanceof VersionTerminalInputPort) {
            return ((Transform.Port) variableOrPort).name;
        } else if (terminal instanceof VersionTerminalBeginningOfCycle) {
            if (variableOrPort instanceof StatementVariableDeclaration) {
                return "Current." + variableOrPort.getName();
            }
            if (variableOrPort instanceof Transform.Port) {
                return "0";
            }
        } else if (terminal instanceof VersionTerminalMerged cast) {
            return "merged" + cast.region.suffixForVariables + "_" + cast.statement.suffixForVariables + "_" +
                    variableOrPort.getName();
        } else if (terminal instanceof VersionTerminalAssigned cast) {
            return "assigned" + cast.region.suffixForVariables + "_" + cast.statement.suffixForVariables + "_" +
                    variableOrPort.getName();
        }
        throw new RuntimeException("Unhandled case");
    }
}
