// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.generator;

import dsl.flows.sde.models.CompilationElements;
import dsl.flows.sde.models.Connection;
import dsl.flows.sde.models.Instance;
import dsl.flows.sde.models.types.*;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.TreeMap;

public class MessageEncoders {
    static InterfaceDigest generateMessageEncoders(CompilationElements compilationElements, String outputDirectory)
            throws IOException {
        StringBuilder headerBuilderA = new StringBuilder();
        for (String name : compilationElements.typeTopologicalOrder) {
            Type type = compilationElements.types.get(name);
            if (type instanceof StructType cast) {
                headerBuilderA.append("struct ").append(name).append(" {\n");
                for (StructType.Field field : cast.fields) {
                    if (field.type().arraySize != null) {
                        if (field.type().resolvedType instanceof BitType) {
                            if (field.type().arraySize <= 8) {
                                headerBuilderA.append("uint8_t");
                            } else if (field.type().arraySize <= 16) {
                                headerBuilderA.append("uint16_t");
                            } else if (field.type().arraySize <= 32) {
                                headerBuilderA.append("uint32_t");
                            } else {
                                headerBuilderA.append("std::array<uint8_t, ").append((field.type().arraySize + 7) >> 3)
                                        .append(">");
                            }
                        } else {
                            headerBuilderA.append("std::array<").append(field.type().name).append(", ")
                                    .append(field.type().arraySize).append(">");
                        }
                    } else {
                        if (field.type().resolvedType instanceof BitType) {
                            headerBuilderA.append("bool");
                        } else {
                            headerBuilderA.append(field.type().name);
                        }
                    }
                    headerBuilderA.append(" ").append(field.name()).append(";\n");
                }
                headerBuilderA.append("};\n");
            }
            if (type instanceof OneOfType cast) {
                headerBuilderA.append("using ").append(name).append(" = std::variant<");
                String separator = "";
                for (TypeReference option : cast.options) {
                    headerBuilderA.append(separator).append(option.name);
                    separator = ",";
                }
                headerBuilderA.append(">;\n");
            }
        }
        headerBuilderA.append("class FpgaIO {\n");
        if (compilationElements.includeTracing) {
            headerBuilderA.append("""
                        struct Tracing {
                            TaskQueue* taskQueue;
                            int dataFileDescriptor;
                    
                            explicit Tracing(const std::string& directory, TaskQueue* taskQueue);
                        };
                    """);
        }
        headerBuilderA.append("public:\n    enum class Error {\n");
        int totalErrorCodes;
        {
            String[] separator = new String[]{""};
            totalErrorCodes = Generator.enumerateErrorCodes(true, (name, value) -> {
                headerBuilderA.append(separator[0]).append("        ").append(name).append(" = ").append(value);
                separator[0] = ",\n";
            }, compilationElements);
        }
        headerBuilderA.append("""
                 };
                    struct Listener {
                        virtual ~Listener() = default;
                        virtual void HandleError(Error code) = 0;
                """);
        Instance hostMessageRouter = compilationElements.systemArchitecture.hostMessageRouter;
        for (Connection connection : hostMessageRouter.connectionsWithInstanceAsDestination) {
            headerBuilderA.append("        virtual void ReceiveMessageFrom").append(connection.source.instanceName)
                    .append("(const ").append(connection.fromCoreNonBlankType.name).append("&) = 0;\n");
        }
        headerBuilderA.append("    };\n");
        StringBuilder implementationBuilderA = new StringBuilder(
                "FpgaIO::FpgaIO(const std::string& deviceName, Listener& listener, const std::string& tracingPath, TaskQueue* taskQueue) : listener(listener)");
        if (compilationElements.includeTracing) {
            implementationBuilderA.append(", tracing(tracingPath, taskQueue)");
        }
        implementationBuilderA.append(" {\n");
        StringBuilder implementationBuilderB = new StringBuilder(), implementationBuilderC = new StringBuilder(
                "#include \"fpga.h\"\n");
        implementationBuilderB.append("static const char* ERROR_DESCRIPTION[] = {");
        {
            String[] separator = new String[]{""};
            Generator.enumerateErrorCodes(true, (name, _) -> {
                implementationBuilderB.append(separator[0]).append("\"").append(name).append("\"");
                separator[0] = ",";
            }, compilationElements);
        }
        implementationBuilderB.append("""
                };
                const char* FpgaIO::GetErrorDescription(FpgaIO::Error error) {
                    return ERROR_DESCRIPTION[static_cast<size_t>(error)];
                }
                """);
        StringBuilder headerBuilderB = new StringBuilder(
                "    static constexpr uint8_t FROM_CORE_CHANNEL_WIDTH = ").append(
                        compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth())
                .append(";\n    std::vector<uint32_t> tracingStateUpdates;\n");
        if (!hostMessageRouter.connectionsWithInstanceAsDestination.isEmpty()) {
            headerBuilderB.append("    uint32_t fromCoreMessageDecodeBuffer[")
                    .append(hostMessageRouter.connectionsWithInstanceAsDestination.getLast().fromCoreChannelRange.end() -
                            hostMessageRouter.connectionsWithInstanceAsDestination.getFirst().fromCoreChannelRange.start())
                    .append("];");
        }
        if (compilationElements.includeTracing) {
            headerBuilderB.append("    Tracing tracing;\n");
        }
        for (String name : compilationElements.typeTopologicalOrder) {
            headerBuilderB.append("    void serializeToHardware(const ").append(name).append("& value);\n");
            headerBuilderB.append("    ").append(name).append(" deserialize").append(name).append("FromHardware();\n");
        }
        ArrayList<TypeReference> types = new ArrayList<>();
        TreeMap<String, Integer> tracingSchemaIndex = new TreeMap<>();
        if (compilationElements.includeTracing) {
            collectTraceTypes(types,
                    compilationElements.systemArchitecture.fromCoreChannelLayout.tracingTypeReference(),
                    tracingSchemaIndex);
        }
        for (Connection connection : hostMessageRouter.connectionsWithInstanceAsDestination) {
            collectTraceTypes(types, connection.fromCoreNonBlankType, tracingSchemaIndex);
        }
        if (compilationElements.includeTracing) {
            implementationBuilderC.append("""
                    namespace fpga {
                    FpgaIO::Tracing::Tracing(const std::string& directory, TaskQueue* taskQueue) : taskQueue(taskQueue) {
                        FileSerializerOutput layoutOutput(directory + "/Layout");
                        Serializer layoutSerializer(layoutOutput);
                    """);
            implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                    .append(compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth()).append(");\n");
            implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                    .append(compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels())
                    .append(");\n");
            implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                    .append(compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels())
                    .append(");\n");
            implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(").append(types.size())
                    .append(");\n");
            for (TypeReference type : types) {
                implementationBuilderC.append("    layoutSerializer.serializeStringBody(\"").append(type.name)
                        .append("\");\n");
                if (type.resolvedType instanceof BitType) {
                    implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(0);\n");
                }
                if (type.resolvedType instanceof OneOfType castOneOfType) {
                    implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(1);\n");
                    implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                            .append(castOneOfType.options.length).append(");\n");
                    for (TypeReference typeReference : castOneOfType.options) {
                        implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                                .append(tracingSchemaIndex.get(typeReference.name)).append(");\n");
                    }
                }
                if (type.resolvedType instanceof StructType castStruct) {
                    implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(2);\n");
                    implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                            .append(castStruct.fields.length).append(");\n");
                    for (StructType.Field field : castStruct.fields) {
                        implementationBuilderC.append("    layoutSerializer.serializeStringBody(\"")
                                .append(field.name()).append("\");\n");
                        implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                                .append(tracingSchemaIndex.get(field.type().name)).append(");\n");
                        implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                                .append(field.type().arraySize != null ? field.type().arraySize : -1).append(");\n");
                    }
                }
            }
            implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                    .append(hostMessageRouter.connectionsWithInstanceAsDestination.size()).append(");\n");
            for (Connection connection : hostMessageRouter.connectionsWithInstanceAsDestination) {
                implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                        .append(tracingSchemaIndex.get(connection.fromCoreNonBlankType.name)).append(");\n");
                implementationBuilderC.append("    layoutSerializer.serializeIntegerBody(")
                        .append(connection.fromCoreChannelRange.end()).append(");\n");
            }
            implementationBuilderC.append("""
                        dataFileDescriptor = openForWritingAndTruncateWithException(directory + "/Data");
                    }
                    """);
        }
        for (Connection connection : hostMessageRouter.connectionsWithInstanceAsSource) {
            collectTraceTypes(types, connection.toCoreNonBlankType, tracingSchemaIndex);
        }
        headerBuilderB.append("""
                public:
                    FpgaIO(const std::string& deviceName, Listener& listener, const std::string& tracingPath, TaskQueue* taskQueue);
                    void Poll();
                    uint32_t GetReaderPointer() const;
                    void SendToHostMessageRouter(const StartTracing& message);
                """);
        for (Connection connection : hostMessageRouter.connectionsWithInstanceAsSource) {
            headerBuilderB.append("    void SendTo").append(connection.destination.instanceName).append("(const ")
                    .append(connection.toCoreNonBlankType.name).append("&);\n");
        }
        headerBuilderB.append("""
                    static const char* GetErrorDescription(Error error);
                };
                }
                """);
        String errorCodeMask = Integer.toHexString(
                (1 << compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth()) - 1);
        implementationBuilderB.append("""
                        void FpgaIO::Poll() {
                            std::chrono::system_clock::time_point now = std::chrono::system_clock::now();
                            uint32_t readerPointer = GetReaderPointer();
                            if (now >= ReportReceiverField.nextLimitUpdateByTime || ((readerPointer-ReportReceiverField.nextLimitUpdateByPointer) & 0x100000) == 0) {
                                CommandSenderField.queueStart[0x202] = readerPointer+0xfff00;
                                ReportReceiverField.nextLimitUpdateByTime = now + std::chrono::milliseconds(1);
                                ReportReceiverField.nextLimitUpdateByPointer = readerPointer + 0x10000;
                            }
                            if (now >= nextCMSUpdateByTime) {
                                SendToCMSBridge(CMSReadRequest{.Address = 0x28100});
                                nextCMSUpdateByTime = now + std::chrono::seconds(1);
                            }
                            for (uint8_t limit = 0; limit != 0x40; limit++) {
                                uint32_t word = *ReportReceiverField.queueReader;
                                if ((word >> 31) != ReportReceiverField.topBit) {
                                    return;
                                }
                                word &= 0x7fffffff;
                                ReportReceiverField.queueReader++;
                                if (ReportReceiverField.queueReader == ReportReceiverField.queueLimit) {
                                    ReportReceiverField.queueReader = ReportReceiverField.queueStart;
                                    ReportReceiverField.topBit ^= 1;
                                }
                        """).append("        uint32_t channelIndex = word >> ")
                .append(compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth()).append(";\n")
                .append("        if (channelIndex >= ")
                .append(compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 2)
                .append(") {\n")
                .append("            throw ApplicationException(\"Received invalid chunk index (\" + std::to_string(channelIndex) + \")\");\n")
                .append("        }\n").append("        if (channelIndex == ")
                .append(compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels()).append(") {\n")
                .append("            uint32_t errorCode = word & 0x").append(errorCodeMask).append(";\n")
                .append("            if (errorCode != 0x").append(errorCodeMask).append(") {\n")
                .append("                if (errorCode >= ").append(totalErrorCodes).append(") {\n")
                .append("                    throw ApplicationException(\"Received invalid error code (\" + std::to_string(errorCode) + \")\");\n")
                .append("                }\n")
                .append("                listener.HandleError(static_cast<Error>(errorCode));\n")
                .append("                tracingStateUpdates.push_back(word);\n").append("            }\n")
                .append("            continue;\n").append("        }\n")
                .append("        tracingStateUpdates.push_back(word);\n").append("        if (channelIndex == ")
                .append(compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 1)
                .append(") {\n");
        if (compilationElements.includeTracing) {
            implementationBuilderB.append(
                    "            tracing.taskQueue->add([tracing = &tracing, tracingStateUpdates = std::move(tracingStateUpdates)] { writeWithException(tracing->dataFileDescriptor, (uint8_t*) tracingStateUpdates.data(), tracingStateUpdates.size()*sizeof(tracingStateUpdates[0])); });\n");
        }
        implementationBuilderB.append("            tracingStateUpdates = {};\n").append("            continue;\n")
                .append("        }\n").append("        if (channelIndex >= ")
                .append(compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels()).append(") {\n")
                .append("            fromCoreMessageDecodeBuffer[")
                .append(compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() - 1)
                .append("-channelIndex] = word;\n").append("            switch (channelIndex) {\n");
        for (Connection connection : hostMessageRouter.connectionsWithInstanceAsDestination) {
            implementationBuilderB.append("              case ").append(connection.fromCoreChannelRange.end() - 1)
                    .append(": { ReportReceiverField.decoderBuffer = fromCoreMessageDecodeBuffer+")
                    .append(compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() -
                            connection.fromCoreChannelRange.end()).append("; ReportReceiverField.bitPosition = ")
                    .append(compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth())
                    .append("; listener.ReceiveMessageFrom").append(connection.source.instanceName)
                    .append("(deserialize").append(connection.fromCoreNonBlankType.name)
                    .append("FromHardware()); break; }\n");
        }
        implementationBuilderB.append("""
                            }
                            continue;
                        }
                    }
                }
                """);
        for (String name : compilationElements.typeTopologicalOrder) {
            Type type = compilationElements.types.get(name);
            implementationBuilderB.append("void FpgaIO::serializeToHardware(const ").append(name)
                    .append("& value) {\n");
            if (type instanceof OneOfType cast) {
                implementationBuilderB.append("  size_t index = value.index();\n");
                implementationBuilderB.append("  CommandSenderField.write(index, ").append(cast.optionBits)
                        .append(");\n");
                implementationBuilderB.append("  switch(index) {\n");
                for (int index = 0; index < cast.options.length; index++) {
                    implementationBuilderB.append("    case ").append(index).append(": serializeToHardware(std::get<")
                            .append(index).append(">(value)); ");
                    int padding = cast.totalBits - cast.optionBits - cast.options[index].resolvedType.totalBits;
                    if (padding != 0) {
                        implementationBuilderB.append("CommandSenderField.pad(").append(padding).append("); ");
                    }
                    implementationBuilderB.append("break;\n");
                }
                implementationBuilderB.append(
                        "    default: throw FpgaException(\"Invalid option contained in std::variant\");\n");
                implementationBuilderB.append("  }\n");
            }
            if (type instanceof StructType castStruct) {
                for (StructType.Field field : castStruct.fields) {
                    if (field.type().arraySize != null) {
                        if (field.type().resolvedType instanceof BitType) {
                            if (field.type().arraySize <= 32) {
                                implementationBuilderB.append("  CommandSenderField.write(value.").append(field.name())
                                        .append(", ").append(field.type().arraySize).append(");\n");
                            } else {
                                int remainder = field.type().arraySize & 7;
                                if (remainder != 0) {
                                    implementationBuilderB.append("  CommandSenderField.write(value.")
                                            .append(field.name()).append("[").append(field.type().arraySize >> 3)
                                            .append("], ").append(remainder).append(");\n");
                                }
                                implementationBuilderB.append("  {\n    size_t index = ")
                                        .append(field.type().arraySize >> 3)
                                        .append(";\n    while (index != 0) {\n      --index;\n      CommandSenderField.write(value.")
                                        .append(field.name()).append("[index], 8);\n    }\n  }\n");
                            }
                        } else {
                            implementationBuilderB.append("  {\n    size_t index = ").append(field.type().arraySize)
                                    .append("""
                                            ;
                                                while (index != 0) {
                                                  --index;
                                                  serializeToHardware(value.""").append(field.name())
                                    .append("[index]);\n    }\n  }\n");
                        }
                    } else {
                        if (field.type().resolvedType instanceof BitType) {
                            implementationBuilderB.append("  CommandSenderField.write(value.").append(field.name())
                                    .append(", 1);\n");
                        } else {
                            implementationBuilderB.append("  serializeToHardware(value.").append(field.name())
                                    .append(");\n");
                        }
                    }
                }
            }
            implementationBuilderB.append("}\n");
            implementationBuilderB.append(name).append(" FpgaIO::deserialize").append(name)
                    .append("FromHardware() {\n");
            if (type instanceof OneOfType cast) {
                implementationBuilderB.append("  size_t index = ReportReceiverField.read(").append(cast.optionBits)
                        .append(");\n");
                implementationBuilderB.append("  switch(index) {\n");
                for (int index = 0; index < cast.options.length; index++) {
                    implementationBuilderB.append("    case ").append(index).append(": { ")
                            .append(cast.options[index].name).append(" result{deserialize")
                            .append(cast.options[index].name).append("FromHardware()};");
                    int padding = cast.totalBits - cast.optionBits - cast.options[index].resolvedType.totalBits;
                    if (padding != 0) {
                        implementationBuilderB.append(" ReportReceiverField.skip(").append(padding).append("); ");
                    }
                    implementationBuilderB.append(" return result; }\n");
                }
                implementationBuilderB.append(
                        "    default: throw FpgaException(\"Invalid option in decode buffer\");\n");
                implementationBuilderB.append("  }\n");
            }
            if (type instanceof StructType castStruct) {
                implementationBuilderB.append("  ").append(name).append(" result;\n");
                for (StructType.Field field : castStruct.fields) {
                    if (field.type().arraySize != null) {
                        if (field.type().resolvedType instanceof BitType) {
                            if (field.type().arraySize <= 32) {
                                implementationBuilderB.append("  result.").append(field.name())
                                        .append(" = ReportReceiverField.read(").append(field.type().arraySize)
                                        .append(");\n");
                            } else {
                                int remainder = field.type().arraySize & 7;
                                if (remainder != 0) {
                                    implementationBuilderB.append("  result.").append(field.name()).append("[")
                                            .append(field.type().arraySize >> 3).append("] = ReportReceiverField.read(")
                                            .append(remainder).append(");\n");
                                }
                                implementationBuilderB.append("  {\n    size_t index = ")
                                        .append((field.type().arraySize + 7) >> 3)
                                        .append(";\n    while (index != 0) {\n      --index;\n      result.")
                                        .append(field.name())
                                        .append("[index] = ReportReceiverField.read(8);\n    }\n  }\n");
                            }
                        } else {
                            implementationBuilderB.append("  {\n    size_t index = ").append(field.type().arraySize)
                                    .append("""
                                            ;
                                                while (index != 0) {
                                                  --index;
                                                  result.""").append(field.name()).append("[index] = deserialize")
                                    .append(field.type().name).append("FromHardware();\n    }\n  }\n");
                        }
                    } else {
                        implementationBuilderB.append("  result.").append(field.name()).append(" = ");
                        if (field.type().resolvedType instanceof BitType) {
                            implementationBuilderB.append("ReportReceiverField.read(1) != 0");
                        } else {
                            implementationBuilderB.append("deserialize").append(field.type().name)
                                    .append("FromHardware()");
                        }
                        implementationBuilderB.append(";\n");
                    }
                }
                implementationBuilderB.append("  return result;\n");
            }
            implementationBuilderB.append("}\n");
        }
        if (compilationElements.includeTracing) {
            implementationBuilderC.append("}\n");
        }
        implementationBuilderB.append(
                        "void FpgaIO::SendToHostMessageRouter(const StartTracing& message) { CommandSenderField.write(0, ")
                .append(Math.max(compilationElements.systemArchitecture.toCoreDestinationBits, 1))
                .append("); CommandSenderField.flush(); }\n");
        for (Connection connection : hostMessageRouter.connectionsWithInstanceAsSource) {
            implementationBuilderB.append("void FpgaIO::SendTo").append(connection.destination.instanceName)
                    .append("(const ").append(connection.toCoreNonBlankType.name).append("& value) {\n");
            implementationBuilderB.append("  CommandSenderField.write(")
                    .append(connection.destination.resolvedInstance.toCoreDestinationIndex).append(", ")
                    .append(compilationElements.systemArchitecture.toCoreDestinationBits).append(");\n");
            implementationBuilderB.append("""
                      serializeToHardware(value);
                      CommandSenderField.flush();
                    }
                    """);
        }
        implementationBuilderB.append("}\n");
        String headerStringA = headerBuilderA.toString();
        String headerStringB = headerBuilderB.toString();
        String implementationStringA = implementationBuilderA.toString();
        String implementationStringB = implementationBuilderB.toString();
        String implementationStringC = implementationBuilderC.toString();
        InterfaceDigest result;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            addIntegerToDigest(digest, compilationElements.includeTracing ? 1 : 0);
            addIntegerToDigest(digest, types.size());
            for (TypeReference type : types) {
                addIntegerToDigest(digest, type.name.length());
                digest.update(type.name.getBytes(StandardCharsets.UTF_8));
                if (type.resolvedType instanceof BitType) {
                    addIntegerToDigest(digest, 0);
                }
                if (type.resolvedType instanceof OneOfType castOneOfType) {
                    addIntegerToDigest(digest, 1);
                    addIntegerToDigest(digest, castOneOfType.options.length);
                    for (TypeReference typeReference : castOneOfType.options) {
                        addIntegerToDigest(digest, tracingSchemaIndex.get(typeReference.name));
                    }
                }
                if (type.resolvedType instanceof StructType castStruct) {
                    addIntegerToDigest(digest, 2);
                    addIntegerToDigest(digest, castStruct.fields.length);
                    for (StructType.Field field : castStruct.fields) {
                        addIntegerToDigest(digest, field.name().length());
                        digest.update(field.name().getBytes(StandardCharsets.UTF_8));
                        addIntegerToDigest(digest, tracingSchemaIndex.get(field.type().name));
                        addIntegerToDigest(digest, field.type().arraySize != null ? field.type().arraySize : -1);
                    }
                }
            }
            addIntegerToDigest(digest, hostMessageRouter.connectionsWithInstanceAsSource.size());
            for (Connection connection : hostMessageRouter.connectionsWithInstanceAsSource) {
                addIntegerToDigest(digest, connection.destination.instanceName.length());
                digest.update(connection.destination.instanceName.getBytes(StandardCharsets.UTF_8));
                addIntegerToDigest(digest, tracingSchemaIndex.get(connection.toCoreNonBlankType.name));
            }
            addIntegerToDigest(digest, hostMessageRouter.connectionsWithInstanceAsDestination.size());
            for (Connection connection : hostMessageRouter.connectionsWithInstanceAsDestination) {
                addIntegerToDigest(digest, connection.source.instanceName.length());
                digest.update(connection.source.instanceName.getBytes(StandardCharsets.UTF_8));
                addIntegerToDigest(digest, tracingSchemaIndex.get(connection.fromCoreNonBlankType.name));
            }
            Generator.enumerateErrorCodes(false, (errorName, errorValue) -> {
                addIntegerToDigest(digest, errorName.length());
                digest.update(errorName.getBytes(StandardCharsets.UTF_8));
            }, compilationElements);
            result = new InterfaceDigest(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException(exception);
        }
        try (PrintStream headerStream = new PrintStream(outputDirectory + "/fpga.h")) {
            Generator.include(headerStream, "cpp/FpgaIOFragmentA.h");
            headerStream.print(headerStringA);
            Generator.include(headerStream, "cpp/FpgaIOFragmentB.h");
            headerStream.print(headerStringB);
        }
        try (PrintStream implementationStream = new PrintStream(outputDirectory + "/fpga.cpp")) {
            implementationStream.format("""
                            #include <fcntl.h>
                            #include <unistd.h>
                            #include <sys/mman.h>
                            #include <cstring>
                            #include <sys/file.h>
                            #include "foundation/Utility.h"
                            #include "fpga.h"
                            
                            namespace fpga {
                            
                            constexpr uint32_t InterfaceDigestA = 0x%02x%02x%02x%02x;
                            constexpr uint32_t InterfaceDigestB = 0x%02x%02x%02x%02x;
                            
                            """, result.value()[0] & 0x7f, result.value()[1], result.value()[2], result.value()[3],
                    result.value()[4] & 0x7f, result.value()[5], result.value()[6], result.value()[7]);
            implementationStream.print(implementationStringA);
            Generator.include(implementationStream, "cpp/FpgaIOFragment.cpp");
            implementationStream.print(implementationStringB);
        }
        try (PrintStream implementationStream = new PrintStream(outputDirectory + "/fpgaTracing.cpp")) {
            implementationStream.print(implementationStringC);
        }
        return result;
    }

    private static void collectTraceTypes(ArrayList<TypeReference> list, TypeReference type,
                                          TreeMap<String, Integer> tracingSchemaIndex) {
        if (tracingSchemaIndex.containsKey(type.name)) {
            return;
        }
        tracingSchemaIndex.put(type.name, list.size());
        list.add(type);
        if (type.resolvedType instanceof OneOfType castOneOf) {
            for (TypeReference option : castOneOf.options) {
                collectTraceTypes(list, option, tracingSchemaIndex);
            }
        }
        if (type.resolvedType instanceof StructType castStruct) {
            for (StructType.Field field : castStruct.fields) {
                collectTraceTypes(list, field.type(), tracingSchemaIndex);
            }
        }
    }

    private static void addIntegerToDigest(MessageDigest digest, int value) {
        digest.update(
                new byte[]{(byte) (value & 0xff), (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)});
    }
}
