// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

`default_nettype none

module ScramblerUnscrambler
    #(
        parameter UNSCRAMBLER
    )
    (
        input wire Clock,
        input wire Reset,
        input wire Advance,
        input wire[31:0] DataIn,
        output wire[31:0] DataOut
    );

logic[57:0] CurrentState, NextState;
logic[57:0] IntermediateState [32:0];
assign IntermediateState[0] = CurrentState;
genvar i;
generate
    for (i = 0; i < 32; i++) begin
        localparam int DATA_IN_INDEX = UNSCRAMBLER ? 31-i : i;
        localparam int DATA_OUT_INDEX = UNSCRAMBLER ? i : 31-i;
        assign DataOut[DATA_OUT_INDEX] = IntermediateState[i][38] ^ IntermediateState[i][57] ^ DataIn[DATA_IN_INDEX];
        assign IntermediateState[i+1] = {IntermediateState[i][56:0], UNSCRAMBLER ? DataIn[DATA_IN_INDEX] : DataOut[DATA_OUT_INDEX]};
    end
endgenerate
assign NextState = IntermediateState[32];

always_ff @(posedge Clock)
begin
    CurrentState <= Reset ? 0 : (Advance ? NextState : CurrentState);
end

endmodule

`default_nettype wire
