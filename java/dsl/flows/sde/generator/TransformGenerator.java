// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.generator;

import dsl.flows.sde.models.*;
import dsl.flows.sde.models.statements.*;
import dsl.flows.sde.models.types.TypeReference;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntFunction;

public class TransformGenerator {
    static final int MAX_CHUNKS_BITS = 4;
    static final int MAX_CHUNKS_PER_CYCLE = 1 << MAX_CHUNKS_BITS;

    static void generateTransform(String outputDirectory, CompilationElements compilationElements, String transformName,
                                  Transform transform) throws FileNotFoundException {
        try (PrintStream module = new PrintStream(outputDirectory + "/" + transformName + ".sv")) {
            module.println("`default_nettype none");
            module.println("import Types::*;");
            module.println("module " + transformName + " (");
            module.print("  input wire Clock, input wire SystemReset, input wire ErrorResetIn");
            if (transform == compilationElements.systemArchitecture.hostMessageRouter.resolvedTransform) {
                module.print("""
                        ,
                          input wire ToCoreMessageValid, input ToCoreWord ToCoreMessageWord,
                          output logic FromCoreMessageValid, output FromCoreWord FromCoreMessageWord,
                          input wire FromCoreQueueAlmostFull, output logic ErrorResetOut""");
                if (compilationElements.includeTracing) {
                    module.print(", input TraceWord Trace");
                }
                for (String instanceName : compilationElements.systemArchitecture.instances.keySet()) {
                    module.print(",\n  input bit " + instanceName + "Error");
                }
                for (Connection connection : compilationElements.systemArchitecture.connections) {
                    if (connection.kind == Connection.Kind.Queue) {
                        module.print(",\n  input bit " + connection.getFullName() + "Error");
                    }
                }
            } else if (compilationElements.includeTracing) {
                module.print(",\n  output " + transform.stateAndInputsTypeReference + " Trace");
            }
            for (Transform.PortGroup portGroup : transform.portGroups) {
                module.print(",\n  " + portGroup.portA.direction.name().toLowerCase() + " " +
                        portGroup.portA.typeReference.toSVString() + " " + portGroup.portA.name);
                if (portGroup.portB != null) {
                    module.print(",\n  " + portGroup.portB.direction.name().toLowerCase() + " " +
                            portGroup.portB.typeReference.toSVString() + " " + portGroup.portB.name);
                }
            }
            module.println(");");
            int fromHostMaxWords = 0;
            int channelGroupBits = 0;
            if (transform == compilationElements.systemArchitecture.hostMessageRouter.resolvedTransform) {
                int fromHostMessageMaxWidth = 0;
                for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsSource) {
                    fromHostMessageMaxWidth = Math.max(fromHostMessageMaxWidth,
                            connection.destination.resolvedPortGroup.typeReference.resolvedType.totalBits);
                }
                fromHostMaxWords =
                        (compilationElements.systemArchitecture.toCoreDestinationBits + fromHostMessageMaxWidth +
                                SystemArchitecture.FROM_HOST_MESSAGE_WORD_WIDTH - 1) /
                                SystemArchitecture.FROM_HOST_MESSAGE_WORD_WIDTH;
                module.println("typedef struct packed {");
                module.println(
                        "  bit[" + (compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth() - 1) +
                                ":0] Value;");
                module.println("} Chunk;");
                module.println("typedef struct packed {");
                int presenceInWidth = compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels() +
                        compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination.size();
                module.println("  bit[" + (presenceInWidth - 1) + ":0] Presence;");
                module.println("  Chunk CycleCounter;");
                module.println("  Chunk ErrorCode;");
                module.println("} PresenceCycleCounterAndErrorCode;");
                module.println("typedef struct packed {");
                if (fromHostMaxWords != 0) {
                    if (fromHostMaxWords > 1) {
                        module.println("  bit[" + (31 - Integer.numberOfLeadingZeros(fromHostMaxWords - 1)) +
                                ":0] ToCoreWord;");
                    }
                    module.println("  bit[" + (fromHostMaxWords * SystemArchitecture.FROM_HOST_MESSAGE_WORD_WIDTH - 1) +
                            ":0] ToCoreMessage;");
                }
                for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsSource) {
                    module.print("  " + connection.source.resolvedPortGroup.portA.typeReference.toSVString() + " " +
                            connection.source.resolvedPortGroup.portA.name + ";");
                }
                module.print("""
                          bit SendingSnapshot;
                          bit Recording;
                          bit StopRecording;
                          bit StopPending;
                          bit ErrorResetA;
                          bit ErrorResetB;
                          TraceAndFromCoreMessagesWord LastA;
                          TraceAndFromCoreMessagesWord LastB;
                          TraceAndFromCoreMessagesWord LastC;
                          PresenceCycleCounterAndErrorCode PresenceCycleCounterAndErrorCodeIn;
                        """);
                module.println(
                        "  bit[" + (compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 1) +
                                ":0] PresenceOut;");
                module.println("  Chunk[" +
                        (compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 1) +
                        ":0] ChunkOut;");
                module.print("""
                          bit FromCoreMessageValid;
                          FromCoreWord FromCoreMessageWord;
                        """);
                channelGroupBits = 32 - Integer.numberOfLeadingZeros(
                        (compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 1) /
                                MAX_CHUNKS_PER_CYCLE);
                if (channelGroupBits != 0) {
                    module.println("  bit[" + (channelGroupBits - 1) + ":0] ChannelGroup;");
                }
                module.println("""
                          } State;
                          State Current, Next, Initial;
                          always_ff @(posedge Clock)
                          begin
                            Current <= SystemReset ? Initial : Next;
                          end
                          PresenceCycleCounterAndErrorCode PresenceCycleCounterAndErrorCodeOut;
                          logic PresencePushValid;
                          assign PresencePushValid = (|Current.PresenceCycleCounterAndErrorCodeIn.Presence) | (&Current.PresenceCycleCounterAndErrorCodeIn.CycleCounter) | (~(&Current.PresenceCycleCounterAndErrorCodeIn.ErrorCode));
                          logic PresenceHasElement;
                          logic PresencePop;
                          logic PresenceReady;
                          Queue #(.ELEMENT_SIZE($bits(PresenceCycleCounterAndErrorCode))) PresenceCycleCounterAndErrorCodeQueue (.Clock(Clock), .Reset(SystemReset),
                            .PushElement(Current.PresenceCycleCounterAndErrorCodeIn), .PushValid(Current.Recording & PresencePushValid),
                            .Front(PresenceCycleCounterAndErrorCodeOut), .HasElement(PresenceHasElement), .Pop(PresencePop), .Ready(PresenceReady));
                        """);
                module.println("logic[" + (presenceInWidth - 1) + ":0] ChunkPop;");
                module.println(
                        "logic[" + (compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() - 1) +
                                ":0] ChunkReady;");
                module.println(
                        "Chunk[" + (compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() - 1) +
                                ":0] ChunkFront;");
                if (compilationElements.includeTracing) {
                    addPadding(compilationElements, module, "TraceChunkInA", 0,
                            compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels(),
                            "Current.LastA.Trace",
                            compilationElements.systemArchitecture.fromCoreChannelLayout.tracingTypeReference());
                    addPadding(compilationElements, module, "TraceChunkInB", 0,
                            compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels(),
                            "Current.LastB.Trace",
                            compilationElements.systemArchitecture.fromCoreChannelLayout.tracingTypeReference());
                    for (int index = 0;
                         index < compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels();
                         index++) {
                        module.println("Queue #(.ELEMENT_SIZE(" +
                                compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth() +
                                "), .ADDRESS_WIDTH(9)) Chunk" + index + "Queue (.Clock(Clock), .Reset(SystemReset), " +
                                ".PushElement(TraceChunkInA[" + index +
                                "]), .PushValid(Current.Recording && Current.PresenceCycleCounterAndErrorCodeIn.Presence[" +
                                index + "]), " + ".Front(ChunkFront[" + index + "]), .Pop(ChunkPop[" + index +
                                "]), .Ready(ChunkReady[" + index + "]));");
                    }
                }
                for (int connectionIndex = 0; connectionIndex <
                        compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination.size();
                     connectionIndex++) {
                    Connection connection = compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination.get(
                            connectionIndex);
                    String wireName = connection.source.instanceName + "ChunkIn";
                    addPadding(compilationElements, module, wireName, connection.fromCoreChannelRange.start(),
                            connection.fromCoreChannelRange.end(),
                            "Current.LastA.FromCoreMessages." + connection.source.instanceName + ".Value",
                            connection.fromCoreNonBlankType);
                    for (int index = connection.fromCoreChannelRange.start();
                         index < connection.fromCoreChannelRange.end(); index++) {
                        int presenceInIndex =
                                compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels() +
                                        connectionIndex;
                        module.println("Queue #(.ELEMENT_SIZE(" +
                                compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth() +
                                "), .ADDRESS_WIDTH(9)) Chunk" + index +
                                "Queue (.Clock(Clock), .Reset(SystemReset), .PushElement(" + wireName + "[" + index +
                                "])," +
                                ".PushValid(Current.Recording && Current.PresenceCycleCounterAndErrorCodeIn.Presence[" +
                                presenceInIndex + "]), .Front(ChunkFront[" + index + "]), " + ".Pop(ChunkPop[" +
                                presenceInIndex + "]), .Ready(ChunkReady[" + index + "]));");
                    }
                }
            } else {
                module.println(transformName + "State Current, Next, Initial;");
                module.print("""
                        always_ff @(posedge Clock)
                        begin
                          Current <= (SystemReset | ErrorResetIn) ? Initial : Next;
                        end
                        """);
                if (compilationElements.includeTracing) {
                    module.println("assign Trace.State = Current;");
                    for (Transform.PortGroup portGroup : transform.portGroups) {
                        if (portGroup.portA != null && portGroup.portA.direction == Transform.Direction.Input) {
                            module.println(
                                    "assign Trace.Inputs." + portGroup.portA.name + " = " + portGroup.portA.name + ";");
                        }
                        if (portGroup.portB != null && portGroup.portB.direction == Transform.Direction.Input) {
                            module.println(
                                    "assign Trace.Inputs." + portGroup.portB.name + " = " + portGroup.portB.name + ";");
                        }
                    }
                }
            }
            for (StatementIfWhile statement : transform.ifWhiles) {
                for (StatementStep region : statement.regionAttributes.keySet()) {
                    module.println(
                            "logic condition" + region.suffixForVariables + "_" + statement.suffixForVariables + ";");
                }
            }
            for (StatementStep region : transform.regions) {
                for (Statement mergePoint : region.mergePoints) {
                    for (VariableOrPort variableOrPort : mergePoint.regionAttributes.get(
                            region).mergedValueByVariableOrPort.keySet()) {
                        module.println(
                                variableOrPort.getTypeReference(compilationElements.BIT_TYPE).toSVString() + " merged" +
                                        region.suffixForVariables + "_" + mergePoint.suffixForVariables + "_" +
                                        variableOrPort.getName() + ";");
                    }
                }
            }
            for (VariableOrPort variableOrPort : transform.variablesOrPorts) {
                String type = variableOrPort.getTypeReference(compilationElements.BIT_TYPE).toSVString();
                for (StatementExpression assignment : variableOrPort.getAssignments()) {
                    for (StatementStep region : assignment.regionAttributes.keySet()) {
                        module.println(
                                type + " assigned" + region.suffixForVariables + "_" + assignment.suffixForVariables +
                                        "_" + variableOrPort.getName() + ";");
                    }
                }
            }
            for (Map.Entry<String, TreeMap<String, Boolean>> entry : compilationElements.oneOfWrappers.entrySet()) {
                for (Map.Entry<String, Boolean> subEntry : entry.getValue().entrySet()) {
                    String wrappedType = subEntry.getKey();
                    String functionName = "OneOfWrapper" + entry.getKey() + "Option" + wrappedType;
                    module.println("function automatic " + entry.getKey() + " " + functionName + "(" + wrappedType +
                            " Wrapped);");
                    module.println("  " + functionName + ".Option = " + entry.getKey() + "Option" + wrappedType + ";");
                    module.println("  " + functionName + ".Value." + wrappedType + ".Value = Wrapped;");
                    if (subEntry.getValue()) {
                        module.println(
                                "  " + functionName + ".Value." + wrappedType + ".Padding." + wrappedType + " = 0;");
                    }
                    module.println("endfunction");
                }
            }
            for (StatementStep region : transform.regions) {
                for (Statement mergePoint : region.mergePoints) {
                    for (Map.Entry<VariableOrPort, BinaryDecisionTreeAndVariableTerminals> entry : mergePoint.regionAttributes.get(
                            region).mergedValueByVariableOrPort.entrySet()) {
                        VariableOrPort variableOrPort = entry.getKey();
                        String decisionTreeRoot = printBinaryDecisionTree(module, region,
                                "merged" + region.suffixForVariables + "_" + mergePoint.suffixForVariables,
                                variableOrPort, entry.getValue(), compilationElements);
                        module.println(
                                "assign merged" + region.suffixForVariables + "_" + mergePoint.suffixForVariables +
                                        "_" + variableOrPort.getName() + " = " + decisionTreeRoot + ";");
                    }
                }
            }
            TreeMap<VariableOrPort, String> mergedNextValues = new TreeMap<>(VariableOrPort.COMPARATOR);
            TreeSet<String> emittedExpressions = new TreeSet<>();
            for (VariableOrPort variableOrPort : transform.variablesOrPorts) {
                for (StatementExpression assignment : variableOrPort.getAssignments()) {
                    for (StatementStep region : assignment.regionAttributes.keySet()) {
                        ExpressionPrinter expressionPrinter = new ExpressionPrinter(module, region, assignment,
                                emittedExpressions);
                        expressionPrinter.output.append("assign assigned").append(region.suffixForVariables).append('_')
                                .append(assignment.suffixForVariables).append("_").append(variableOrPort.getName())
                                .append(" = ");
                        if (assignment.oneOfTypeWrapper) {
                            expressionPrinter.output.append("OneOfWrapper")
                                    .append(assignment.resolvedAssignmentTarget.getTypeReference(
                                            compilationElements.BIT_TYPE).name).append("Option")
                                    .append(assignment.expression.resolvedTypeReference.name).append("(");
                        }
                        expressionPrinter.print(assignment.expression);
                        if (assignment.oneOfTypeWrapper) {
                            expressionPrinter.output.append(")");
                        }
                        expressionPrinter.output.append(";");
                        module.println(expressionPrinter.output);
                    }
                }
                StringBuilder merged = new StringBuilder();
                for (StatementStep region : transform.regions) {
                    addToMerged(merged, region, printBinaryDecisionTree(module, region, "next", variableOrPort,
                            region.variableNextState.get(variableOrPort), compilationElements));
                }
                mergedNextValues.put(variableOrPort, merged.toString());
            }
            TreeMap<StatementStep, String> mergedNextAtValues = new TreeMap<>(Statement.COMPARATOR);
            for (StatementStep region : transform.regions) {
                StringBuilder merged = new StringBuilder();
                for (Map.Entry<StatementStep, StatementRegionAttributes> entry : region.regionAttributes.entrySet()) {
                    addToMerged(merged, entry.getKey(),
                            printBinaryDecisionTree(module, entry.getKey(), "nextAt" + region.suffixForVariables, null,
                                    new BinaryDecisionTreeAndVariableTerminals(entry.getValue().reachabilityTree, null),
                                    compilationElements));
                }
                mergedNextAtValues.put(region, merged.toString());
            }
            for (StatementIfWhile statement : transform.ifWhiles) {
                for (StatementStep region : statement.regionAttributes.keySet()) {
                    ExpressionPrinter expressionPrinter = new ExpressionPrinter(module, region, statement,
                            emittedExpressions);
                    expressionPrinter.output.append("assign condition").append(region.suffixForVariables).append("_")
                            .append(statement.suffixForVariables).append(" = ");
                    expressionPrinter.print(statement.condition);
                    expressionPrinter.output.append(";");
                    module.println(expressionPrinter.output);
                }
            }
            module.println("always_comb");
            module.println("begin");
            module.println("  Error = 0;");
            module.println("  Next = Current;");
            module.println("  Initial = 0;");
            if (transform == compilationElements.systemArchitecture.hostMessageRouter.resolvedTransform) {
                for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsSource) {
                    module.println("  " + connection.source.resolvedPortGroup.portA.name + " = Current." +
                            connection.source.resolvedPortGroup.portA.name + ";");
                }
                module.print("""
                          Initial.PresenceCycleCounterAndErrorCodeIn.ErrorCode = -1;
                          ErrorResetOut = Current.ErrorResetA;
                          FromCoreMessageValid = Current.FromCoreMessageValid;
                          FromCoreMessageWord = Current.FromCoreMessageWord;
                          Next.ErrorResetA = Current.ErrorResetB;
                          Next.ErrorResetB = 0;
                          Next.PresenceCycleCounterAndErrorCodeIn.ErrorCode = -1;
                          Next.FromCoreMessageValid = 0;
                          Next.SendingSnapshot = 0;
                          Next.StopRecording = 0;
                          if (Current.SendingSnapshot)
                          begin
                            Next.Recording = 1;
                          end
                          else if (Current.ErrorResetB | Current.StopRecording)
                          begin
                            Next.Recording = 0;
                          end
                        """);
                if (fromHostMaxWords != 0) {
                    module.println("  Next.ToCoreMessage[" + (fromHostMaxWords > 1 ?
                            "(" + (fromHostMaxWords - 1) + "-Next.ToCoreWord)*" +
                                    SystemArchitecture.FROM_HOST_MESSAGE_WORD_WIDTH : "0") + "+:" +
                            SystemArchitecture.FROM_HOST_MESSAGE_WORD_WIDTH + "] = ToCoreMessageWord;");
                }
                for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsSource) {
                    if (connection.kind == Connection.Kind.Queue) {
                        module.println("  Next.To" + connection.destination.instanceName + "Writer = 0;");
                    } else if (connection.kind == Connection.Kind.Wire) {
                        module.println("  Next.To" + connection.destination.instanceName + " = 0;");
                    }
                }
                Generator.enumerateErrorCodes(false, (errorName, errorValue) -> {
                    module.println("  if (" + errorName + "Error)");
                    module.println("  begin");
                    module.println("    Next.ErrorResetB = 1;");
                    module.println("    Next.PresenceCycleCounterAndErrorCodeIn.ErrorCode = Error" + errorName + ";");
                    module.println("  end");
                }, compilationElements);
                module.print("""
                          if (ToCoreMessageValid)
                          begin
                            if (ToCoreMessageWord.Stop)
                            begin
                              Next.ErrorResetB = 1;
                              Next.PresenceCycleCounterAndErrorCodeIn.ErrorCode = ToCoreMessageWord.Data;
                            end else begin
                        """);
                if (fromHostMaxWords > 1) {
                    module.println("      Next.ToCoreWord = Next.ToCoreWord + 1;");
                }
                int fromHostMessageBitsWithDestination =
                        fromHostMaxWords * SystemArchitecture.FROM_HOST_MESSAGE_WORD_WIDTH;
                int fromHostMessageBits = fromHostMessageBitsWithDestination -
                        compilationElements.systemArchitecture.toCoreDestinationBits;
                emitDestinationConditionBeginning(compilationElements, fromHostMaxWords, module,
                        fromHostMessageBitsWithDestination, fromHostMessageBits, 0, 0);
                module.println("        Next.SendingSnapshot = 1;");
                emitDestinationConditionEnd(fromHostMaxWords, module);
                for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsSource) {
                    emitDestinationConditionBeginning(compilationElements, fromHostMaxWords, module,
                            fromHostMessageBitsWithDestination, fromHostMessageBits,
                            connection.destination.resolvedInstance.toCoreDestinationIndex,
                            connection.destination.resolvedPortGroup.typeReference.resolvedType.totalBits);
                    if (connection.kind == Connection.Kind.Queue) {
                        module.println("        Next.To" + connection.destination.instanceName + "Writer.Option = 1;");
                        if (connection.destination.resolvedPortGroup.typeReference.resolvedType.totalBits != 0) {
                            module.println("        Next.To" + connection.destination.instanceName + "Writer.Value." +
                                    connection.destination.resolvedPortGroup.queueNonBlankTypeReference.name +
                                    ".Value = Next.ToCoreMessage[" + (fromHostMessageBits - 1) + ":" +
                                    (fromHostMessageBits -
                                            connection.destination.resolvedPortGroup.typeReference.resolvedType.totalBits) +
                                    "];");
                        }
                    } else if (connection.kind == Connection.Kind.Wire) {
                        module.println("      Next.To" + connection.destination.instanceName + " = OneOfWrapper" +
                                connection.source.resolvedPortGroup.typeReference.name + "Option" +
                                connection.toCoreNonBlankType.name + "(Next.ToCoreMessage[" +
                                (fromHostMessageBits - 1) + ":" +
                                (fromHostMessageBits - connection.toCoreNonBlankType.resolvedType.totalBits) + "]);");
                    }
                    emitDestinationConditionEnd(fromHostMaxWords, module);
                }
                module.println("    end");
                module.println("  end");
                {
                    for (int index = 0;
                         index < compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels();
                         index++) {
                        module.println("  Next.PresenceCycleCounterAndErrorCodeIn.Presence[" + index +
                                "] = Current.SendingSnapshot || (TraceChunkInA[" + index + "] != TraceChunkInB[" +
                                index + "]);");
                    }
                }
                for (int connectionIndex = 0; connectionIndex <
                        compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination.size();
                     connectionIndex++) {
                    Connection connection = compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination.get(
                            connectionIndex);
                    module.println("  Next.PresenceCycleCounterAndErrorCodeIn.Presence[" +
                            (compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels() +
                                    connectionIndex) + "] = Current.LastB.FromCoreMessages." +
                            connection.source.instanceName + ".Option == " +
                            connection.source.resolvedPortGroup.typeReference.name + "Option" +
                            connection.fromCoreNonBlankType.name + ";");
                }
                module.print("""
                          Next.PresenceCycleCounterAndErrorCodeIn.CycleCounter = PresencePushValid ? 0 : Current.PresenceCycleCounterAndErrorCodeIn.CycleCounter+1;
                          if (!(PresenceReady & (&ChunkReady)))
                          begin
                            Next.StopRecording = 1;
                            Next.PresenceCycleCounterAndErrorCodeIn.ErrorCode = ErrorTraceQueueOverflow;
                          end
                          Next.LastA = Current.LastB;
                          Next.LastB = Current.LastC;
                        """);
                if (compilationElements.includeTracing) {
                    module.println("  Next.LastC.Trace = Trace;");
                }
                for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination) {
                    module.println("  Next.LastC.FromCoreMessages." + connection.source.instanceName + " = " +
                            connection.destination.portGroupName + ";");
                }
                module.print("""
                          PresencePop = 0;
                          ChunkPop = 0;
                          if (Current.PresenceOut == 0)
                          begin
                            if (PresenceHasElement)
                            begin
                        """);
                module.print("      Next.PresenceOut = {1'b1, ~(&PresenceCycleCounterAndErrorCodeOut.ErrorCode.Value)");
                int connectionIndex = compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination.size();
                while (connectionIndex != 0) {
                    connectionIndex--;
                    Connection connection = compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination.get(
                            connectionIndex);
                    module.print(
                            ", {" + (connection.fromCoreChannelRange.end() - connection.fromCoreChannelRange.start()) +
                                    "{PresenceCycleCounterAndErrorCodeOut.Presence[" +
                                    (compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels() +
                                            connectionIndex) + "]}}");
                }
                if (compilationElements.includeTracing) {
                    module.print(", PresenceCycleCounterAndErrorCodeOut.Presence[0+:" +
                            compilationElements.systemArchitecture.fromCoreChannelLayout.tracingChannels() + "]");
                }
                module.println("};");
                module.println("      Next.StopPending = PresenceCycleCounterAndErrorCodeOut.ErrorCode.Value != " +
                        ErrorCode.TraceQueueOverflow.ordinal() +
                        " && PresenceCycleCounterAndErrorCodeOut.ErrorCode.Value != " +
                        ((1 << compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth()) - 1) + ";");
                module.println(
                        "      Next.ChunkOut = {PresenceCycleCounterAndErrorCodeOut.CycleCounter, PresenceCycleCounterAndErrorCodeOut.ErrorCode.Value, ChunkFront};");
                if (channelGroupBits != 0) {
                    module.println("      Next.ChannelGroup = 0;");
                }
                module.print("""
                              PresencePop = 1;
                              ChunkPop = PresenceCycleCounterAndErrorCodeOut.Presence;
                            end
                          end
                          else
                          begin
                        """);
                int channelGroupCount = Math.min(
                        compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 2,
                        MAX_CHUNKS_PER_CYCLE);
                for (int index = 0; index < channelGroupCount; index++) {
                    module.println("    if (Current.PresenceOut[" + index + "]" +
                            (index > 0 ? " && Current.PresenceOut[0+:" + index + "] == 0" : "") + ")");
                    module.println("    begin");
                    int presenceOutRemainder =
                            compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 2 - index -
                                    1;
                    module.println("      Next.PresenceOut = " + (presenceOutRemainder != 0 ?
                            "{Current.PresenceOut[" + (index + 1) + "+:" + presenceOutRemainder + "], " : "") +
                            (index + 1) + "'b0" + (presenceOutRemainder != 0 ? "}" : "") + ";");
                    module.println("      Next.FromCoreMessageValid = 1;");
                    module.println("      Next.FromCoreMessageWord.Stop = " + (index ==
                            ((compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 1) &
                                    (MAX_CHUNKS_PER_CYCLE - 1)) ? (channelGroupBits != 0 ? "Current.ChannelGroup == " +
                            ((compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 1) >>
                                    MAX_CHUNKS_BITS) + " && " : "") + "Current.StopPending" : "0") + ";");
                    module.print("      Next.FromCoreMessageWord.WordA = {");
                    if (channelGroupBits != 0) {
                        module.print("Current.ChannelGroup, ");
                    }
                    module.println(MAX_CHUNKS_BITS + "'d" + index + ", Current.ChunkOut[" + index + "]};");
                    module.println("    end");
                }
                if (channelGroupBits != 0) {
                    module.println("    if (Current.PresenceOut[0+:" + MAX_CHUNKS_PER_CYCLE + "] == 0)");
                    module.println("    begin");
                    module.println("      Next.ChannelGroup = Current.ChannelGroup+1;");
                    module.println("      Next.PresenceOut = Current.PresenceOut[" + MAX_CHUNKS_PER_CYCLE + "+:" +
                            (compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 2 -
                                    MAX_CHUNKS_PER_CYCLE) + "];");
                    module.println("      Next.ChunkOut = Current.ChunkOut[" + MAX_CHUNKS_PER_CYCLE + "+:" +
                            (compilationElements.systemArchitecture.fromCoreChannelLayout.totalChannels() + 2 -
                                    MAX_CHUNKS_PER_CYCLE) + "];");
                    module.println("    end");
                }
                module.print("""
                          end
                        """);
            } else {
                module.println("  Initial." + transform.entry.getAtVariableName() + " = 1;");
                for (VariableOrPort variableOrPort : transform.variablesOrPorts) {
                    module.println("  " + (variableOrPort instanceof Transform.Port castPort ? castPort.name :
                            "Next." + variableOrPort.getName()) + " = " + mergedNextValues.get(variableOrPort) + ";");
                }
                for (StatementStep region : transform.regions) {
                    String mergedNextAtValue = mergedNextAtValues.get(region);
                    module.println("  Next." + region.getAtVariableName() + " = " +
                            (mergedNextAtValue.isEmpty() ? "0" : mergedNextAtValue) + ";");
                }
            }
            module.println("end");
            module.println("endmodule");
            module.println("`default_nettype wire");
        }
    }

    static void addPadding(CompilationElements compilationElements, PrintStream module, String wireName, int start,
                           int end, String source, TypeReference typeReference) {
        int padding =
                typeReference.totalBits() % compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth();
        module.println("Chunk[" + (end - 1) + ":" + start + "] " + wireName + ";");
        module.print("assign " + wireName + '[' + (end - 1) + ":" + start + "] = " + source);
        if (padding != 0) {
            module.print(
                    " << " + (compilationElements.systemArchitecture.fromCoreChannelLayout.channelWidth() - padding));
        }
        module.println(";");
    }

    private static void emitDestinationConditionEnd(int fromHostMaxWords, PrintStream module) {
        if (fromHostMaxWords != 0) {
            if (fromHostMaxWords > 1) {
                module.println("        Next.ToCoreWord = 0;");
            }
            module.println("      end");
        }
    }

    private static void emitDestinationConditionBeginning(CompilationElements compilationElements, int fromHostMaxWords,
                                                          PrintStream module, int fromHostMessageBitsWithDestination,
                                                          int fromHostMessageBits, int toCoreDestinationIndex,
                                                          int destinationBits) {
        boolean moreThanOneToCoreDestination = compilationElements.systemArchitecture.toCoreDestinationBits != 0;
        boolean moreThanOneMaxWords = fromHostMaxWords > 1;
        if (moreThanOneToCoreDestination || moreThanOneMaxWords) {
            module.print("      if (");
            String condition1Text = moreThanOneToCoreDestination ?
                    "Next.ToCoreMessage[" + (fromHostMessageBitsWithDestination - 1) + ":" + fromHostMessageBits +
                            "] == " + toCoreDestinationIndex : null;
            String condition2Text = moreThanOneMaxWords ? "Current.ToCoreWord == " +
                    ((compilationElements.systemArchitecture.toCoreDestinationBits + destinationBits - 1) /
                            SystemArchitecture.FROM_HOST_MESSAGE_WORD_WIDTH) : null;
            if (condition1Text != null && condition2Text != null) {
                module.print("(" + condition1Text + ") & (" + condition2Text + ")");
            } else if (condition1Text != null) {
                module.print(condition1Text);
            } else {
                module.print(condition2Text);
            }
            module.println(")\n      begin");
        }
    }

    static private void addToMerged(StringBuilder merged, StatementStep region, String decisionTreeRoot) {
        if (!merged.isEmpty()) {
            merged.append(" |");
        }
        merged.append("\n  (Current.").append(region.getAtVariableName()).append(" ? ").append(decisionTreeRoot)
                .append(" : 0)");
    }

    private static String printBinaryDecisionTree(PrintStream module, StatementStep region, String location,
                                                  VariableOrPort variableOrPort,
                                                  BinaryDecisionTreeAndVariableTerminals binaryDecisionTreeAndVariableTerminals,
                                                  CompilationElements compilationElements) {
        String type =
                variableOrPort != null ? variableOrPort.getTypeReference(compilationElements.BIT_TYPE).toSVString() :
                        "logic";
        BinaryDecisionTreePrinter printer = new BinaryDecisionTreePrinter(variableOrPort,
                "decisionTree" + region.suffixForVariables + location, region.suffixForVariables,
                binaryDecisionTreeAndVariableTerminals);
        printForRootAndNodesWithMultipleReferences(module, printer,
                index -> type + " " + printer.getNodeName(index) + ";");
        printForRootAndNodesWithMultipleReferences(module, printer,
                index -> "assign " + printer.getNodeName(index) + " = " + printer.getSubtree(index) + ";");
        return printer.getSubtreeBranch(binaryDecisionTreeAndVariableTerminals.tree().getRoot());
    }

    private static void printForRootAndNodesWithMultipleReferences(PrintStream module,
                                                                   BinaryDecisionTreePrinter printer,
                                                                   IntFunction assignLambda) {
        for (int index = 0; index < printer.references.length; index++) {
            if (index == printer.tree.getRoot() || printer.references[index] > 1) {
                module.println(assignLambda.apply(index));
            }
        }
    }
}
