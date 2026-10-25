// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

`default_nettype none

import Types::*;

module FlashProgrammerBridge (
    input wire PCIEClock, input wire PCIEReset,
    output wire ReadyForRequest,
    input wire RequestValid, input AXIRequest RequestData,
    input wire ReadyForResponse,
    output wire ResponseValid, output AXIResponse ResponseData,
    input wire Clock50MHz,
    input wire Reset50MHz);

typedef struct packed {
    bit [1:0] WordIndex;
    bit ReadSendingAddress;
    bit ReadReceivingResponse;
    bit WriteSendingAddress;
    bit WriteSendingData;
    AXIRequest Request;
    AXIResponse Response;
} State;

State Current, Next;
wire ReadyForReadRequest;
wire ReadResponseValid;
wire [31:0] ReadData;
wire ReadyForWriteAddress;
wire ReadyForWriteData;
wire PCIEToAXIDataValid;
AXIRequest PCIEToAXIDataOut;
wire PCIEToAXIAlmostFull;
assign ReadyForRequest = ~PCIEToAXIAlmostFull;
wire AXIToPCIEAlmostFull;
wire Idle = ~(Current.ReadSendingAddress | Current.ReadReceivingResponse | Current.WriteSendingAddress | Current.WriteSendingData | AXIToPCIEAlmostFull);
logic StartTransaction;
WriteDataStruct RequestWriteDataField;
assign RequestWriteDataField = Next.Request.OperationDetails.WriteDataField;
bit[3:0] FirstBits;
assign FirstBits = RequestWriteDataField.FirstByteEnable[0] ? 4 : (RequestWriteDataField.FirstByteEnable[1] ? 3 : (RequestWriteDataField.FirstByteEnable[2] ? 2 : (RequestWriteDataField.FirstByteEnable[3] ? 1 : 0)));
bit[3:0] LastBits;
assign LastBits = RequestWriteDataField.LastByteEnable[3] ? 4 : (RequestWriteDataField.LastByteEnable[2] ? 3 : (RequestWriteDataField.LastByteEnable[1] ? 2 : (RequestWriteDataField.LastByteEnable[0] ? 1 : 0)));

always_comb
begin
    Next = Current;
    StartTransaction = 0;
    if (Idle & PCIEToAXIDataValid)
    begin
        Next.Request = PCIEToAXIDataOut;
        Next.Response.Length = Next.Request.Length;
        Next.Response.ReadCompletionField = Next.Request.OperationDetails.PaddedReadCompletionField.ReadCompletionField;
        Next.Response.TotalBytes = Next.Request.Length == 1 ? FirstBits : FirstBits+(Next.Request.Length-2)*4+LastBits;
        StartTransaction = 1;
    end
    if (Current.ReadSendingAddress & ReadyForReadRequest)
    begin
        Next.ReadSendingAddress = 0;
        Next.ReadReceivingResponse = 1;
    end
    if (Current.ReadReceivingResponse & ReadResponseValid)
    begin
        Next.Response.ReadData[Current.WordIndex*32+:32] = ReadData;
        Next.ReadReceivingResponse = 0;
    end
    if (Current.WriteSendingAddress & ReadyForWriteAddress)
    begin
        Next.WriteSendingAddress = 0;
    end
    if (Current.WriteSendingData & ReadyForWriteData)
    begin
        Next.WriteSendingData = 0;
    end
    if ((Current.ReadReceivingResponse & ~Next.ReadReceivingResponse) |
        ((Current.WriteSendingAddress | Current.WriteSendingData) & ~((Next.WriteSendingAddress | Next.WriteSendingData))))
    begin
        Next.WordIndex = Current.WordIndex + 1;
        if (Next.WordIndex == Current.Request.Length)
        begin
            Next.WordIndex = 0;
        end
        else
        begin
            StartTransaction = 1;
        end
    end
    if (StartTransaction)
    begin
        Next.ReadSendingAddress = ~Next.Request.OperationIsWrite;
        Next.WriteSendingAddress = Next.Request.OperationIsWrite;
        Next.WriteSendingData = Next.Request.OperationIsWrite;
    end
end

always_ff @(posedge Clock50MHz)
begin
    Current <= Reset50MHz ? 0 : Next;
end

xpm_fifo_async #(.READ_MODE("fwft"), .READ_DATA_WIDTH($bits(AXIRequest)), .WRITE_DATA_WIDTH($bits(AXIRequest)), .FIFO_WRITE_DEPTH(256), .PROG_FULL_THRESH(240), .USE_ADV_FEATURES("1002")) PCIEToAXI
    (.rst(PCIEReset), .wr_clk(PCIEClock), .wr_en(RequestValid), .din(RequestData), .prog_full(PCIEToAXIAlmostFull),
     .rd_en(Idle), .rd_clk(Clock50MHz), .data_valid(PCIEToAXIDataValid), .dout(PCIEToAXIDataOut));

xpm_fifo_async #(.READ_MODE("fwft"), .READ_DATA_WIDTH($bits(AXIResponse)), .WRITE_DATA_WIDTH($bits(AXIResponse)), .FIFO_WRITE_DEPTH(128), .PROG_FULL_THRESH(120), .USE_ADV_FEATURES("1002")) AXIToPCIE
    (.rst(Reset50MHz), .wr_clk(Clock50MHz), .wr_en(Current.ReadReceivingResponse & ~Next.ReadReceivingResponse & Next.WordIndex == 0), .din(Next.Response), .prog_full(AXIToPCIEAlmostFull),
     .rd_en(ReadyForResponse), .rd_clk(PCIEClock), .data_valid(ResponseValid), .dout(ResponseData));

wire [31:0] WriteData;
assign WriteData = Current.Request.OperationDetails.WriteDataField.WriteData[Current.WordIndex*32+:32];
wire [3:0] WriteStrb [3:0];
assign WriteStrb[0] = Current.Request.OperationDetails.WriteDataField.FirstByteEnable;
assign WriteStrb[1] = Current.Request.Length == 2 ? Current.Request.OperationDetails.WriteDataField.LastByteEnable : 4'hf;
assign WriteStrb[2] = Current.Request.OperationDetails.WriteDataField.LastByteEnable;
assign WriteStrb[3] = 0;
FlashProgrammerBlock FlashProgrammerInstance (
    .s_axi_araddr({Current.Request.Address + Current.WordIndex, 2'b0}),
    .s_axi_arready(ReadyForReadRequest),
    .s_axi_arvalid(Current.ReadSendingAddress),
    .s_axi_awaddr({Current.Request.Address + Current.WordIndex, 2'b0}),
    .s_axi_awready(ReadyForWriteAddress),
    .s_axi_awvalid(Current.WriteSendingAddress),
    .s_axi_bready(1),
    .s_axi_rdata(ReadData),
    .s_axi_rready(Current.ReadReceivingResponse),
    .s_axi_rvalid(ReadResponseValid),
    .s_axi_wdata(WriteData),
    .s_axi_wready(ReadyForWriteData),
    .s_axi_wstrb(WriteStrb[Current.WordIndex]),
    .s_axi_wvalid(Current.WriteSendingData),
    .ext_spi_clk(Clock50MHz),
    .gsr(0),
    .gts(0),
    .keyclearb(0),
    .usrdoneo(1),
    .usrdonets(0),
    .usrcclkts(0),
    .s_axi_aclk(Clock50MHz),
    .s_axi_aresetn(~Reset50MHz));

endmodule

`default_nettype wire
