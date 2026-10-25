// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

`default_nettype none

import Types::*;

module EthernetPCSAndMAC (
    input wire SendClock,
    input wire SendReset,
    output wire[5:0] SendSequenceOut,
    output logic[5:0] SendHeader,
    output logic[31:0] SendDataWord,
    output logic SendReady,
    input Network32Word SendWord,

    input wire ReceiveClock,
    input wire ReceiveReset,
    input wire ReceiveHeaderValid,
    input wire[1:0] ReceiveHeaderWord,
    input wire ReceiveDataValid,
    input wire[31:0] ReceiveDataWord,
    output Network32Word ReceiveWord,
    output wire ReceiveSlip);

localparam int START_BLOCK_FIRST_WORD = 32'h55555578;
localparam int START_BLOCK_SECOND_WORD = 32'hd5555555;

typedef struct packed {
    bit Idle;
    bit StartBlock;
    bit InFrameExpectingHeader;
    bit InFrameNotExpectingHeader;
    bit TerminatingFrame0;
    bit TerminatingFrame1;
    bit TerminatingFrame2;
    bit TerminatingFrame3;
    bit TerminatingFrame4;
    bit [23:0] CarryoverData;
    bit [2:0] CarryoverByteCount;
    bit CarryoverLast;
    bit [3:0] ReceiveSlipRegister;
} ReceiveMACState;

ReceiveMACState ReceiveMACInitial, ReceiveMACCurrent, ReceiveMACNext;

typedef struct packed {
    bit Idle;
    bit SendingIdle2;
    bit StartBlock;
    bit SendingHeaderAndWord;
    bit SendingTerminateWord1;
    bit SendingTerminateWord2;
    bit SendingWord;
    bit SendingWordToZeroTerminate;
    bit [3:0] InterframeGap;
    bit [39:0] TerminateWord;
    bit [6:0] SendSequence;
    Network32Word WordA;
    Network32Word WordB;
    bit [31:0] CRCDataIn;
    bit [2:0] CRCAdvance;
} SendMACState;

SendMACState SendMACInitial, SendMACCurrent, SendMACNext;

assign SendSequenceOut = SendMACCurrent.SendSequence[6:1];
assign ReceiveSlip = ReceiveMACCurrent.ReceiveSlipRegister == 1;

logic[7:0] TERMINATE_CODES [7:0];
assign TERMINATE_CODES[0] = 8'h87;
assign TERMINATE_CODES[1] = 8'h99;
assign TERMINATE_CODES[2] = 8'haa;
assign TERMINATE_CODES[3] = 8'hb4;
assign TERMINATE_CODES[4] = 8'hcc;
assign TERMINATE_CODES[5] = 8'hd2;
assign TERMINATE_CODES[6] = 8'he1;
assign TERMINATE_CODES[7] = 8'hff;
wire[7:0] Terminate;
genvar i;
generate
    for (i = 0; i < 8; i++)
    begin
        assign Terminate[i] = ReceiveDataWord[7:0] == TERMINATE_CODES[i];
    end
endgenerate
wire IsStartOfBlockFirstWord;
assign IsStartOfBlockFirstWord = ReceiveDataWord == START_BLOCK_FIRST_WORD;
wire IsStartOfBlockSecondWord;
assign IsStartOfBlockSecondWord = ReceiveDataWord == START_BLOCK_SECOND_WORD;
assign ReceiveWord.ByteCount = ReceiveMACCurrent.Idle ?
    ReceiveMACCurrent.CarryoverByteCount :
    (ReceiveDataValid ?
        (ReceiveMACCurrent.InFrameExpectingHeader & (ReceiveHeaderWord == 2) ?
            (Terminate[3] ?
                3 :
                (Terminate[2] ?
                    2 :
                    Terminate[1])) :
            (ReceiveMACCurrent.InFrameExpectingHeader | ReceiveMACCurrent.InFrameNotExpectingHeader |
             ReceiveMACCurrent.TerminatingFrame1 | ReceiveMACCurrent.TerminatingFrame2 |
             ReceiveMACCurrent.TerminatingFrame3 | ReceiveMACCurrent.TerminatingFrame4) ?
                4 :
                0) :
        0);
assign ReceiveWord.Data = ReceiveMACCurrent.Idle ? {8'h0, ReceiveMACCurrent.CarryoverData} :
    (ReceiveMACCurrent.InFrameExpectingHeader & (ReceiveHeaderWord == 2) ? {8'h0, ReceiveDataWord[31:8]} :
        (ReceiveMACCurrent.TerminatingFrame1 | ReceiveMACCurrent.TerminatingFrame2 | ReceiveMACCurrent.TerminatingFrame3 | ReceiveMACCurrent.TerminatingFrame4 ? {ReceiveDataWord[7:0], ReceiveMACCurrent.CarryoverData} : ReceiveDataWord));
assign ReceiveWord.Last = ReceiveMACCurrent.InFrameExpectingHeader ? (ReceiveHeaderValid & ReceiveHeaderWord == 2 & (|Terminate[3:0])) :
    (ReceiveMACCurrent.TerminatingFrame1 & ReceiveDataValid ? 1 : (ReceiveMACCurrent.Idle ? ReceiveMACCurrent.CarryoverLast : 0));

always_comb
begin
    ReceiveMACInitial = 0;
    ReceiveMACInitial.Idle = 1;
    ReceiveMACNext = ReceiveMACCurrent;
    ReceiveMACNext.ReceiveSlipRegister = ReceiveMACCurrent.ReceiveSlipRegister == 0 ? {3'b0, (ReceiveHeaderValid & (ReceiveHeaderWord[0] == ReceiveHeaderWord[1]))} : ReceiveMACCurrent.ReceiveSlipRegister+1;
    if (ReceiveMACCurrent.Idle)
    begin
        ReceiveMACNext.CarryoverByteCount = 0;
        ReceiveMACNext.CarryoverLast = 0;
    end
    if (ReceiveDataValid)
    begin
        if (ReceiveMACCurrent.Idle & ReceiveHeaderValid & ReceiveHeaderWord == 2 & IsStartOfBlockFirstWord)
        begin
            ReceiveMACNext.Idle = 0;
            ReceiveMACNext.StartBlock = 1;
        end
        if (ReceiveMACCurrent.StartBlock)
        begin
            ReceiveMACNext.StartBlock = 0;
            ReceiveMACNext.InFrameExpectingHeader = IsStartOfBlockSecondWord;
            ReceiveMACNext.Idle = !IsStartOfBlockSecondWord;
        end
        if (ReceiveMACCurrent.InFrameExpectingHeader & ReceiveHeaderValid)
        begin
            if (ReceiveHeaderWord == 1)
            begin
                ReceiveMACNext.InFrameExpectingHeader = 0;
                ReceiveMACNext.InFrameNotExpectingHeader = 1;
            end
            if (ReceiveHeaderWord == 2)
            begin
                ReceiveMACNext.InFrameExpectingHeader = 0;
                ReceiveMACNext.TerminatingFrame0 = |Terminate[3:0];
                ReceiveMACNext.TerminatingFrame1 = Terminate[4];
                ReceiveMACNext.TerminatingFrame2 = Terminate[5];
                ReceiveMACNext.TerminatingFrame3 = Terminate[6];
                ReceiveMACNext.TerminatingFrame4 = Terminate[7];
                ReceiveMACNext.CarryoverData = ReceiveDataWord[31:8];
                ReceiveMACNext.Idle = ~(|Terminate[7:0]);
            end
        end
        if (ReceiveMACCurrent.InFrameNotExpectingHeader)
        begin
            ReceiveMACNext.InFrameNotExpectingHeader = 0;
            ReceiveMACNext.InFrameExpectingHeader = 1;
        end
        if (ReceiveMACCurrent.TerminatingFrame0)
        begin
            ReceiveMACNext.TerminatingFrame0 = 0;
            ReceiveMACNext.Idle = 1;
        end
        if (ReceiveMACCurrent.TerminatingFrame1)
        begin
            ReceiveMACNext.TerminatingFrame1 = 0;
            ReceiveMACNext.CarryoverByteCount = 0;
            ReceiveMACNext.CarryoverLast = 0;
            ReceiveMACNext.Idle = 1;
        end
        if (ReceiveMACCurrent.TerminatingFrame2)
        begin
            ReceiveMACNext.TerminatingFrame2 = 0;
            ReceiveMACNext.CarryoverData = ReceiveDataWord[31:8];
            ReceiveMACNext.CarryoverByteCount = 1;
            ReceiveMACNext.CarryoverLast = 1;
            ReceiveMACNext.Idle = 1;
        end
        if (ReceiveMACCurrent.TerminatingFrame3)
        begin
            ReceiveMACNext.TerminatingFrame3 = 0;
            ReceiveMACNext.CarryoverData = ReceiveDataWord[31:8];
            ReceiveMACNext.CarryoverByteCount = 2;
            ReceiveMACNext.CarryoverLast = 1;
            ReceiveMACNext.Idle = 1;
        end
        if (ReceiveMACCurrent.TerminatingFrame4)
        begin
            ReceiveMACNext.TerminatingFrame4 = 0;
            ReceiveMACNext.CarryoverData = ReceiveDataWord[31:8];
            ReceiveMACNext.CarryoverByteCount = 3;
            ReceiveMACNext.CarryoverLast = 1;
            ReceiveMACNext.Idle = 1;
        end
    end
end

always_ff @(posedge ReceiveClock)
begin
    ReceiveMACCurrent <= ReceiveReset ? ReceiveMACInitial : ReceiveMACNext;
end

logic[2:0] CRCAdvance;
logic CRCReset;
logic[31:0] CRCDataOut;

CRC CRCInstance (
    .Clock(SendClock),
    .Reset(SendReset | CRCReset),
    .Advance(SendMACCurrent.CRCAdvance),
    .DataIn(SendMACCurrent.CRCDataIn),
    .DataOut(CRCDataOut));

always_comb
begin
    SendMACInitial = 0;
    SendMACInitial.Idle = 1;
    CRCReset = 0;
    SendReady = 0;
    SendMACNext = SendMACCurrent;
    SendMACNext.CRCAdvance = 0;
    SendMACNext.CRCDataIn = SendWord.Data;
    SendMACNext.SendSequence = (SendMACCurrent.SendSequence == 65 ? 0 : SendMACCurrent.SendSequence+1);
    SendHeader = 0;
    SendDataWord = 0;
    if (SendMACCurrent.SendSequence[6:1] != 32)
    begin
        if (SendMACCurrent.Idle)
        begin
            SendMACNext.Idle = 0;
            if ({SendMACCurrent.WordA.ByteCount, SendMACCurrent.WordA.Last} != 0)
            begin
                SendMACNext.StartBlock = 1;
                SendHeader = 2;
                SendDataWord = START_BLOCK_FIRST_WORD;
            end
            else
            begin
                SendMACNext.SendingIdle2 = 1;
                SendHeader = 2;
                SendDataWord = 32'h1e;
            end
        end
        if (SendMACCurrent.StartBlock)
        begin
            SendMACNext.StartBlock = 0;
            SendMACNext.SendingHeaderAndWord = 1;
            SendDataWord = START_BLOCK_SECOND_WORD;
        end
        if (SendMACCurrent.SendingIdle2)
        begin
            SendMACNext.SendingIdle2 = 0;
            SendMACNext.Idle = 1;
        end
        if (SendMACCurrent.SendingHeaderAndWord)
        begin
            SendMACNext.SendingHeaderAndWord = 0;
            if (SendMACCurrent.WordA.Last & SendMACCurrent.WordA.ByteCount < 4)
            begin
                SendHeader = 2;
                SendDataWord = {
                    ({8{SendMACCurrent.WordA.ByteCount > 2}} & SendMACCurrent.WordA.Data[23:16]),
                    ({8{SendMACCurrent.WordA.ByteCount > 1}} & SendMACCurrent.WordA.Data[15:8]),
                    SendMACCurrent.WordA.Data[7:0],
                    TERMINATE_CODES[4+SendMACCurrent.WordA.ByteCount]
                } | (CRCDataOut << (8*(1+SendMACCurrent.WordA.ByteCount)));
                SendMACNext.SendingTerminateWord2 = 1;
                SendMACNext.TerminateWord = CRCDataOut >> (8*(3-SendMACCurrent.WordA.ByteCount));
            end
            else
            begin
                SendHeader = 1;
                SendDataWord = SendMACCurrent.WordA.Data;
                SendMACNext.SendingWord = ~SendMACCurrent.WordA.Last;
                SendMACNext.SendingWordToZeroTerminate = SendMACCurrent.WordA.Last;
                SendMACNext.TerminateWord = CRCDataOut;
            end
            CRCReset = SendMACCurrent.WordA.Last;
            SendMACNext.WordA = SendMACNext.WordB;
            SendMACNext.WordB = 0;
        end
        if (SendMACCurrent.SendingWordToZeroTerminate)
        begin
            SendDataWord = SendMACCurrent.TerminateWord;
            SendMACNext.SendingWordToZeroTerminate = 0;
            SendMACNext.SendingTerminateWord1 = 1;
            SendMACNext.TerminateWord = TERMINATE_CODES[0];
        end
        if (SendMACCurrent.SendingWord)
        begin
            SendMACNext.SendingWord = 0;
            if (SendMACCurrent.WordA.Last)
            begin
                SendDataWord = {
                    ({8{SendMACCurrent.WordA.ByteCount == 4}} & SendMACCurrent.WordA.Data[31:24]),
                    ({8{SendMACCurrent.WordA.ByteCount > 2}} & SendMACCurrent.WordA.Data[23:16]),
                    ({8{SendMACCurrent.WordA.ByteCount > 1}} & SendMACCurrent.WordA.Data[15:8]),
                    SendMACCurrent.WordA.Data[7:0]
                } | (CRCDataOut << (8*SendMACCurrent.WordA.ByteCount));
                SendMACNext.SendingTerminateWord1 = 1;
                SendMACNext.TerminateWord = {
                    CRCDataOut >> (8*(4-SendMACCurrent.WordA.ByteCount)),
                    TERMINATE_CODES[SendMACCurrent.WordA.ByteCount]};
                CRCReset = 1;
            end
            else
            begin
                SendDataWord = SendMACCurrent.WordA.Data;
                SendMACNext.SendingHeaderAndWord = 1;
            end
            SendMACNext.WordA = SendMACNext.WordB;
            SendMACNext.WordB = 0;
        end
        if (SendMACCurrent.SendingTerminateWord1)
        begin
            SendHeader = 2;
            SendDataWord = SendMACCurrent.TerminateWord;
            SendMACNext.SendingTerminateWord1 = 0;
            SendMACNext.SendingTerminateWord2 = 1;
            SendMACNext.TerminateWord = SendMACNext.TerminateWord[39:32];
        end
        if (SendMACCurrent.SendingTerminateWord2)
        begin
            SendDataWord = SendMACCurrent.TerminateWord;
            SendMACNext.SendingTerminateWord2 = 0;
            SendMACNext.InterframeGap = 1;
        end
        if (SendMACCurrent.InterframeGap[0])
        begin
            SendHeader = 2;
            SendDataWord = 32'h1e;
            SendMACNext.InterframeGap[1:0] = 2;
        end
        if (SendMACCurrent.InterframeGap[1])
        begin
            SendMACNext.InterframeGap[2:1] = 2;
        end
        if (SendMACCurrent.InterframeGap[2])
        begin
            SendHeader = 2;
            SendDataWord = 32'h1e;
            SendMACNext.InterframeGap[3:2] = 2;
        end
        if (SendMACCurrent.InterframeGap[3])
        begin
            SendMACNext.Idle = 1;
            SendMACNext.InterframeGap[3] = 0;
        end
    end
    if ({SendMACNext.WordA.ByteCount, SendMACNext.WordA.Last} == 0)
    begin
        SendReady = 1;
        SendMACNext.WordA = SendWord;
        SendMACNext.CRCAdvance = SendWord.ByteCount;
    end
    else
    begin
        if (~SendMACNext.WordA.Last & {SendMACNext.WordB.ByteCount, SendMACNext.WordB.Last} == 0)
        begin
            SendReady = 1;
            SendMACNext.WordB = SendWord;
            SendMACNext.CRCAdvance = SendWord.ByteCount;
        end
    end
end

always_ff @(posedge SendClock)
begin
    SendMACCurrent <= SendReset ? SendMACInitial : SendMACNext;
end

endmodule

`default_nettype wire
