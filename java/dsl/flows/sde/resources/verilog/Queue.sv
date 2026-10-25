// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

`default_nettype none

module Queue
    #(
        parameter ELEMENT_SIZE,
        parameter ADDRESS_WIDTH = 10
    )

    (
        input wire Clock,
        input wire Reset,
        output wire Ready,
        input wire[ELEMENT_SIZE-1:0] PushElement,
        input wire PushValid,
        output wire HasElement,
        output wire[ELEMENT_SIZE-1:0] Front,
        input wire Pop,
        output bit Error
    );

typedef struct packed {
    bit[ADDRESS_WIDTH-1:0] ReadPointer;
    bit[ADDRESS_WIDTH-1:0] WritePointer;
    bit[ELEMENT_SIZE-1:0] ReadData;
    bit[ELEMENT_SIZE-1:0] ElementA;
    bit ValidA;
    bit[ELEMENT_SIZE-1:0] ElementB;
    bit ValidB;
    bit[ELEMENT_SIZE-1:0] ElementC;
    bit ValidC;
    bit[1:0] RequestedRead;
    bit Ready;
} State;

State Current, Next, Initial;
logic ReadEnable;
logic WriteEnable;
logic[ELEMENT_SIZE-1:0] ReadData;
logic[ADDRESS_WIDTH-1:0] QueueSize;

assign Ready = Current.Ready;
assign QueueSize = Current.WritePointer-Current.ReadPointer;

BlockRam #(.DATA_WIDTH(ELEMENT_SIZE), .ADDRESS_WIDTH(ADDRESS_WIDTH)) Storage (.Clock(Clock),
    .ReadEnable(ReadEnable), .ReadAddress(Current.ReadPointer), .ReadData(ReadData),
    .WriteEnable(WriteEnable), .WriteAddress(Current.WritePointer), .WriteData(PushElement));

always_comb
begin
    Initial = 0;
    Initial.Ready = 1;
    Next = Current;
    Next.ReadData = ReadData;
    Next.Ready = QueueSize < ADDRESS_WIDTH'(-4);
    WriteEnable = 0;
    Error = 0;
    if (Pop)
    begin
        if (!Next.ValidA)
        begin
            Error = 1;
        end
        Next.ElementA = Next.ElementB;
        Next.ElementB = Next.ElementC;
        Next.ValidA = Next.ValidB;
        Next.ValidB = Next.ValidC;
        Next.ValidC = 0;
    end
    if (Current.RequestedRead[1])
    begin
        if (!Next.ValidA)
        begin
            Next.ElementA = Current.ReadData;
            Next.ValidA = 1;
        end
        else if (!Next.ValidB)
        begin
            Next.ElementB = Current.ReadData;
            Next.ValidB = 1;
        end
        else
        begin
            Next.ElementC = Current.ReadData;
            Next.ValidC = 1;
        end
    end
    ReadEnable = (Current.ReadPointer != Current.WritePointer) & (!Next.ValidB | (!Current.RequestedRead[0] & !Next.ValidC));
    if (PushValid)
    begin
        if (QueueSize != ADDRESS_WIDTH'(-1))
        begin
            if (Current.ReadPointer != Current.WritePointer || Current.RequestedRead[0])
            begin
                WriteEnable = 1;
            end
            else
            begin
                if (!Next.ValidA)
                begin
                    Next.ElementA = PushElement;
                    Next.ValidA = 1;
                end
                else if (!Next.ValidB)
                begin
                    Next.ElementB = PushElement;
                    Next.ValidB = 1;
                end
                else if (!Next.ValidC)
                begin
                    Next.ElementC = PushElement;
                    Next.ValidC = 1;
                end
                else
                begin
                    WriteEnable = 1;
                end
            end
        end
        else
        begin
            Error = 1;
        end
    end
    Next.RequestedRead = {Next.RequestedRead[0], ReadEnable};
    Next.ReadPointer = Current.ReadPointer + ReadEnable;
    Next.WritePointer = Current.WritePointer + WriteEnable;
end
assign HasElement = Current.ValidA;
assign Front = Current.ElementA;

always_ff @(posedge Clock)
begin
    Current <= Reset ? Initial : Next;
end

endmodule

`default_nettype wire
