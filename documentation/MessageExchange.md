# Software-hardware co-design

This page demonstrates a Flows program integrating with host software to form a co-designed system. Basic plumbing (PCIe, host ↔ card communication protocol,
serializers, kernel driver) is taken care of by Flows runtime.

## Flows side

```
  1  struct MultiplyRequest { A: Bit[32]; B: Bit[32]; }
  2  struct GCDRequest { A: Bit[32]; B: Bit[32]; }
  3  oneof CalculatorRequest { MultiplyRequest, GCDRequest }
  4  struct CalculatorResult { Value: Bit[32]; }
  5  oneof BlankOrCalculatorResult { Blank, CalculatorResult }
  6  
  7  transform CalculatorTransform (Request: CalculatorRequest, Response: BlankOrCalculatorResult) {
  8      while (true) {
  9          if (RequestReader is CalculatorRequest) {
 10              RequestPop = 1;
 11              if (RequestReader.CalculatorRequest is MultiplyRequest) {
 12                  Bit[32] Product = 0;
 13                  Bit[32] MultiplyA = RequestReader.CalculatorRequest.MultiplyRequest.A;
 14                  Bit[32] MultiplyB = RequestReader.CalculatorRequest.MultiplyRequest.B;
 15                  step;
 16                  while (MultiplyB != 0) {
 17                      if (Slice(whole: MultiplyB, offsetInBits: 0, countInBits: 1) == 1) {
 18                          Product = Product + MultiplyA;
 19                      }
 20                      MultiplyA = ShiftLeft(what: MultiplyA, howManyBits: 1);
 21                      MultiplyB = ShiftRight(what: MultiplyB, howManyBits: 1);
 22                      step;
 23                  }
 24                  Response = CalculatorResult{Value: Product};
 25              } else {
 26                  if (RequestReader.CalculatorRequest is GCDRequest) {
 27                      Bit[32] GCDA = RequestReader.CalculatorRequest.GCDRequest.A;
 28                      Bit[32] GCDB = RequestReader.CalculatorRequest.GCDRequest.B;
 29                      if (GCDA != 0 && GCDB != 0) {
 30                          step;
 31                          while (GCDA != GCDB) {
 32                              if (GCDA > GCDB) {
 33                                  GCDA = GCDA-GCDB;
 34                              } else {
 35                                  GCDB = GCDB-GCDA;
 36                              }
 37                              step;
 38                          }
 39                      }
 40                      Response = CalculatorResult{Value: GCDA | GCDB};
 41                  }
 42              }
 43          }
 44          step;
 45      }
 46  }
 47  
 48  system {
 49      instance Calculator: CalculatorTransform;
 50      queue HostMessageRouter.ToCalculator -> Calculator.Request;
 51      wire Calculator.Response -> HostMessageRouter.FromCalculator;
 52  }
 53  
```

## C++ side

```
#include "fpga.h"

struct PrintingListener : fpga::FpgaIO::Listener {
    void HandleError(fpga::FpgaIO::Error error) override {
        std::string description{fpga::FpgaIO::GetErrorDescription(error)};
        printf("Received error: %s\n", description.c_str());
    }

    void ReceiveMessageFromCalculator(const fpga::CalculatorResult& result) override {
        printf("Received result: %u\n", result.Value);
    }
};

int main() {
    PrintingListener listener;
    std::unique_ptr<TaskQueue> taskQueue = std::make_unique<TaskQueue>();
    fpga::FpgaIO fpgaIO("/dev/fpga_dma_device", listener, "/tmp", taskQueue.get());
    fpgaIO.SendToHostMessageRouter(fpga::StartTracing{});
    fpgaIO.SendToCalculator(fpga::GCDRequest{.A = 12300, .B = 41});
    fpgaIO.SendToCalculator(fpga::MultiplyRequest{.A = 31, .B = 53});
    while (true) {
        fpgaIO.Poll();
        taskQueue->execute();
    }
}
```

## Output

<pre>
Received result: 41
Received result: 1643
</pre>

## Trace

Program counter shows the syntactic location of the step being executed (e.g. 44:9 means line 44, position 9).
All values other than Time are shown in hex (0x300c = 12300, 0x29 = 41, 0x66b = 1643).

<table><tr><td rowspan=6 colspan=1>Time</td><td rowspan=1 colspan=12>Calculator</td><td rowspan=6 colspan=1>Calculator<br/>Result</td></tr>
<tr><td rowspan=1 colspan=6>State</td><td rowspan=1 colspan=6>Inputs</td></tr>
<tr><td rowspan=4 colspan=1>Product</td><td rowspan=4 colspan=1>MultiplyA</td><td rowspan=4 colspan=1>MultiplyB</td><td rowspan=4 colspan=1>GCDA</td><td rowspan=4 colspan=1>GCDB</td><td rowspan=4 colspan=1>Program<br/>Counter</td><td rowspan=1 colspan=6>Request<br/>Reader</td></tr>
<tr><td rowspan=3 colspan=1>Option</td><td rowspan=1 colspan=5>Calculator<br/>Request</td></tr>
<tr><td rowspan=2 colspan=1>Option</td><td rowspan=1 colspan=2>Multiply<br/>Request</td><td rowspan=1 colspan=2>GCD<br/>Request</td></tr>
<tr><td rowspan=1 colspan=1>A</td><td rowspan=1 colspan=1>B</td><td rowspan=1 colspan=1>A</td><td rowspan=1 colspan=1>B</td></tr>
<tr><td><code></code></td><td><code>00000000</code></td><td><code>00000000</code></td><td><code>00000000</code></td><td><code>00000000</code></td><td><code>00000000</code></td><td><code>44:9</code></td><td><code>Blank</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 396</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td><code>CalculatorRequest</code></td><td><code>GCDRequest</code></td><td></td><td></td><td><code>0000300c</code></td><td><code>00000029</code></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>0000300c</code></td><td><code>00000029</code></td><td><code>30:25</code></td><td><code>Blank</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>00002fe3</code></td><td></td><td><code>37:29</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>00002fba</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>00002f91</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>00002f68</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td colspan="14" style="text-align: center">...</td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>000008f8</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>000008cf</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>000008a6</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>0000087d</code></td><td></td><td></td><td><code>CalculatorRequest</code></td><td><code>MultiplyRequest</code></td><td><code>0000001f</code></td><td><code>00000035</code></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>00000854</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>0000082b</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>00000802</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td colspan="14" style="text-align: center">...</td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>000000a4</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>0000007b</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>00000052</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td><code>00000029</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td><code>00000029</code></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td></td><td><code>44:9</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>0000001f</code></td><td><code>00000035</code></td><td></td><td></td><td><code>15:17</code></td><td><code>Blank</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>0000001f</code></td><td><code>0000003e</code></td><td><code>0000001a</code></td><td></td><td></td><td><code>22:21</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>0000007c</code></td><td><code>0000000d</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>0000009b</code></td><td><code>000000f8</code></td><td><code>00000006</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>000001f0</code></td><td><code>00000003</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>0000028b</code></td><td><code>000003e0</code></td><td><code>00000001</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>0000066b</code></td><td><code>000007c0</code></td><td><code>00000000</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td><code>0000066b</code></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td></td><td><code>44:9</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
</table>

A couple of things to note here:

* Multiply request arrives from host CPU as Calculator transform is busy processing GCD request and sits in the queue until GCD request is finished
* Pop signal lasts for one step only - this is why GCD request is popped off as soon as transform gets started working on it but Multiply request stays
  once it arrives until transform is finished with GCD request

## What does compiler actually generate from Flows?

Compiler generates SystemVerilog, a hardware description language. There's more to it (e.g. different parts running at different speeds) but on a basic
level it describes two things:

* What do registers contain (e.g. Alveo U50 has 1,743,360 bits of registers)
* How they are combined with inputs (PCIe, Ethernet, other connections)

[This page](RTL.md) illustrates the difference between how FPGA and CPU execute programs

## How does the data get to/from the card?

Messages to the card are usually small, so the CPU writes them directly into a memory window on the card (memory-mapped I/O). Direct Memory Access
(DMA) would need a round trip, because the CPU can't start a DMA transfer itself because it has no instructions for it. Messages are serialized into
32-bit words, with fields packed in the order they are declared, starting with most significant bit, and zero-padded on the last word.

PCIe has a configuration mechanism called Base Address Register (BAR). Each card reports number and sizes of memory windows it needs during boot-up
sequence:

```
$ sudo lspci -vs 01:00.0
01:00.0 Memory controller: Xilinx Corporation Device 903f
	Subsystem: Xilinx Corporation Device 0007
	Flags: fast devsel, IOMMU group 12
	Memory at fc000000 (32-bit, non-prefetchable) [size=64K]
	Memory at fc010000 (32-bit, non-prefetchable) [size=4K]
	Capabilities: [40] Power Management version 3
	Capabilities: [70] Express Endpoint, MSI 00
	Capabilities: [100] Advanced Error Reporting
	Capabilities: [1c0] Secondary PCI Express
	Kernel driver in use: fpga_dma_driver
	Kernel modules: fpga_driver
```

The first window (64 KB long) is used for AMD `xbflash2` utility to load the new flash image onto the card.
The second window (4 KB long) is used to send messages to the card. For example, this message:

```
    fpgaIO.SendToCalculator(fpga::GCDRequest{.A = 12300, .B = 41});
```
is serialized as
```
60000601 80000005 20000000

01|1|0 0000 0000 0000 0000 0110 0000 0001   100|0 0000 0000 0000 0000 0000 0000 0101    001|0 0000 0000 0000 0000 0000 0000 0000

01                               Destination (00: HostMessageRouter, 01: Calculator, 10: CMSBridge)
1                                Oneof option (GCDRequest)
00000000000000000011000000001100 A: 0x300c = 12300
00000000000000000000000000101001 B: 0x29 = 41
```
by packing the fields from most significant bit to the least significant bit and then writing resulting 32-bit words to sequential addresses
in the 4 KB window, wrapping around at 2 KB (top half is reserved for control registers - base address and write limit).

A small kernel driver allocates a contiguous range of memory 1 MB long that serves as a buffer for messages from the card and maps it to virtual
address space of the process opening the device. During the startup, FPGA library obtains the address from the driver and sets an FPGA register
to that value so that the card knows where to write the messages. The card then batches the 32-bit words into groups of 16 words (64 bytes) and
issues a DMA request to write it to host's memory (this allows it to avoid running out of PCIe credits and maximize utilization of PCIe bus).
In order for the host to recognize that a new value has been written, the top bit is inverted each time the queue wraps around. The queue also
maintains the write limit that is periodically updated by software in order to avoid overflow if software fails to keep up. The write limit
register also serves as a heartbeat - if the host fails to write a value (even if it's the same) to write limit register, the card runtime
assumes that the control process has crashed and resets the variables and restarts FPGA program.

The compiler generates a transform called `HostMessageRouter` that is responsible for sending messages to Flows transforms, tracing their
state and sending messages back to the host. The entire state of each transform, its inputs and messages to `HostMessageRouter` is packed into
one long bit array, then sliced into channels that can be updated independently:
```
08000000

0|000 10|00 0000 0000 0000 0000 0000 0000

0                          Lap bit
00010                      2, channel number (5 bits)
00000000000000000000000000 New value
```
The width for channel number is the smallest value for which the entire state bit array can fit into addressing scheme:
<table>
<tr><td>Channel number width</td><td>Total state bit array that can fit into the addressing scheme</td></tr>
<tr><td>0 bits</td><td>(31-0)*2^0 = 31</td></tr>
<tr><td>1 bits</td><td>(31-1)*2^1 = 60</td></tr>
<tr><td>2 bits</td><td>(31-2)*2^2 = 116</td></tr>
<tr><td>3 bits</td><td>(31-3)*2^3 = 224</td></tr>
<tr><td colspan="2" style="text-align: center">...</td></tr>
<tr><td>26 bits</td><td>(31-26)*2^26 = 335,544,320</td></tr>
<tr><td>27 bits</td><td>(31-27)*2^27 = 536,870,912</td></tr>
<tr><td>28 bits</td><td>(31-28)*2^28 = 805,306,368</td></tr>
<tr><td>29 bits</td><td>(31-29)*2^29 = 1,073,741,824</td></tr>
<tr><td>30 bits</td><td>(31-30)*2^30 = 1,073,741,824</td></tr>
</table>

For the example above, channels are laid out like this:
<table>
<tr><td>Channel</td><td>Bits</td><td>Value</td></tr>
<tr><td>17</td><td>25-0</td><td>Step counter</td></tr>
<tr><td>16</td><td>25-0</td><td>Error code</td></tr>
<tr><td>15</td><td>25-0</td><td>CMS Bridge response bits 31-6</td></tr>
<tr><td>14</td><td>25-0</td><td>CMS Bridge response bits 5-0, padded on the bottom with 20 zero bits</td></tr>
<tr><td>13</td><td>25-0</td><td>Calculator response bits 31-6</td></tr>
<tr><td>12</td><td>25-0</td><td>Calculator response bits 5-0, padded on the bottom with 20 zero bits</td></tr>
<tr><td rowspan="3">11</td><td>25</td><td>CMSBridge request is present</td></tr>
<tr><td>24-7</td><td>CMSBridge request read address (see <a href="https://docs.amd.com/r/en-US/pg348-cms-subsystem/Register-Space">CMS subsystem page for values</a>])</td></tr>
<tr><td>6-0</td><td>Calculator Product bits 31-25</td></tr>
<tr><td rowspan="2">10</td><td>25-1</td><td>Calculator Product bits 24-0</td></tr>
<tr><td>0</td><td>Calculator MultiplyA bit 31</td></tr>
<tr><td>9</td><td>25-0</td><td>Calculator MultiplyA bits 30-5</td></tr>
<tr><td rowspan="2">8</td><td>25-21</td><td>Calculator MultiplyA bits 4-0</td></tr>
<tr><td>20-0</td><td>Calculator MultiplyB bits 31-11</td></tr>
<tr><td rowspan="2">7</td><td>25-15</td><td>Calculator MultiplyB bits 10-0</td></tr>
<tr><td>14-0</td><td>Calculator GCDA bits 31-17</td></tr>
<tr><td rowspan="2">6</td><td>25-9</td><td>Calculator GCDA bits 16-0</td></tr>
<tr><td>8-0</td><td>Calculator GCDB bits 31-23</td></tr>
<tr><td rowspan="4">5</td><td>25-3</td><td>Calculator GCDB bits 22-0</td></tr>
<tr><td>2</td><td>Calculator currently at beginning (7:1)</td></tr>
<tr><td>1</td><td>Calculator currently at 15:17</td></tr>
<tr><td>0</td><td>Calculator currently at 22:21</td></tr>
<tr><td rowspan="6">4</td><td>25</td><td>Calculator currently at 30:25</td></tr>
<tr><td>24</td><td>Calculator currently at 37:29</td></tr>
<tr><td>23</td><td>Calculator currently at 44:9</td></tr>
<tr><td>22</td><td>Calculator request present</td></tr>
<tr><td>21</td><td>Calculator request option (GCD or Multiply)</td></tr>
<tr><td>20-0</td><td>Calculator request parameter A bits 31-11</td></tr>
<tr><td rowspan="2">3</td><td>25-15</td><td>Calculator request parameter A bits 10-0</td></tr>
<tr><td>14-0</td><td>Calculator request parameter B bits 31-17</td></tr>
<tr><td rowspan="2">2</td><td>25-9</td><td>Calculator request parameter B bits 16-0</td></tr>
<tr><td>8-0</td><td>Ethernet send Data bits 31-23</td></tr>
<tr><td rowspan="2">1</td><td>25-3</td><td>Ethernet send Data bits 22-0</td></tr>
<tr><td>2-0</td><td>Ethernet send ByteCount</td></tr>
<tr><td rowspan="2">0</td><td>25</td><td>Ethernet send Last</td></tr>
<tr><td>24-0</td><td>Zero padding</td></tr>
</table>

For tracing channels (0-11) `HostMessageRouter` compares the current value of traced state and input to its value on previous step and only
sends channels whose values have changed to host. For message channels (12-15), it sends values for all corresponding channels when Flows
program sets the value of output connection.

Step counter is increased on every step. It is sent and reset to 0 whenever there's new data on any of the channels, it reaches maximum value
or there is an error.

## How does PCIe interface work?

When CPU writes a 32-bit word to a memory-mapped region, it sends a Transaction Level Packet (TLP) over PCIe. Here's an example of a write packet:
```
40000001 0000000f 01234564 89abcdef

010|0 0000| 0|000 |0000| 0|0|00| 00|00 0000 0001|   0000 0000 0000 0000| 0000 0000| 0000| 1111|   0000 0001 0010 0011 0100 0101 0110 01|00|    1110 1111 1100 1101 1010 1011 1000 1001|

010                                Format (3 32-bit header with data)
00000                              Type (Memory write request)
0                                  Reserved
000                                Traffic class
0000                               Reserved
0                                  TLP digest
0                                  Poisoned data
00                                 Attributes
00                                 Address translation (untranslated)
0000000001                         Length (1 32-bit word)
0000000000000000                   Requester ID
00000000                           Tag
0000                               Last byte enable (used for byte-granularity writes)
1111                               First byte enable (write all 4 bytes in 32 bit word)
000000010010001101000101011001     Top 30 bits of address
00                                 Reserved
11101111110011011010101110001001   New value (ef cd ab 89 because x86 inverts byte order)
```
AMD provides a building block that delivers the essential fields from TLP to the FPGA program.
