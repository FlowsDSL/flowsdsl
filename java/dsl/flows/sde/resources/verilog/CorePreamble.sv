`default_nettype none

import Types::*;

module Core (
    input wire NetworkReceiveClock, input wire NetworkReceiveReset, input Network32Word NetworkReceiveWord,
    input wire NetworkSendClock, input wire NetworkSendReset, input wire NetworkSendReady, output Network32Word NetworkSendWord,
    input wire ToCoreMessageValid, input ToCoreWord ToCoreMessageWord,
    input wire FromCoreQueueAlmostFull, output logic FromCoreMessageValid, output FromCoreWord FromCoreMessageWord,
    input wire[31:0] UnscrambledRx, input wire[1:0] rxheader_out, input wire rxdatavalid_out, input wire rxheadervalid_out,
    input wire[1:0] satellite_gpio, input wire satellite_uart_rxd, output wire satellite_uart_txd,
    input wire Clock50MHz, input wire Reset50MHz);

typedef struct packed {
  bit ToCoreMessageValid;
  ToCoreWord ToCoreMessageWord;
  bit[1:0] FromCoreQueueAlmostFull;
  bit FromCoreMessageValid;
  FromCoreWord FromCoreMessageWord;
  bit NetworkReceiveValid;
  bit[31:0] NetworkReceiveData;
  Network32Word NetworkReceiveWithoutFCS;
} NetworkSendState;
NetworkSendState NetworkSendCurrent, NetworkSendNext;

wire NetworkReceiveToSendFIFODataValid;
Network32Word NetworkReceiveToSendFIFODataOut;
wire NetworkReceiveToSendFIFOReadResetBusy;
xpm_fifo_async #(.READ_MODE("fwft"), .WRITE_DATA_WIDTH($bits(Network32Word)), .READ_DATA_WIDTH ($bits(Network32Word)), .FIFO_WRITE_DEPTH(16), .USE_ADV_FEATURES("1002")) NetworkReceiveToSendQueue (
    .rst(NetworkReceiveReset),
    .wr_clk(NetworkReceiveClock), .wr_en((NetworkReceiveWord.ByteCount != 0) | NetworkReceiveWord.Last), .din(NetworkReceiveWord),
    .rd_clk(NetworkSendClock), .data_valid(NetworkReceiveToSendFIFODataValid), .rd_en(~NetworkReceiveToSendFIFOReadResetBusy), .rd_rst_busy(NetworkReceiveToSendFIFOReadResetBusy), .dout(NetworkReceiveToSendFIFODataOut));

wire ErrorReset;
wire HostMessageDispatcherFromCoreMessageValid;
FromCoreWord HostMessageDispatcherFromCoreMessageWord;

assign FromCoreMessageValid = NetworkSendCurrent.FromCoreMessageValid;
assign FromCoreMessageWord = NetworkSendCurrent.FromCoreMessageWord;

Network32Word NetworkReceiveWithoutFCS;

always_comb
begin
  NetworkSendNext = NetworkSendCurrent;
  NetworkSendNext.ToCoreMessageValid = ToCoreMessageValid;
  NetworkSendNext.ToCoreMessageWord = ToCoreMessageWord;
  NetworkSendNext.FromCoreQueueAlmostFull = {NetworkSendCurrent.FromCoreQueueAlmostFull[0], FromCoreQueueAlmostFull};
  NetworkSendNext.FromCoreMessageValid = HostMessageDispatcherFromCoreMessageValid;
  NetworkSendNext.FromCoreMessageWord = HostMessageDispatcherFromCoreMessageWord;
  NetworkReceiveWithoutFCS = NetworkSendCurrent.NetworkReceiveWithoutFCS;
  NetworkSendNext.NetworkReceiveWithoutFCS = 0;
  if (NetworkReceiveToSendFIFODataValid)
  begin
    NetworkSendNext.NetworkReceiveValid = !NetworkReceiveToSendFIFODataOut.Last;
    NetworkSendNext.NetworkReceiveData = NetworkReceiveToSendFIFODataOut.Data;
    if (NetworkSendCurrent.NetworkReceiveValid)
    begin
      NetworkSendNext.NetworkReceiveWithoutFCS.Data = NetworkSendCurrent.NetworkReceiveData;
      NetworkSendNext.NetworkReceiveWithoutFCS.ByteCount = NetworkReceiveToSendFIFODataOut.ByteCount;
      NetworkSendNext.NetworkReceiveWithoutFCS.Last = NetworkReceiveToSendFIFODataOut.Last;
    end
  end
end

always_ff @(posedge NetworkSendClock)
begin
  NetworkSendCurrent <= NetworkSendReset ? 0 : NetworkSendNext;
end

// the rest, including endmodule, appended by generator
