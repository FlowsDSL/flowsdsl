// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

`default_nettype none

module BlockRam
    #(
        parameter DATA_WIDTH = 9,
        parameter ADDRESS_WIDTH = 12
    ) (
        input wire                    Clock,
        input wire                    ReadEnable,
        input wire[ADDRESS_WIDTH-1:0] ReadAddress,
        output wire[DATA_WIDTH-1:0]   ReadData,
        input wire                    WriteEnable,
        input wire[ADDRESS_WIDTH-1:0] WriteAddress,
        input wire[DATA_WIDTH-1:0]    WriteData
    );

    reg [DATA_WIDTH-1:0] Data [2**ADDRESS_WIDTH-1:0];
    reg [DATA_WIDTH-1:0] ReadResult;

    assign ReadData = ReadResult;

    always @(posedge Clock)
    begin
        if (WriteEnable) begin
            Data[WriteAddress] <= WriteData;
        end
        if (ReadEnable) begin
            ReadResult <= Data[ReadAddress];
        end
    end
endmodule

`default_nettype wire
