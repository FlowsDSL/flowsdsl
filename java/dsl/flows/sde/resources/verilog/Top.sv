// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

`default_nettype none

import Types::*;

module Top (
    input wire [15:0] pci_exp_rxn,
    input wire [15:0] pci_exp_rxp,
    output wire [15:0] pci_exp_txn,
    output wire [15:0] pci_exp_txp,
    input wire pcie_mgt_clk_n,
    input wire pcie_mgt_clk_p,
    input wire pcie_perstn,
    input wire[1:0] satellite_gpio,
    input wire satellite_uart_rxd,
    output wire satellite_uart_txd,
    output wire hbm_cattrip,
    input wire SYSCLK2_P,
    input wire SYSCLK2_N,
    input wire SYNCE_CLK_P,
    input wire SYNCE_CLK_N,
    input wire[3:0] gt_rxp_in,
    input wire[3:0] gt_rxn_in,
    output wire[3:0] gt_txp_out,
    output wire[3:0] gt_txn_out);

typedef struct packed {
    bit PathActive;
    bit WriteEnable;
    ToCoreWord StreamIn;
    bit [10:2] WritePointer;
    bit [24:0] CyclesSinceLastWrite;
} ToCoreState;

typedef struct packed {
    bit [30:0] Value;
} RQWord;

typedef struct packed {
    bit PathActive;
    bit [63:20] WriteBase;
    bit [20:6] WritePointer;
    bit [20:6] WriteLimit;
    RQWord [31:0] WriteBufferWords;
    bit [4:0] WriteBufferLength;
    bit [4:0] WriteBufferTimer;
} FromCoreState;

typedef struct packed {
    bit[7:0] SteeringTag;
    bit[1:0] ProcessingHint;
    bit Present;
} TLPProcessingHintsStruct;

typedef struct packed {
    ReadCompletionStruct ReadCompletionField;
    TLPProcessingHintsStruct TLPProcessingHintsField;
    bit[3:0] FirstByteEnable;
    bit[3:0] LastByteEnable;
    bit[127:0] RequestDescriptor;
} ReadRejection;

typedef struct packed {
    ToCoreState ToCore;
    FromCoreState FromCore;
    bit CCArbiter;
} PCIEState;

PCIEState PCIECurrent, PCIENext;

assign hbm_cattrip = 0;
wire sys_clk;
wire sys_clk_gt;
wire PCIEClock;
wire PCIEReset;
PCIEProducer PCIECQ;
PCIEProducer PCIECC;
PCIEProducer PCIERQ;
wire[3:0] PCIECCReady;
wire[3:0] PCIERQReady;
wire AXIReadyForRequest;

PCIEBlock PCIEInstance (
    .pci_exp_rxn(pci_exp_rxn),
    .pci_exp_rxp(pci_exp_rxp),
    .pci_exp_txn(pci_exp_txn),
    .pci_exp_txp(pci_exp_txp),
    .sys_reset(pcie_perstn),
    .sys_clk(sys_clk),
    .sys_clk_gt(sys_clk_gt),
    .user_clk(PCIEClock),
    .user_reset(PCIEReset),
    .m_axis_cq_tready(AXIReadyForRequest),
    .m_axis_cq_tdata(PCIECQ.Data),
    .m_axis_cq_tuser(PCIECQ.User),
    .m_axis_cq_tlast(PCIECQ.Last),
    .m_axis_cq_tvalid(PCIECQ.Valid),
    .m_axis_rc_tready(1),
    .s_axis_cc_tready(PCIECCReady),
    .s_axis_cc_tdata(PCIECC.Data),
    .s_axis_cc_tkeep(PCIECC.Keep),
    .s_axis_cc_tlast(PCIECC.Last),
    .s_axis_cc_tuser(PCIECC.User),
    .s_axis_cc_tvalid(PCIECC.Valid),
    .s_axis_rq_tdata(PCIERQ.Data),
    .s_axis_rq_tkeep(PCIERQ.Keep),
    .s_axis_rq_tlast(PCIECurrent.FromCore.WriteBufferTimer == 31),
    .s_axis_rq_tuser(PCIERQ.User),
    .s_axis_rq_tvalid(PCIERQ.Valid),
    .s_axis_rq_tready(PCIERQReady),
    .cfg_interrupt_pending(0),
    .cfg_interrupt_int(0));

UtilityBufferBlock UtilityBufferInstance (
    .IBUF_DS_N(pcie_mgt_clk_n),
    .IBUF_DS_P(pcie_mgt_clk_p),
    .IBUF_OUT(sys_clk_gt),
    .IBUF_DS_ODIV2(sys_clk));

wire ReferenceClock100MhzIBUFToBUFG;
wire ReferenceClock100MHz;
IBUFDS ReferenceClock100MHzIBUF (.I(SYSCLK2_P), .IB(SYSCLK2_N), .O(ReferenceClock100MhzIBUFToBUFG));
BUFG ReferenceClock100MHzBUFG (.I(ReferenceClock100MhzIBUFToBUFG), .O(ReferenceClock100MHz));
wire ClockWizardClockIn;
wire ClockWizardLocked;
wire Clock50MHz;
wire Reset50MHzNegative;

ProcessorSystemResetBlock ProcessorSystemResetInstance (
    .slowest_sync_clk(Clock50MHz), .ext_reset_in(~PCIEReset), .aux_reset_in(1), .mb_debug_sys_rst(0), .dcm_locked(ClockWizardLocked),
    .peripheral_aresetn(Reset50MHzNegative));

ClockWizardBlock50MHz Clock50MHzWizardInstance (
    .resetn(pcie_perstn), .clk_in1(ReferenceClock100MHz), .clk_out1(Clock50MHz), .locked(ClockWizardLocked));

wire Reset50MHz = ~Reset50MHzNegative;

wire NetworkReceiveClock0;
wire NetworkReceiveReset0;
Network32Word NetworkReceive0;
Network32Word NetworkReceive0Swapped;
wire NetworkSendClock0;
wire NetworkSendReset0;
wire NetworkReceiveClock1;
wire NetworkReceiveReset1;
wire NetworkReceiveClock2;
wire NetworkReceiveReset2;
wire NetworkReceiveClock3;
wire NetworkReceiveReset3;
wire NetworkSendClock1;
wire NetworkSendReset1;
wire NetworkSendClock2;
wire NetworkSendReset2;
wire NetworkSendClock3;
wire NetworkSendReset3;
wire NetworkSendReady0;
Network32Word NetworkSend0;
Network32Word NetworkSend0Swapped;

logic AXIRequestValid;
AXIRequest AXIRequestData;
ReadCompletionStruct AXIRequestReadCompletion;
ReadRejection HostToCardReadRejectionsDataIn;
ReadRejection HostToCardReadRejectionsDataOut;
wire CCResponseValid[2];
AXIResponse AXIResponseData;
ReadCompletionStruct AXIResponseReadCompletion;

always_comb
begin
    AXIRequestReadCompletion.Attributes = PCIECQ.Data[126:124];
    AXIRequestReadCompletion.TransactionClass = PCIECQ.Data[123:121];
    AXIRequestReadCompletion.RequesterID = PCIECQ.Data[95:80];
    AXIRequestReadCompletion.Tag = PCIECQ.Data[103:96];
    AXIRequestReadCompletion.AddressType = PCIECQ.Data[1:0];
    AXIRequestReadCompletion.AddressLow7[6:2] = PCIECQ.Data[6:2];
    AXIRequestReadCompletion.AddressLow7[1:0] = PCIECQ.User[0] ? 0 : (PCIECQ.User[1] ? 1 : (PCIECQ.User[2] ? 2 : (PCIECQ.User[3] ? 3 : 0)));
    HostToCardReadRejectionsDataIn.ReadCompletionField = AXIRequestReadCompletion;
    HostToCardReadRejectionsDataIn.TLPProcessingHintsField.SteeringTag = PCIECQ.User[110:103];
    HostToCardReadRejectionsDataIn.TLPProcessingHintsField.ProcessingHint = PCIECQ.User[100:99];
    HostToCardReadRejectionsDataIn.TLPProcessingHintsField.Present = PCIECQ.User[97];
    HostToCardReadRejectionsDataIn.FirstByteEnable = PCIECQ.User[3:0];
    HostToCardReadRejectionsDataIn.LastByteEnable = PCIECQ.User[11:8];
    HostToCardReadRejectionsDataIn.RequestDescriptor = PCIECQ.Data[127:0];
    AXIRequestValid = 0;
    AXIRequestData = 0;
    AXIRequestData.Address = PCIECQ.Data[17:2];
    AXIRequestData.Length = PCIECQ.Data[65:64];
    AXIRequestData.OperationIsWrite = PCIECQ.Data[75];
    AXIRequestData.OperationDetails.WriteDataField.WriteData = PCIECQ.Data[223:128];
    AXIRequestData.OperationDetails.WriteDataField.FirstByteEnable = PCIECQ.User[3:0];
    AXIRequestData.OperationDetails.WriteDataField.LastByteEnable = PCIECQ.User[11:8];
    if (PCIECQ.Data[75] == 0)
    begin
        AXIRequestData.OperationDetails.PaddedReadCompletionField.ReadCompletionField = AXIRequestReadCompletion;
    end
    if (PCIECQ.Valid & AXIReadyForRequest)
    begin
        AXIRequestValid = PCIECQ.User[80] & (PCIECQ.Data[74:66] == 0) & (PCIECQ.Data[114:112] == 0) & (PCIECQ.Data[78:76] == 0);
    end
    AXIResponseReadCompletion = PCIECurrent.CCArbiter ? HostToCardReadRejectionsDataOut.ReadCompletionField : AXIResponseData.ReadCompletionField;
    PCIECC.Data[511:256] = 0;
    if (PCIECurrent.CCArbiter == 0)
    begin
        PCIECC.Data[255:192] = 0;
        PCIECC.Data[191:96] = AXIResponseData.ReadData;
    end
    else
    begin
        PCIECC.Data[255:128] = HostToCardReadRejectionsDataOut.RequestDescriptor;
        PCIECC.Data[127:96] = {8'b0, HostToCardReadRejectionsDataOut.TLPProcessingHintsField.SteeringTag, 5'b0, HostToCardReadRejectionsDataOut.TLPProcessingHintsField.ProcessingHint,
            HostToCardReadRejectionsDataOut.TLPProcessingHintsField.Present, HostToCardReadRejectionsDataOut.LastByteEnable, HostToCardReadRejectionsDataOut.FirstByteEnable};
    end
    PCIECC.Data[95] = 0;
    PCIECC.Data[94:92] = AXIResponseReadCompletion.Attributes;
    PCIECC.Data[91:89] = AXIResponseReadCompletion.TransactionClass;
    PCIECC.Data[88:72] = 0;
    PCIECC.Data[71:64] = AXIResponseReadCompletion.Tag;
    PCIECC.Data[63:48] = AXIResponseReadCompletion.RequesterID;
    PCIECC.Data[47:44] = 0;
    PCIECC.Data[43] = PCIECurrent.CCArbiter;
    PCIECC.Data[42:32] = PCIECurrent.CCArbiter ? 0 : AXIResponseData.Length;
    PCIECC.Data[31:29] = 0;
    PCIECC.Data[28:16] = PCIECurrent.CCArbiter ? 4 : AXIResponseData.TotalBytes;
    PCIECC.Data[15:10] = 0;
    PCIECC.Data[9:8] = AXIResponseReadCompletion.AddressType;
    PCIECC.Data[7] = 0;
    PCIECC.Data[6:0] = AXIResponseReadCompletion.AddressLow7;
    PCIECC.User = 0;
    PCIECC.Last = 1;
    PCIECC.Keep[15:8] = 0;
    PCIECC.Keep[7] = PCIECurrent.CCArbiter;
    PCIECC.Keep[6] = PCIECurrent.CCArbiter;
    PCIECC.Keep[5] = PCIECurrent.CCArbiter | (AXIResponseData.Length >= 3);
    PCIECC.Keep[4] = PCIECurrent.CCArbiter | (AXIResponseData.Length >= 2);
    PCIECC.Keep[3] = PCIECurrent.CCArbiter | (AXIResponseData.Length >= 1);
    PCIECC.Keep[2:0] = 7;
    PCIECC.Valid = CCResponseValid[PCIECurrent.CCArbiter];
end

FlashProgrammerBridge FlashProgrammerBridgeInstance (
    .PCIEClock(PCIEClock), .PCIEReset(PCIEReset),
    .ReadyForRequest(AXIReadyForRequest), .RequestValid(AXIRequestValid), .RequestData(AXIRequestData),
    .ReadyForResponse((PCIECurrent.CCArbiter == 0) & PCIECCReady[0]), .ResponseValid(CCResponseValid[0]), .ResponseData(AXIResponseData),
    .Clock50MHz(Clock50MHz), .Reset50MHz(Reset50MHz));

xpm_fifo_sync #(.READ_MODE("fwft"), .WRITE_DATA_WIDTH($bits(ReadRejection)), .READ_DATA_WIDTH($bits(ReadRejection)), .FIFO_WRITE_DEPTH(16), .USE_ADV_FEATURES("1002")) HostToCardReadRejections (
    .wr_clk(PCIEClock), .rst(PCIEReset), .wr_en(PCIECQ.Valid & PCIECQ.User[80] & (PCIECQ.Data[114:112] == 1) & (PCIECQ.Data[78:75] == 0) & AXIReadyForRequest), .din(HostToCardReadRejectionsDataIn),
    .rd_en(PCIECCReady[0] & PCIECurrent.CCArbiter), .data_valid(CCResponseValid[1]), .dout(HostToCardReadRejectionsDataOut));

wire ToCoreAlmostFull;
wire ToCoreFIFOValid;
wire ToCoreReadResetBusy;
ToCoreWord ToCoreFIFOOut;
xpm_fifo_async #(.READ_MODE("fwft"), .WRITE_DATA_WIDTH($bits(ToCoreWord)), .READ_DATA_WIDTH ($bits(ToCoreWord)), .FIFO_WRITE_DEPTH(1024), .PROG_FULL_THRESH(1000), .USE_ADV_FEATURES("1002")) ToCoreQueue (
    .rst(PCIEReset),
    .wr_clk(PCIEClock), .wr_en(PCIECurrent.ToCore.WriteEnable), .din(PCIECurrent.ToCore.StreamIn), .prog_full(ToCoreAlmostFull),
    .rd_clk(NetworkSendClock0), .data_valid(ToCoreFIFOValid), .rd_en(~ToCoreReadResetBusy), .rd_rst_busy(ToCoreReadResetBusy), .dout(ToCoreFIFOOut));
wire FromCoreWriteEnable;
FromCoreWord FromCoreFIFOIn;
wire FromCoreQueueAlmostFull;
wire FromCoreDataValid;
wire FromCoreReadResetBusy;
logic FromCoreDataReady;
FromCoreWord FromCoreFIFOOut;
bit FromCoreWriteEnableRegister;
FromCoreWord FromCoreFIFOInRegister;
xpm_fifo_async #(.READ_MODE("fwft"), .WRITE_DATA_WIDTH($bits(FromCoreWord)), .READ_DATA_WIDTH ($bits(FromCoreWord)), .FIFO_WRITE_DEPTH(8192), .PROG_FULL_THRESH(8180), .USE_ADV_FEATURES("1002")) FromCoreQueue (
    .rst(NetworkSendReset0),
    .wr_clk(NetworkSendClock0), .wr_en(FromCoreWriteEnableRegister), .din(FromCoreFIFOInRegister), .prog_full(FromCoreQueueAlmostFull),
    .rd_clk(PCIEClock), .rd_en(~FromCoreReadResetBusy & FromCoreDataReady), .data_valid(FromCoreDataValid), .rd_rst_busy(FromCoreReadResetBusy), .dout(FromCoreFIFOOut));

always_ff @(posedge PCIEClock)
begin
    PCIECurrent <= PCIEReset ? 0 : PCIENext;
end

always_ff @(posedge NetworkSendClock0)
begin
    FromCoreWriteEnableRegister <= NetworkSendReset0 ? 0 : FromCoreWriteEnable;
    FromCoreFIFOInRegister <= NetworkSendReset0 ? 0 : FromCoreFIFOIn;
end

logic FromCoreOverflow;
RQWord BLANK;
assign BLANK.Value = BlankValue;

always_comb
begin
    PCIENext = PCIECurrent;
    PCIENext.ToCore.WriteEnable = 0;
    PCIENext.ToCore.StreamIn.Stop = 0;
    PCIENext.ToCore.StreamIn.Data = PCIECQ.Data[159:128];
    if (PCIECQ.Valid & PCIECQ.User[80] & (PCIECQ.Data[78:75] == 1) & (PCIECQ.Data[114:112] == 1) & (PCIECQ.Data[74:64] == 1) & (PCIECQ.User[3:0] == 4'hf) & AXIReadyForRequest)
    begin
        PCIENext.ToCore.CyclesSinceLastWrite = 0;
        if (PCIECQ.Data[11] == 1)
        begin
            if (PCIECQ.Data[10:2] == 0)
            begin
                PCIENext.FromCore.WriteBase[31:20] = PCIECQ.Data[159:148];
            end
            if (PCIECQ.Data[10:2] == 1)
            begin
                PCIENext.FromCore.WriteBase[63:32] = PCIECQ.Data[159:128];
                PCIENext.ToCore.PathActive = 1;
                PCIENext.ToCore.WritePointer = 0;
                PCIENext.FromCore.PathActive = 1;
                PCIENext.FromCore.WritePointer = 0;
                PCIENext.FromCore.WriteBufferWords[0].Value = InterfaceDigestA;
                PCIENext.FromCore.WriteBufferWords[1].Value = InterfaceDigestB;
                PCIENext.FromCore.WriteBufferLength = 2;
            end
            if (PCIECQ.Data[10:2] == 2)
            begin
                PCIENext.FromCore.WriteLimit = PCIECQ.Data[148:134];
            end
        end
        else
        begin
            PCIENext.ToCore.WriteEnable = PCIECurrent.ToCore.PathActive;
            if (PCIECQ.Data[10:2] == PCIECurrent.ToCore.WritePointer)
            begin
                if (ToCoreAlmostFull)
                begin
                    PCIENext.ToCore.StreamIn.Stop = 1;
                    PCIENext.ToCore.StreamIn.Data = ErrorToCoreQueueOverflow;
                    PCIENext.ToCore.PathActive = 0;
                end
                else
                begin
                    PCIENext.ToCore.WritePointer = PCIECurrent.ToCore.WritePointer+1;
                end
            end
            else
            begin
                PCIENext.ToCore.StreamIn.Stop = 1;
                PCIENext.ToCore.StreamIn.Data = ErrorWriteAddressMismatch;
                PCIENext.ToCore.PathActive = 0;
            end
        end
    end
    else
    begin
        PCIENext.ToCore.CyclesSinceLastWrite = PCIECurrent.ToCore.CyclesSinceLastWrite + 1;
        if (PCIENext.ToCore.CyclesSinceLastWrite == 0)
        begin
            PCIENext.ToCore.WriteEnable = PCIECurrent.ToCore.PathActive;
            PCIENext.ToCore.StreamIn.Stop = 1;
            PCIENext.ToCore.StreamIn.Data = ErrorHeartbeatTimeout;
            PCIENext.ToCore.PathActive = 0;
        end
    end
    FromCoreDataReady = PCIECurrent.FromCore.WriteBufferTimer < 30;
    FromCoreOverflow = PCIECurrent.FromCore.WritePointer == PCIECurrent.FromCore.WriteLimit;
    if (FromCoreDataReady)
    begin
        if (PCIECurrent.FromCore.PathActive & (FromCoreOverflow | FromCoreDataValid))
        begin
            PCIENext.FromCore.WriteBufferWords[PCIECurrent.FromCore.WriteBufferLength].Value = FromCoreOverflow ? ErrorReportQueueOverflow : FromCoreFIFOOut.WordA;
            PCIENext.FromCore.WriteBufferWords[PCIECurrent.FromCore.WriteBufferLength+1].Value = FromCoreFIFOOut.WordB;
            PCIENext.FromCore.WriteBufferLength = PCIECurrent.FromCore.WriteBufferLength + ((FromCoreOverflow | (~FromCoreFIFOOut.ValidB)) ? 1 : 2);
        end
        if (PCIENext.FromCore.WriteBufferLength >= 16)
        begin
            PCIENext.FromCore.WriteBufferTimer = 30;
        end
        else if (PCIENext.FromCore.WriteBufferLength != 0)
        begin
            PCIENext.FromCore.WriteBufferTimer = PCIECurrent.FromCore.WriteBufferTimer + 1;
        end
        if (FromCoreOverflow | (FromCoreDataValid & FromCoreFIFOOut.Stop))
        begin
            PCIENext.FromCore.PathActive = 0;
        end
    end
    else if (PCIERQReady[0])
    begin
        PCIENext.FromCore.WriteBufferTimer = PCIECurrent.FromCore.WriteBufferTimer + 1;
        if (PCIECurrent.FromCore.WriteBufferTimer == 31)
        begin
            PCIENext.FromCore.WriteBufferWords[0] = PCIECurrent.FromCore.WriteBufferWords[16];
            PCIENext.FromCore.WriteBufferLength = (PCIECurrent.FromCore.WriteBufferLength == 17 ? 1 : 0);
            PCIENext.FromCore.WritePointer = PCIECurrent.FromCore.WritePointer+1;
        end
    end
    PCIERQ.Valid = PCIECurrent.FromCore.WriteBufferTimer >= 30;
    PCIERQ.Data = 0;
    if (PCIECurrent.FromCore.WriteBufferTimer == 30)
    begin
        PCIERQ.Data[511] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[510:480] = PCIECurrent.FromCore.WriteBufferLength > 11 ? PCIECurrent.FromCore.WriteBufferWords[11] : BLANK;
        PCIERQ.Data[479] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[478:448] = PCIECurrent.FromCore.WriteBufferLength > 10 ? PCIECurrent.FromCore.WriteBufferWords[10] : BLANK;
        PCIERQ.Data[447] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[446:416] = PCIECurrent.FromCore.WriteBufferLength > 9 ? PCIECurrent.FromCore.WriteBufferWords[9] : BLANK;
        PCIERQ.Data[415] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[414:384] = PCIECurrent.FromCore.WriteBufferLength > 8 ? PCIECurrent.FromCore.WriteBufferWords[8] : BLANK;
        PCIERQ.Data[383] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[382:352] = PCIECurrent.FromCore.WriteBufferLength > 7 ? PCIECurrent.FromCore.WriteBufferWords[7] : BLANK;
        PCIERQ.Data[351] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[350:320] = PCIECurrent.FromCore.WriteBufferLength > 6 ? PCIECurrent.FromCore.WriteBufferWords[6] : BLANK;
        PCIERQ.Data[319] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[318:288] = PCIECurrent.FromCore.WriteBufferLength > 5 ? PCIECurrent.FromCore.WriteBufferWords[5] : BLANK;
        PCIERQ.Data[287] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[286:256] = PCIECurrent.FromCore.WriteBufferLength > 4 ? PCIECurrent.FromCore.WriteBufferWords[4] : BLANK;
        PCIERQ.Data[255] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[254:224] = PCIECurrent.FromCore.WriteBufferLength > 3 ? PCIECurrent.FromCore.WriteBufferWords[3] : BLANK;
        PCIERQ.Data[223] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[222:192] = PCIECurrent.FromCore.WriteBufferLength > 2 ? PCIECurrent.FromCore.WriteBufferWords[2] : BLANK;
        PCIERQ.Data[191] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[190:160] = PCIECurrent.FromCore.WriteBufferLength > 1 ? PCIECurrent.FromCore.WriteBufferWords[1] : BLANK;
        PCIERQ.Data[159] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[158:128] = PCIECurrent.FromCore.WriteBufferWords[0];
        PCIERQ.Data[127:79] = 0;
        PCIERQ.Data[78:75] = 1;
        PCIERQ.Data[74:64] = 11'h10;
        PCIERQ.Data[63:20] = PCIECurrent.FromCore.WriteBase;
        PCIERQ.Data[19:6] = PCIECurrent.FromCore.WritePointer;
        PCIERQ.Data[5:0] = 0;
        PCIERQ.User[182:12] = 0;
        PCIERQ.User[11:8] = 4'hf;
        PCIERQ.User[7:4] = 0;
        PCIERQ.User[3:0] = 4'hf;
    end else begin
        PCIERQ.Data[127] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[126:96] = PCIECurrent.FromCore.WriteBufferLength > 15 ? PCIECurrent.FromCore.WriteBufferWords[15] : BLANK;
        PCIERQ.Data[95] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[94:64] = PCIECurrent.FromCore.WriteBufferLength > 14 ? PCIECurrent.FromCore.WriteBufferWords[14] : BLANK;
        PCIERQ.Data[63] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[62:32] = PCIECurrent.FromCore.WriteBufferLength > 13 ? PCIECurrent.FromCore.WriteBufferWords[13] : BLANK;
        PCIERQ.Data[31] = PCIECurrent.FromCore.WritePointer[20];
        PCIERQ.Data[30:0] = PCIECurrent.FromCore.WriteBufferLength > 12 ? PCIECurrent.FromCore.WriteBufferWords[12] : BLANK;
        PCIERQ.User[182:0] = 0;
    end
    PCIERQ.Keep[15:0] = PCIECurrent.FromCore.WriteBufferTimer == 30 ? 16'hffff : 4'hf;
    PCIENext.CCArbiter = PCIECurrent.CCArbiter ^ PCIECCReady[0];
end

wire[31:0] UnscrambledRx;
wire[5:0] rxheader_out;
wire[1:0] rxdatavalid_out;
wire[1:0] rxheadervalid_out;

Core CoreInstance(
    .NetworkReceiveClock(NetworkReceiveClock0), .NetworkReceiveReset(NetworkReceiveReset0), .NetworkReceiveWord(NetworkReceive0),
    .NetworkSendClock(NetworkSendClock0), .NetworkSendReset(NetworkSendReset0), .NetworkSendReady(NetworkSendReady0), .NetworkSendWord(NetworkSend0),
    .ToCoreMessageValid(ToCoreFIFOValid), .ToCoreMessageWord(ToCoreFIFOOut),
    .FromCoreQueueAlmostFull(FromCoreQueueAlmostFull), .FromCoreMessageValid(FromCoreWriteEnable), .FromCoreMessageWord(FromCoreFIFOIn),
    .satellite_gpio(satellite_gpio), .satellite_uart_rxd(satellite_uart_rxd), .satellite_uart_txd(satellite_uart_txd),
    .UnscrambledRx(UnscrambledRx), .rxheader_out(rxheader_out[1:0]), .rxdatavalid_out(rxdatavalid_out[0]), .rxheadervalid_out(rxheadervalid_out[0]),
    .Clock50MHz(Clock50MHz), .Reset50MHz(Reset50MHz));

wire gtwiz_userclk_rx_usrclk2_out;
wire gtwiz_reset_rx_done_out;
wire gtwiz_userclk_tx_usrclk2_out;
wire gtwiz_reset_tx_done_out;
wire[127:0] gtwiz_userdata_rx_out;
wire[6:0] SendSequence;
wire ReceiveSlip;

wire SerializerDeserializerClock;

IBUFDS_GTE4 SerializerDeserializerClockBuffer (
    .I(SYNCE_CLK_P),
    .IB(SYNCE_CLK_N),
    .CEB(0),
    .O(SerializerDeserializerClock));

assign NetworkReceiveClock0 = gtwiz_userclk_rx_usrclk2_out;
assign NetworkReceiveReset0 = ~gtwiz_reset_rx_done_out;
assign NetworkSendClock0 = gtwiz_userclk_tx_usrclk2_out;
assign NetworkSendReset0 = ~gtwiz_reset_tx_done_out;

wire[5:0] TxHeader0;
wire[31:0] UnscrambledTx0;
wire[31:0] ScrambledTx0;

EthernetSerializerDeserializerBlock EthernetSerializerDeserializer (
    .gtwiz_userclk_tx_reset_in(0),
    .gtwiz_userclk_rx_reset_in(0),
    .gtwiz_reset_clk_freerun_in(Clock50MHz),
    .gtwiz_reset_all_in(Reset50MHz),
    .gtwiz_reset_tx_pll_and_datapath_in(0),
    .gtwiz_reset_tx_datapath_in(0),
    .gtwiz_reset_rx_pll_and_datapath_in(0),
    .gtwiz_reset_rx_datapath_in(0),
    .gtwiz_userdata_tx_in({4{ScrambledTx0}}),
    .gtrefclk00_in(SerializerDeserializerClock),
    .gtyrxn_in(gt_rxn_in),
    .gtyrxp_in(gt_rxp_in),
    .gtwiz_reset_rx_done_out(gtwiz_reset_rx_done_out),
    .gtwiz_userclk_rx_usrclk2_out(gtwiz_userclk_rx_usrclk2_out),
    .gtwiz_userdata_rx_out(gtwiz_userdata_rx_out),
    .gtwiz_reset_tx_done_out(gtwiz_reset_tx_done_out),
    .gtwiz_userclk_tx_usrclk2_out(gtwiz_userclk_tx_usrclk2_out),
    .rxdatavalid_out(rxdatavalid_out),
    .rxheadervalid_out(rxheadervalid_out),
    .rxgearboxslip_in(ReceiveSlip),
    .rxheader_out(rxheader_out),
    .txheader_in({4{TxHeader0}}),
    .txsequence_in({4{2'b0, SendSequence}}),
    .gtytxn_out(gt_txn_out),
    .gtytxp_out(gt_txp_out)
    );

ScramblerUnscrambler #(.UNSCRAMBLER(0)) EthernetScrambler (
    .Clock(gtwiz_userclk_tx_usrclk2_out),
    .Reset(~gtwiz_reset_tx_done_out),
    .Advance(SendSequence != 32),
    .DataIn(UnscrambledTx0),
    .DataOut(ScrambledTx0));

ScramblerUnscrambler #(.UNSCRAMBLER(1)) EthernetUnscrambler (
    .Clock(gtwiz_userclk_rx_usrclk2_out),
    .Reset(~gtwiz_reset_rx_done_out),
    .Advance(rxdatavalid_out[0]),
    .DataIn(gtwiz_userdata_rx_out[31:0]),
    .DataOut(UnscrambledRx));

assign NetworkSend0Swapped.Data[7:0] = NetworkSend0.Data[31:24];
assign NetworkSend0Swapped.Data[15:8] = NetworkSend0.Data[23:16];
assign NetworkSend0Swapped.Data[23:16] = NetworkSend0.Data[15:8];
assign NetworkSend0Swapped.Data[31:24] = NetworkSend0.Data[7:0];
assign NetworkSend0Swapped.ByteCount = NetworkSend0.ByteCount;
assign NetworkSend0Swapped.Last = NetworkSend0.Last;

assign NetworkReceive0.Data[7:0] = NetworkReceive0Swapped.Data[31:24];
assign NetworkReceive0.Data[15:8] = NetworkReceive0Swapped.Data[23:16];
assign NetworkReceive0.Data[23:16] = NetworkReceive0Swapped.Data[15:8];
assign NetworkReceive0.Data[31:24] = NetworkReceive0Swapped.Data[7:0];
assign NetworkReceive0.ByteCount = NetworkReceive0Swapped.ByteCount;
assign NetworkReceive0.Last = NetworkReceive0Swapped.Last;

EthernetPCSAndMAC EthernetPCSAndMACInstance (
    .SendClock(NetworkSendClock0),
    .SendReset(NetworkSendReset0),
    .SendHeader(TxHeader0),
    .SendSequenceOut(SendSequence),
    .SendReady(NetworkSendReady0),
    .SendDataWord(UnscrambledTx0),
    .SendWord(NetworkSend0Swapped),
    .ReceiveClock(NetworkReceiveClock0),
    .ReceiveReset(NetworkReceiveReset0),
    .ReceiveHeaderValid(rxheadervalid_out[0]),
    .ReceiveHeaderWord(rxheader_out[1:0]),
    .ReceiveDataValid(rxdatavalid_out[0]),
    .ReceiveDataWord(UnscrambledRx),
    .ReceiveWord(NetworkReceive0Swapped),
    .ReceiveSlip(ReceiveSlip));

endmodule

`default_nettype wire
