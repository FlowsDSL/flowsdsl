// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.generator;

import dsl.flows.sde.models.CompilationElements;
import dsl.flows.sde.models.Connection;
import dsl.flows.sde.models.Instance;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

public class CoreModuleGenerator {
    static void generateCoreModule(String outputDirectory, CompilationElements compilationElements) throws IOException {
        try (PrintStream top = new PrintStream(outputDirectory + "/Core.sv")) {
            Generator.include(top, "verilog/CorePreamble.sv");
            if (compilationElements.includeRawEthernetFrames) {
                top.println("""
                        logic[33:0] RawUnscrambledEthernetReceiveSideReg;
                        wire RawUnscrambledEthernetFIFOReadResetBusy;
                        wire[65:0] RawUnscrambledEthernetFIFODataOut;
                        wire RawUnscrambledEthernetFIFODataValid;
                        logic[65:0] RawUnscrambledEthernetSendSideReg;
                        
                        xpm_fifo_async #(.READ_MODE("fwft"), .WRITE_DATA_WIDTH(66), .READ_DATA_WIDTH (66), .FIFO_WRITE_DEPTH(16), .USE_ADV_FEATURES("1002")) RawUnscrambledEthernetQueue (
                            .rst(NetworkReceiveReset),
                            .wr_clk(NetworkReceiveClock), .wr_en(rxdatavalid_out & !rxheadervalid_out), .din({RawUnscrambledEthernetReceiveSideReg[32+:2], UnscrambledRx, RawUnscrambledEthernetReceiveSideReg[0+:32]}),
                            .rd_clk(NetworkSendClock), .rd_en(~RawUnscrambledEthernetFIFOReadResetBusy), .rd_rst_busy(RawUnscrambledEthernetFIFOReadResetBusy), .data_valid(RawUnscrambledEthernetFIFODataValid), .dout(RawUnscrambledEthernetFIFODataOut));
                        
                        always_ff @(posedge NetworkReceiveClock)
                        begin
                            RawUnscrambledEthernetReceiveSideReg <= NetworkReceiveReset ? 0 : {rxheadervalid_out ? rxheader_out : RawUnscrambledEthernetReceiveSideReg[32+:2],
                                rxdatavalid_out & rxheadervalid_out ? UnscrambledRx : RawUnscrambledEthernetReceiveSideReg[0+:32]};
                        end
                        
                        always_ff @(posedge NetworkSendClock)
                        begin
                            RawUnscrambledEthernetSendSideReg <= NetworkSendReset ? 0 : (RawUnscrambledEthernetFIFODataValid ? RawUnscrambledEthernetFIFODataOut : RawUnscrambledEthernetSendSideReg);
                        end
                        """);
            }
            if (compilationElements.includeTracing) {
                top.println("TraceWord Trace;");
            }
            for (Connection connection : compilationElements.systemArchitecture.connections) {
                if (connection.kind == Connection.Kind.Queue) {
                    top.println(
                            connection.source.resolvedPortGroup.typeReference.name + " " + connection.getFullName() +
                                    "Writer;");
                    top.println(
                            connection.source.resolvedPortGroup.typeReference.name + " " + connection.getFullName() +
                                    "Reader;");
                    top.println("wire " + connection.getFullName() + "Pop;");
                    top.println("wire " + connection.getFullName() + "Ready;");
                    top.println("wire " + connection.getFullName() + "Error;");
                    boolean emptyElementType =
                            connection.source.resolvedPortGroup.typeReference.resolvedType.totalBits == 0;
                    top.println("Queue #(.ELEMENT_SIZE(" + (emptyElementType ? "0" :
                            "$bits(" + connection.source.resolvedPortGroup.queueNonBlankTypeReference.name + ")") +
                            "), .ADDRESS_WIDTH(" +
                            (connection.source.resolvedPortGroup.typeReference.resolvedType.totalBits > 128 ? 3 : 5) +
                            ")) " + connection.getFullName() +
                            " (\n    .Clock(NetworkSendClock), .Reset(NetworkSendReset | ErrorReset),\n" +
                            "    .PushElement(" + (emptyElementType ? "0" : connection.getFullName() + "Writer.Value." +
                            connection.source.resolvedPortGroup.queueNonBlankTypeReference.name + ".Value") + "),\n" +
                            "    .PushValid(" + connection.getFullName() + "Writer.Option),\n" + "    .HasElement(" +
                            connection.getFullName() + "Reader.Option),\n" + (emptyElementType ? "" :
                            "    .Front(" + connection.getFullName() + "Reader.Value." +
                                    connection.source.resolvedPortGroup.queueNonBlankTypeReference.name +
                                    ".Value),\n") + "    .Pop(" + connection.getFullName() + "Pop),\n" + "    .Ready(" +
                            connection.getFullName() + "Ready),\n" + "    .Error(" + connection.getFullName() +
                            "Error));");
                } else if (connection.kind == Connection.Kind.WireWithBackpressure) {
                    top.println(
                            connection.source.resolvedPortGroup.typeReference.name + " " + connection.getFullName() +
                                    "Word;");
                    top.println("wire " + connection.getFullName() + "Ready;");
                } else {
                    top.println(connection.destination.resolvedPortGroup.typeReference.toSVString() + " " +
                            connection.getFullName() + ";");
                }
                if (connection.destination.instanceName.equals("Ethernet") &&
                        connection.destination.portGroupName.equals("Send")) {
                    if (compilationElements.includeTracing) {
                        top.println("assign Trace.Ethernet.Send = " + connection.getFullName() + "Word;");
                    }
                    top.println("assign NetworkSendWord = " + connection.getFullName() + "Word;");
                    if (compilationElements.includeRawEthernetFrames) {
                        top.println("assign Trace.RawUnscrambledEthernet = RawUnscrambledEthernetSendSideReg;");
                    }
                    top.println("assign " + connection.getFullName() + "Ready = NetworkSendReady;");
                }
                if (connection.source.instanceName.equals("Ethernet") &&
                        connection.source.portGroupName.equals("Receive")) {
                    top.println("assign " + connection.getFullName() + " = NetworkReceiveWithoutFCS;");
                }
            }
            for (String innerEntry : compilationElements.systemArchitecture.instances.keySet()) {
                top.println("bit " + innerEntry + "Error;");
            }
            for (Map.Entry<String, Instance> entry : compilationElements.systemArchitecture.instances.entrySet()) {
                if (entry.getKey().equals("Ethernet")) {
                    continue;
                }
                top.println(entry.getValue().transformName + " " + entry.getKey() + " (");
                top.print(
                        "    .Clock(NetworkSendClock), .SystemReset(NetworkSendReset), .ErrorResetIn(ErrorReset), .Error(" +
                                entry.getKey() + "Error)");
                if (entry.getKey().equals("CMSBridge")) {
                    top.print(
                            ",\n    .Clock50MHz(Clock50MHz), .Reset50MHz(Reset50MHz), .satellite_gpio(satellite_gpio), .satellite_uart_rxd(satellite_uart_rxd), .satellite_uart_txd(satellite_uart_txd)");
                }
                if (entry.getValue() == compilationElements.systemArchitecture.hostMessageRouter) {
                    top.print(
                            ",\n    .ToCoreMessageValid(NetworkSendCurrent.ToCoreMessageValid), .ToCoreMessageWord(NetworkSendCurrent.ToCoreMessageWord)");
                    top.print(
                            ",\n    .FromCoreQueueAlmostFull(NetworkSendCurrent.FromCoreQueueAlmostFull[1]), .FromCoreMessageValid(HostMessageDispatcherFromCoreMessageValid), .FromCoreMessageWord(HostMessageDispatcherFromCoreMessageWord)");
                    for (String instanceName : compilationElements.systemArchitecture.instances.keySet()) {
                        top.print(",\n    ." + instanceName + "Error(" + instanceName + "Error)");
                    }
                    for (Connection connection : compilationElements.systemArchitecture.connections) {
                        if (connection.kind == Connection.Kind.Queue) {
                            top.print(",\n    ." + connection.getFullName() + "Error(" + connection.getFullName() +
                                    "Error)");
                        }
                    }
                    top.print(",\n    .ErrorResetOut(ErrorReset)");
                    if (compilationElements.includeTracing) {
                        top.print(", .Trace(Trace)");
                    }
                } else if (compilationElements.includeTracing) {
                    top.print(",\n    .Trace(Trace." + entry.getKey() + ")");
                }
                for (Connection connection : entry.getValue().connectionsWithInstanceAsSource) {
                    if (connection.kind == Connection.Kind.Queue) {
                        top.print(",\n    ." + connection.source.portGroupName + "Writer(" + connection.getFullName() +
                                "Writer)");
                    } else if (connection.kind == Connection.Kind.WireWithBackpressure) {
                        top.print(",\n    ." + connection.source.portGroupName + "Word(" + connection.getFullName() +
                                "Word)");
                        top.print(",\n    ." + connection.source.portGroupName + "Ready(" + connection.getFullName() +
                                "Ready)");
                    } else {
                        top.print(",\n    ." + connection.source.portGroupName + "(" + connection.getFullName() + ")");
                    }
                }
                for (Connection connection : entry.getValue().connectionsWithInstanceAsDestination) {
                    if (connection.kind == Connection.Kind.Queue) {
                        top.print(",\n    ." + connection.destination.portGroupName + "Reader(" +
                                connection.getFullName() + "Reader)");
                        top.print(
                                ",\n    ." + connection.destination.portGroupName + "Pop(" + connection.getFullName() +
                                        "Pop)");
                    } else if (connection.kind == Connection.Kind.WireWithBackpressure) {
                        top.print(
                                ",\n    ." + connection.destination.portGroupName + "Word(" + connection.getFullName() +
                                        "Word)");
                        top.print(",\n    ." + connection.destination.portGroupName + "Ready(" +
                                connection.getFullName() + "Ready)");
                    } else {
                        top.print(",\n    ." + connection.destination.portGroupName + "(" + connection.getFullName() +
                                ")");
                    }
                }
                top.println(");");
            }
            top.println("""
                    endmodule
                    `default_nettype wire
                    """);
        }
    }
}
