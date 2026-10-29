# Working Ping Responder First, Clock Domain Crossings Later

The usual learning path for FPGA programming starts with logic gates, flip-flops and timing, then a hardware description language, and reaches a working application
after months. This guide starts with a working ping responder, written in familiar C-like notation, and introduces each concept when the trace shows why it matters.

You don't need to learn about setup/hold times, clock domains and timing violations **to get started**. Same fundamentals, different order: you'll meet them when
they become critical, and master them gradually.

Flows, a C-like high-level language, and its tooling exist to make this path possible. You decide what happens in each step, and the compiler works out the
hardware details that follow:
* a transform is ordinary sequential code with loops, branches and early exits; the `step` keyword marks where one step ends and the next begins
* within a step, you can assign a variable several times, just like in software ([see how](documentation/Compiler.md))
* transforms run in parallel, connected by queues in a `system` block; a queue makes the sender wait when the receiver isn't ready
* the hardware streams every transform's state to the host, so you debug against your source, step by step, on the board

The ping responder below, written in Flows, demonstrates the approach. It is compiled to a hardware flash image (known as bitfile) in a single build step.

One of the key components of this approach is transparency - generated FPGA bitfile collects traces of internal state of your program in hardware and sends them to
the host CPU. You can see what FPGA did on every step, in terms of your source code (line numbers, transforms, variables). It is a part of the hardware program from
very beginning, so when something unexpected happens the trace is already there.

All traces in this documentation were captured on an Alveo U50, not in simulation. The ping responder runs on the FPGA
card and answers real ping requests from another computer, keeping up with 10 Gigabit Ethernet at full speed and
starting each reply before the request has finished arriving. [This page](documentation/CompletePath.md) shows how to run the
`PingResponder` example on FPGA and includes the complete trace.

## How

The Flows compiler translates source code into SystemVerilog (low-level hardware description language). Vivado (AMD's
FPGA toolchain) then builds that into a hardware flash image. Hand-written SystemVerilog plumbing connects the compiled
code to the card's PCIe and Ethernet port. The FPGA's built-in serial link turns the signal on the wire into raw bits,
and the plumbing turns those bits into packets and back. [Platform underneath](documentation/Platform.md) page explains this design
choice.

SystemVerilog generated from ping responder source code below meets timing (meaning Vivado lays out FPGA cells in a way
that propagates the signal in time for logic to work reliably) at 322 MHz required to keep up with 10 Gigabit Ethernet
(322.265625 * 32 = 10312.5; 10312.5 * 64 / 66 = 10000 - see [Ethernet under the hood](documentation/Ethernet.md) page for details of where 64
and 66 come from).

## Ping example

```
  1  /* Combined headers to read at once rather than one by one */
  2  struct EthernetIPAndICMP {
  3      Ethernet: EthernetHeader;
  4      IP: IPHeader;
  5      ICMP: ICMPHeader;
  6  }
  7  
  8  /* Essential fields from incoming packets that are sent from receiver transform to sender transform */
  9  struct ResponderHeader {
 10      EthernetSource: EthernetAddress;
 11      EthernetDestination: EthernetAddress;
 12      IPSource: IPAddress;
 13      IPDestination: IPAddress;
 14      IPTotalLength: Bit[16];
 15      ICMPChecksum: Bit[16];
 16  }
 17  
 18  /* Fragment of incoming packet body (data following Ethernet/IP/ICMP headers) */
 19  struct BodyWord {
 20      Data: Bit[64];
 21      ByteCount: Bit[4];
 22      Last: Bit;
 23  }
 24  
 25  /* This block describes a stateful transform that runs continuously, taking inputs, accumulating the state over time and producing outputs.
 26   * At fixed time intervals, Network input bus provides next word that has next 4 bytes (+ flag indicating end of the packet).
 27   */
 28  transform PingResponderReceiver (Network: Network32Word, HeaderQueue: ResponderHeader, BodyQueue: BodyWord) {
 29      while (true) {
 30          EthernetIPAndICMP Headers = EthernetIPAndICMP{};
 31          Bit HeadersAreComplete = 0;
 32          /* Construct incoming network packet headers from incoming 4-byte words */
 33          ReadFixedLength(where: Network, what: Headers, complete: HeadersAreComplete);
 34          /* If packet is shorter than required minimum headers, skip it */
 35          if (!HeadersAreComplete) {
 36              continue;
 37          }
 38          /* Check that it's ICMP echo request packet. We still need to read the entire packet but the question
 39           * is whether we send a response */
 40          Bit Copy = Headers.Ethernet.EtherType == ETHERTYPE_IP && Headers.IP.Version == 4 && Headers.IP.HeaderLength == 5 &&
 41              Headers.IP.Protocol == IP_PROTOCOL_ICMP && Headers.IP.TotalLength >= 24 && Headers.ICMP.Type == ICMP_ECHO_REQUEST;
 42          if (Copy) {
 43              AssertingPush(where: HeaderQueue, what: ResponderHeader{
 44                  EthernetSource: Headers.Ethernet.Destination, EthernetDestination: Headers.Ethernet.Source,
 45                  IPSource: Headers.IP.Destination, IPDestination: Headers.IP.Source,
 46                  IPTotalLength: Headers.IP.TotalLength, ICMPChecksum: Headers.ICMP.ICMPChecksum });
 47          }
 48          /* Read rest of the packet and copy it to body queue if it's ICMP echo request packet */
 49          BodyWord DataWord = BodyWord{};
 50          while (true) {
 51              ReadVariableLength(where: Network, what: DataWord);
 52              if (Copy) {
 53                  AssertingPush(where: BodyQueue, what: DataWord);
 54              }
 55              if (DataWord.Last) {
 56                  break;
 57              }
 58          }
 59          step;
 60      }
 61  }
 62  
 63  /* This transform is responsible for driving the network port sender interface. For each request enqueued by Receiver
 64   * transform, writes an echo reply: header with swapped addresses and adjusted checksums, then the original body
 65   */
 66  transform PingResponderSender (Network: Network32Word, HeaderQueue: ResponderHeader, BodyQueue: BodyWord) {
 67      while (true) {
 68          /* Check if there's entry in header queue */
 69          if (HeaderQueueReader is ResponderHeader) {
 70              /* If there is, copy it to local variable and pop it off the queue */
 71              ResponderHeader Header = HeaderQueueReader.ResponderHeader;
 72              HeaderQueuePop = 1;
 73              /* Calculate checksums
 74               * IP checksum is calculated from scratch
 75               * 0x5501 is 0x4500 (version+header length) + 0x1000 (time to live) + 0x0001 (ICMP protocol). Identification and flags header fields are 0
 76               * ICMP checksum is calculated from original (presumably correct checksum) by adjusting it for replacing
 77               * echo request (8) with echo response (0) in high byte of 16-bit packet word
 78               */
 79              Bit[16] IPChecksum = ~InternetChecksum(0x5501, Header.IPTotalLength, Header.IPSource, Header.IPDestination);
 80              Bit[16] ICMPChecksum = InternetChecksum(Header.ICMPChecksum, 0x800);
 81              /* Start writing on the next step - checksum calculations are too slow to fit in same step as WriteFixedLength */
 82              step;
 83              /* Write header */
 84              WriteFixedLength(where: Network, what: EthernetIPAndICMP{
 85                  Ethernet: EthernetHeader{Destination: Header.EthernetDestination, Source: Header.EthernetSource, EtherType: ETHERTYPE_IP},
 86                  IP: IPHeader{Version: 4, HeaderLength: 5, HeaderChecksum: IPChecksum, TimeToLive: 0x10, TotalLength: Header.IPTotalLength,
 87                               Protocol: IP_PROTOCOL_ICMP, Source: Header.IPSource, Destination: Header.IPDestination},
 88                  ICMP: ICMPHeader{Type: ICMP_ECHO_RESPONSE, ICMPChecksum: ICMPChecksum}});
 89              while (true) {
 90                  /* Request body queue should not be empty by now because we are behind Receiver by at least 10 steps that it takes to
 91                   * write out headers
 92                   */
 93                  BodyWord DataWord = BodyQueueReader.BodyWord;
 94                  BodyQueuePop = 1;
 95                  WriteVariableLength(where: Network, what: DataWord);
 96                  if (DataWord.Last) {
 97                      break;
 98                  }
 99              }
100          }
101          step;
102      }
103  }
104  
105  system {
106      instance Receiver: PingResponderReceiver;
107      instance Sender: PingResponderSender;
108      /* Native module connecting to Ethernet port */
109      wire Ethernet.Receive -> Receiver.Network;
110      queue Receiver.HeaderQueue -> Sender.HeaderQueue;
111      queue Receiver.BodyQueue -> Sender.BodyQueue;
112      wire with backpressure Sender.Network -> Ethernet.Send;
113  }
114  
```

## How is this different from software?

Unlike software, which runs as a series of instructions on a CPU, a Flows program is compiled into a blueprint for a physical circuit
that is then loaded into the FPGA card. Each transform describes how the input signals are transformed into outputs using accumulated
state and becomes a dedicated circuit on the chip. All transforms run continuously in parallel and communicate using the connections
and queues listed in the system section.

<img src="documentation/Card.svg" width="600" height="440"/>

The second difference is timing. CPUs have many mechanisms that enable scalability of complexity but come at the cost
of deterministic timing:
* Caches (a fetch from memory can take anywhere from 4 cycles for L1 cache to 300+ cycles for a fetch from DRAM)
* Virtual memory (loading new page into TLB can take several hundred cycles due to multiple levels of paging)
* Interrupts/page protection faults (can take microseconds or even more for e.g. memory-mapped files)
* Memory-mapped I/O (PCIe devices) (hundreds of cycles once write buffers fill up)

FPGA does not have any of those opaque delays. Each step in FPGA generally takes a fixed amount of time (for the
transform that processes Ethernet data in particular it's approximately 3.1 nanoseconds). This is what the table in the
next section shows (click to expand) - the evolution of state over steps (collected from hardware, not a simulation).
Think of it as stepping through the program in a debugger. It is up to the programmer to describe what calculations take
place on each step.

Reading a header that arrives over several steps is handled by a macro; [this page](documentation/ReadFixedLength.md) shows how it
works step by step.

## Concurrent execution of transforms

The reply starts going out before the request has finished arriving. The sender begins transmitting 3 steps (9.3 ns)
after the ICMP type byte arrives, which is the first moment the program knows it's an echo request, while the rest of
the request is still being received. The gap in data sent is caused by Ethernet interface not being ready to accept more
data (counterpart of receive gap explained on [this page](documentation/ReadFixedLength.md)).

<table><tr><td rowspan=4 colspan=1>Time</td><td rowspan=1 colspan=1>Sender</td><td rowspan=1 colspan=3>Ethernet</td><td rowspan=1 colspan=3>Receiver</td></tr>
<tr><td rowspan=1 colspan=1>Inputs</td><td rowspan=3 colspan=1>Data</td><td rowspan=3 colspan=1>Byte<br/>Count</td><td rowspan=3 colspan=1>Last</td><td rowspan=1 colspan=3>Inputs</td></tr>
<tr><td rowspan=2 colspan=1>Network<br/>Ready</td><td rowspan=1 colspan=3>Network</td></tr>
<tr><td rowspan=1 colspan=1>Data</td><td rowspan=1 colspan=1>Byte<br/>Count</td><td rowspan=1 colspan=1>Last</td></tr>
<tr><td><code></code></td><td><code>1</code></td><td><code>00000000</code></td><td><code>0</code></td><td><code>0</code></td><td><code>00000000</code></td><td><code>0</code></td><td><code>0</code></td></tr>
<tr><td><code>+ 1,688,113,826</code></td><td></td><td></td><td></td><td></td><td><code>00005e00</code></td><td><code>4</code></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>53020000</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>5e005301</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>08004500</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>00542de8</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>00000000</code></td><td><code>0</code></td><td></td></tr>
<tr><td><code>+ 2</code></td><td></td><td></td><td></td><td></td><td><code>40004001</code></td><td><code>4</code></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>f8590a00</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>00080a00</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>00600800</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>b6530003</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td></td><td></td><td></td><td><code>00017245</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>00005e00</code></td><td><code>4</code></td><td></td><td><code>ca6a0000</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>53010000</code></td><td></td><td></td><td><code>00003c25</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>0</code></td><td><code>5e005302</code></td><td></td><td></td><td><code>0a000000</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>1</code></td><td></td><td></td><td></td><td><code>00001011</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>08004500</code></td><td></td><td></td><td><code>12131415</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>00540000</code></td><td></td><td></td><td><code>16171819</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>00001001</code></td><td></td><td></td><td><code>1a1b1c1d</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>96420a00</code></td><td></td><td></td><td><code>1e1f2021</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>00600a00</code></td><td></td><td></td><td><code>22232425</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>00080000</code></td><td></td><td></td><td><code>26272829</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>be530003</code></td><td></td><td></td><td><code>2a2b2c2d</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>00017245</code></td><td></td><td></td><td><code>2e2f3031</code></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>ca6a0000</code></td><td></td><td></td><td><code>00000000</code></td><td><code>0</code></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>00003c25</code></td><td></td><td></td><td><code>32333435</code></td><td><code>4</code></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>0a000000</code></td><td></td><td></td><td><code>3637a503</code></td><td><code>2</code></td><td><code>1</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>0</code></td><td><code>00001011</code></td><td></td><td></td><td><code>00000000</code></td><td><code>0</code></td><td><code>0</code></td></tr>
<tr><td><code>+ 2</code></td><td><code>1</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>12131415</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>16171819</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>1a1b1c1d</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>1e1f2021</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>22232425</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>26272829</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>2a2b2c2d</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>2e2f3031</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>32333435</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td></td><td><code>3637a503</code></td><td><code>2</code></td><td><code>1</code></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>0</code></td><td><code>00000000</code></td><td><code>0</code></td><td><code>0</code></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>1</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
</table>

## Where next?

* [From source to trace](documentation/CompletePath.md) page shows how to install Flows, build the `PingResponder` example, run it on
  FPGA, collect the trace
* [Software-hardware co-design](documentation/MessageExchange.md) page shows how to exchange messages with host software.
* [Ethernet under the hood](documentation/Ethernet.md) page shows details of how Ethernet works below the packet level
* [How ReadFixedLength macro is implemented](documentation/ReadFixedLength.md) page shows what the `ReadFixedLength` macro expands 
  into
* [How the Flows compiler works](documentation/Compiler.md) page shows how Flows compiler transforms source code into SystemVerilog

## Status and known limitations

This is a personal research project built over about the equivalent of 6 months of full-time effort.
I have other projects competing for my time, so expect progress to be steady but not fast.

### Hardware and platform

* Alveo U50 only (other Alveo cards: needs a small change)
* One card per host (needs a small change)
* One 10 Gb Ethernet connection (needs a moderate change)
* PCIe interface is limited to basic read/write operations up to 64 bits (no atomics, no SSE/AVX wide reads/writes, etc).
  Unsupported operations on BAR 0 are left without completions and will likely hang the host.
* The kernel driver makes `/dev/fpga_dma_device` readable and writable by every local user, which is fine on a personal
  machine; on a shared one, restrict it with a udev rule (mode 0660 and a dedicated group)

### Language and compiler

* An output port group can connect to only one destination; fan-out from wires is rejected by the compiler (needs a small change)
* AssertingPush and AssertingPop don't end the step, so using the same queue twice in one step with them acts on one slot: the
* second push replaces the first, and the second pop reads the same element again. The compiler doesn't check this yet.
  `BlockingPush` and `BlockingPop` end their step, so they're safe
* Accessing a field of a `oneof` doesn't check which option is currently stored; reading the wrong option returns that option's
  bits reinterpreted
* Pop works on queues only; Push works on queues and wires with backpressure
* Transforms that are declared but never instantiated are checked for errors but not generated

### Building

* Tested on specific versions of toolchain (Ubuntu 22.04, Vivado 2023.2, JDK 22+)
* Timing closure is not guaranteed (by design). When Vivado reports a timing failure, the usual cause is a step that does
  too much (wide arithmetic or deep function trees); splitting it with another `step` typically fixes it. Mapping timing
  failures back to source lines is the top planned compiler feature.

### Future development

Flows is meant to grow as a platform: language, compiler, and tooling together. Planned directions:

* Language:
  * General Assert function
  * Verification of type-safe union option access (verifying type option before accessing fields)
* Compiler:
  * Mapping timing violations back to source code
  * Liveness analysis (which registers need to be saved in which steps)
  * Read/Write macros unrolling to enhance timing
  * HBM/DDR support
  * Warning about overwriting port output in same step
  * True spatial layout for variables through creating a set of registers for each step
* Tooling:
  * Running Flows programs and collecting traces on CPU, without FPGA card
  * Visual composition of streaming computation pipelines

Tell me which of these would matter to you.

## Related approaches

Flows is not the first attempt to raise the level of hardware description. It was designed independently, and some of its ideas turn out to have close
relatives in earlier approaches. The quickest way to place it is to look at what the programmer writes by hand and what the tool derives:

| | Control flow (state machine) | Dataflow within a cycle | Timing (what happens in which cycle) |
|---|---|---|---|
| RTL (Verilog, VHDL, SystemVerilog) | manual | manual | manual |
| Handel-C | automated | avoided (one assignment per cycle) | implied by assignments |
| **Flows** | automated | automated | explicit (`step`) |
| High-level synthesis | automated | automated | automated (scheduler) |

Nearest neighbors, and how each differs:

* **High-level synthesis** (Vitis HLS, Catapult, Bambu): a scheduler decides which operation runs in which cycle, guided by pragmas. In Flows the
  programmer decides with `step`, and the trace shows exactly that schedule. HLS is a good fit for regular compute pipelines; Flows targets
  control-heavy logic such as protocol handling, where cycle-by-cycle behavior is the point. The two can coexist in one design.
* **Embedded hardware languages** (Chisel, SpinalHDL, Amaranth, Clash, Hardcaml): a host language (Scala, Python, Haskell, OCaml) generates RTL,
  which gives much better abstraction and reuse, but registers and state machines are still written by hand.
* **Bluespec**: hardware is described as guarded atomic rules, and the compiler decides which rules fire together in a cycle. Flows instead uses
  sequential code divided into steps.
* **Handel-C**: the closest relative - C-like code with control flow turned into hardware automatically. In Handel-C every assignment takes one
  clock cycle, so a chain of dependent assignments spans several cycles. In Flows any number of statements between two `step`s execute in the same
  cycle; the compiler merges them into one formula per variable (see the [compiler page](documentation/Compiler.md)).
* **Synchronous languages** (Lustre, Esterel, SCADE, and later Lucid Synchrone and Zélus), a largely French tradition: a program advances in
  discrete instants, and everything within an instant happens at once. Esterel is imperative, and its `pause` marks the boundary between instants
  much like `step` does in Flows; it was also compiled to circuits. Lustre describes programs as equations over streams of values, or flows.
  Flows arrives at the same synchronous model, in an imperative style close to Esterel's, and applies it to streaming data on FPGAs, with queues
  between transforms and traces from real hardware.
* **On-chip debugging** (Vivado ILA, Quartus SignalTap, Synopsys Identify): capture a window of netlist signals around a trigger. Flows builds
  tracing into the program itself and streams it to the host in terms of source lines, transforms and variables.

Also related: Spatial (Stanford), Calyx, Filament, PipelineC and Kôika. A longer comparison page is planned; if you know a neighbor that belongs
on this list, please tell me.

## Feedback and contact

Flows is a one-person project at this point, and the most useful thing you can give it is an honest reaction. I'm especially interested in hearing from:
* software engineers who tried FPGA development and gave up: did this get you further, and where did it lose you?
* FPGA engineers: where does this break down for the designs you build, and what would you need to see before using it on a real project?
* people who build or lead hardware teams: does this look like a faster path to productive engineers, or not?

I'm also glad to talk with anyone working on hardware/software co-design, low-latency systems, or synchronous languages,
whether or not it's about Flows. I'm planning a move to Paris and visit a few times a year; if you're there and working on any of this,
I'd be glad to meet.

* Email: <a href="mailto:flows.dsl@proton.me">flows.dsl@proton.me</a> (or open a GitHub issue/discussion). I read everything and reply.

## Acknowledgements

AMD, Alveo, Vivado and Vitis are trademarks of Advanced Micro Devices, Inc.
Flows builds on AMD's Vivado, PCIe and transceiver IP and the Alveo U50.
