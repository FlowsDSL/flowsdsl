// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

import Types::*;

module CMSBridgeTransform (
    input wire Clock, input wire SystemReset, input wire ErrorResetIn,
    output CMSBridgeStateAndInputs Trace,
    input BlankOrCMSReadRequest Request,
    output BlankOrCMSReadResponse Response,
    output bit Error,
    input wire[1:0] satellite_gpio, input wire satellite_uart_rxd, output wire satellite_uart_txd,
    input wire Clock50MHz, input wire Reset50MHz);

typedef struct packed {
    bit[15:0] InitializationCountup;
    bit WriteSendingAddress;
    bit WriteSendingData;
} State;

assign Trace.Request = Request;

wire CoreToAXIDataValid;
wire CoreToAXIReadEnable;
CMSReadRequest CoreToAXIDataOut;

xpm_fifo_async #(.READ_MODE("fwft"), .READ_DATA_WIDTH($bits(CMSReadRequest)), .WRITE_DATA_WIDTH($bits(CMSReadRequest)), .FIFO_WRITE_DEPTH(16), .USE_ADV_FEATURES("1000")) CoreToAXI
    (.rst(SystemReset | ErrorResetIn), .wr_clk(Clock), .wr_en(Request.Option == BlankOrCMSReadRequestOptionCMSReadRequest), .din(Request.Value.CMSReadRequest.Value),
     .rd_en(CoreToAXIReadEnable), .rd_clk(Clock50MHz), .data_valid(CoreToAXIDataValid), .dout(CoreToAXIDataOut));

wire ReadyForWriteAddress;
wire ReadyForWriteData;

wire AXIToCoreWriteEnable;
CMSReadResponse AXIToCoreWriteData;

CMSSubsystemBlock CMSSubsystemInstance (
    .s_axi_ctrl_awaddr(32'h20000),
    .s_axi_ctrl_awprot(0),
    .s_axi_ctrl_awvalid(Current.WriteSendingAddress),
    .s_axi_ctrl_awready(ReadyForWriteAddress),
    .s_axi_ctrl_wdata(1),
    .s_axi_ctrl_wstrb(4'hf),
    .s_axi_ctrl_wvalid(Current.WriteSendingData),
    .s_axi_ctrl_wready(ReadyForWriteData),
    .s_axi_ctrl_bready(1),
    .s_axi_ctrl_araddr({14'b0, CoreToAXIDataOut}),
    .s_axi_ctrl_arprot(0),
    .s_axi_ctrl_arvalid(CoreToAXIDataValid),
    .s_axi_ctrl_arready(CoreToAXIReadEnable),
    .s_axi_ctrl_rdata(AXIToCoreWriteData),
    .s_axi_ctrl_rvalid(AXIToCoreWriteEnable),
    .s_axi_ctrl_rready(1),
    .satellite_uart_rxd(satellite_uart_rxd),
    .satellite_uart_txd(satellite_uart_txd),
    .satellite_gpio(satellite_gpio),
    .interrupt_hbm_cattrip(0),
    .hbm_temp_1(0),
    .hbm_temp_2(0),
    .aclk_ctrl(Clock50MHz),
    .aresetn_ctrl(~Reset50MHz));

wire ResponseValid;

assign Response.Option = ResponseValid == BlankOrCMSReadResponseOptionCMSReadResponse;

xpm_fifo_async #(.READ_MODE("fwft"), .READ_DATA_WIDTH($bits(CMSReadResponse)), .WRITE_DATA_WIDTH($bits(CMSReadResponse)), .FIFO_WRITE_DEPTH(16), .USE_ADV_FEATURES("1000")) AXIToPCIE
    (.rst(Reset50MHz), .wr_clk(Clock50MHz), .wr_en(AXIToCoreWriteEnable), .din(AXIToCoreWriteData),
     .rd_en(1), .rd_clk(Clock), .data_valid(ResponseValid), .dout(Response.Value.CMSReadResponse.Value));

State Current, Next;

assign Error = 0;

always_comb
begin
    Next = Current;
    if (Current.InitializationCountup == 16'hfffe)
    begin
        Next.WriteSendingAddress = 1;
        Next.WriteSendingData = 1;
    end
    if (Current.WriteSendingAddress & ReadyForWriteAddress)
    begin
        Next.WriteSendingAddress = 0;
    end
    if (Current.WriteSendingData & ReadyForWriteData)
    begin
        Next.WriteSendingData = 0;
    end
    if (Current.InitializationCountup < 16'hffff)
    begin
        Next.InitializationCountup = Current.InitializationCountup+1;
    end
end

always_ff @(posedge Clock50MHz)
begin
    Current <= Reset50MHz ? 0 : Next;
end

endmodule
