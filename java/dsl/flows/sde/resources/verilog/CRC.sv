// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

`default_nettype none

module CRC
    (
        input wire Clock,
        input wire Reset,
        input wire[2:0] Advance,
        input wire[31:0] DataIn,
        output wire[31:0] DataOut
    );

logic[31:0] CurrentState;
logic[31:0] NextState [32:0];
assign NextState[0] = CurrentState;
genvar i;
generate
    for (i = 0; i < 32; i++) begin
        assign NextState[i+1] = {1'b0, NextState[i][31:1]} ^ ((NextState[i][0] ^ DataIn[i]) ? 32'hedb88320 : 0);
    end
endgenerate
assign DataOut = ~CurrentState;

always_ff @(posedge Clock)
begin
    CurrentState <= Reset ? 32'hffffffff : NextState[Advance*8];
end

endmodule

`default_nettype wire
