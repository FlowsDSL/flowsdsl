# Working Ping Responder First, Clock Domain Crossings Later

The usual learning path for FPGA programming starts with logic gates, flip-flops and timing, then a hardware description language, and reaches a working application
after months. This guide starts with a working ping responder, written in familiar C-like notation, and introduces each concept when the trace shows why it matters.

You don't need to learn about setup/hold times, clock domains and timing violations to get started. You'll meet them when they become critical and learn the same
fundamentals as experienced electrical engineering professionals, but you don't need them right away.

Flows, a C-like high-level language, and its tooling exist to make this path possible. The ping responder below, written in Flows, demonstrates the approach. It is
compiled to a hardware flash image (known as bitfile) in a single build step.

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

<style>
.s0 { display: inline; color: #c0c0c0 }
.s1 { display: inline; color: #80c080 }
.s2 { display: inline; color: #c08080 }
.s3 { display: inline; color: #808080 }
</style>
<pre>  1  <div class="s0"></div><div class="s3">/* Combined headers to read at once rather than one by one */</div><div class="s0"></div>
  2  <div class="s0"></div><div class="s1">struct</div><div class="s0"> EthernetIPAndICMP </div><div class="s2">{</div><div class="s0"></div>
  3  <div class="s0">    Ethernet</div><div class="s2">:</div><div class="s0"> EthernetHeader</div><div class="s2">;</div><div class="s0"></div>
  4  <div class="s0">    IP</div><div class="s2">:</div><div class="s0"> IPHeader</div><div class="s2">;</div><div class="s0"></div>
  5  <div class="s0">    ICMP</div><div class="s2">:</div><div class="s0"> ICMPHeader</div><div class="s2">;</div><div class="s0"></div>
  6  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
  7  <div class="s0"></div>
  8  <div class="s0"></div><div class="s3">/* Essential fields from incoming packets that are sent from receiver transform to sender transform */</div><div class="s0"></div>
  9  <div class="s0"></div><div class="s1">struct</div><div class="s0"> ResponderHeader </div><div class="s2">{</div><div class="s0"></div>
 10  <div class="s0">    EthernetSource</div><div class="s2">:</div><div class="s0"> EthernetAddress</div><div class="s2">;</div><div class="s0"></div>
 11  <div class="s0">    EthernetDestination</div><div class="s2">:</div><div class="s0"> EthernetAddress</div><div class="s2">;</div><div class="s0"></div>
 12  <div class="s0">    IPSource</div><div class="s2">:</div><div class="s0"> IPAddress</div><div class="s2">;</div><div class="s0"></div>
 13  <div class="s0">    IPDestination</div><div class="s2">:</div><div class="s0"> IPAddress</div><div class="s2">;</div><div class="s0"></div>
 14  <div class="s0">    IPTotalLength</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">16</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 15  <div class="s0">    ICMPChecksum</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">16</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 16  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
 17  <div class="s0"></div>
 18  <div class="s0"></div><div class="s3">/* Fragment of incoming packet body (data following Ethernet/IP/ICMP headers) */</div><div class="s0"></div>
 19  <div class="s0"></div><div class="s1">struct</div><div class="s0"> BodyWord </div><div class="s2">{</div><div class="s0"></div>
 20  <div class="s0">    Data</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">64</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 21  <div class="s0">    ByteCount</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">[</div><div class="s0">4</div><div class="s2">]</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 22  <div class="s0">    Last</div><div class="s2">:</div><div class="s0"> Bit</div><div class="s2">;</div><div class="s0"></div>
 23  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
 24  <div class="s0"></div>
 25  <div class="s0"></div><div class="s3">/* This block describes a stateful transform that runs continuously, taking inputs, accumulating the state over time and producing outputs.</div>
 26  <div class="s3"> * At fixed time intervals, &quot;Network&quot; input bus provides next word that has next 4 bytes (+ flag indicating end of the packet).</div>
 27  <div class="s3"> */</div><div class="s0"></div>
 28  <div class="s0"></div><div class="s1">transform</div><div class="s0"> PingResponderReceiver </div><div class="s2">(</div><div class="s0">Network</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">,</div><div class="s0"> HeaderQueue</div><div class="s2">:</div><div class="s0"> ResponderHeader</div><div class="s2">,</div><div class="s0"> BodyQueue</div><div class="s2">:</div><div class="s0"> BodyWord</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 29  <div class="s0">    </div><div class="s1">while</div><div class="s0"> </div><div class="s2">(</div><div class="s0"></div><div class="s1">true</div><div class="s0"></div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 30  <div class="s0">        EthernetIPAndICMP Headers </div><div class="s2">=</div><div class="s0"> EthernetIPAndICMP</div><div class="s2">{</div><div class="s0"></div><div class="s2">}</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 31  <div class="s0">        Bit HeadersAreComplete </div><div class="s2">=</div><div class="s0"> 0</div><div class="s2">;</div><div class="s0"></div>
 32  <div class="s0">        </div><div class="s3">/* Construct incoming network packet headers from incoming 4-byte words */</div><div class="s0"></div>
 33  <div class="s0">        ReadFixedLength</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> Network</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> Headers</div><div class="s2">,</div><div class="s0"> complete</div><div class="s2">:</div><div class="s0"> HeadersAreComplete</div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 34  <div class="s0">        </div><div class="s3">/* If packet is shorter than required minimum headers, skip it */</div><div class="s0"></div>
 35  <div class="s0">        </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0"></div><div class="s2">!</div><div class="s0">HeadersAreComplete</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 36  <div class="s0">            </div><div class="s1">continue</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 37  <div class="s0">        </div><div class="s2">}</div><div class="s0"></div>
 38  <div class="s0">        </div><div class="s3">/* Check that it&apos;s ICMP echo request packet. We still need to read the entire packet but the question</div>
 39  <div class="s3">         * is whether we send a response */</div><div class="s0"></div>
 40  <div class="s0">        Bit Copy </div><div class="s2">=</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">Ethernet</div><div class="s2">.</div><div class="s0">EtherType </div><div class="s2">==</div><div class="s0"> ETHERTYPE_IP </div><div class="s2">&amp;&amp;</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">IP</div><div class="s2">.</div><div class="s0">Version </div><div class="s2">==</div><div class="s0"> 4 </div><div class="s2">&amp;&amp;</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">IP</div><div class="s2">.</div><div class="s0">HeaderLength </div><div class="s2">==</div><div class="s0"> 5 </div><div class="s2">&amp;&amp;</div><div class="s0"></div>
 41  <div class="s0">            Headers</div><div class="s2">.</div><div class="s0">IP</div><div class="s2">.</div><div class="s0">Protocol </div><div class="s2">==</div><div class="s0"> IP_PROTOCOL_ICMP </div><div class="s2">&amp;&amp;</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">IP</div><div class="s2">.</div><div class="s0">TotalLength </div><div class="s2">&gt;=</div><div class="s0"> 24 </div><div class="s2">&amp;&amp;</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">ICMP</div><div class="s2">.</div><div class="s0">Type </div><div class="s2">==</div><div class="s0"> ICMP_ECHO_REQUEST</div><div class="s2">;</div><div class="s0"></div>
 42  <div class="s0">        </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">Copy</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 43  <div class="s0">            AssertingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> HeaderQueue</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> ResponderHeader</div><div class="s2">{</div><div class="s0"></div>
 44  <div class="s0">                EthernetSource</div><div class="s2">:</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">Ethernet</div><div class="s2">.</div><div class="s0">Destination</div><div class="s2">,</div><div class="s0"> EthernetDestination</div><div class="s2">:</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">Ethernet</div><div class="s2">.</div><div class="s0">Source</div><div class="s2">,</div><div class="s0"></div>
 45  <div class="s0">                IPSource</div><div class="s2">:</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">IP</div><div class="s2">.</div><div class="s0">Destination</div><div class="s2">,</div><div class="s0"> IPDestination</div><div class="s2">:</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">IP</div><div class="s2">.</div><div class="s0">Source</div><div class="s2">,</div><div class="s0"></div>
 46  <div class="s0">                IPTotalLength</div><div class="s2">:</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">IP</div><div class="s2">.</div><div class="s0">TotalLength</div><div class="s2">,</div><div class="s0"> ICMPChecksum</div><div class="s2">:</div><div class="s0"> Headers</div><div class="s2">.</div><div class="s0">ICMP</div><div class="s2">.</div><div class="s0">ICMPChecksum </div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 47  <div class="s0">        </div><div class="s2">}</div><div class="s0"></div>
 48  <div class="s0">        </div><div class="s3">/* Read rest of the packet and copy it to body queue if it&apos;s ICMP echo request packet */</div><div class="s0"></div>
 49  <div class="s0">        BodyWord DataWord </div><div class="s2">=</div><div class="s0"> BodyWord</div><div class="s2">{</div><div class="s0"></div><div class="s2">}</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 50  <div class="s0">        </div><div class="s1">while</div><div class="s0"> </div><div class="s2">(</div><div class="s0"></div><div class="s1">true</div><div class="s0"></div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 51  <div class="s0">            ReadVariableLength</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> Network</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> DataWord</div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 52  <div class="s0">            </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">Copy</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 53  <div class="s0">                AssertingPush</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> BodyQueue</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> DataWord</div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 54  <div class="s0">            </div><div class="s2">}</div><div class="s0"></div>
 55  <div class="s0">            </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">DataWord</div><div class="s2">.</div><div class="s0">Last</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 56  <div class="s0">                </div><div class="s1">break</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 57  <div class="s0">            </div><div class="s2">}</div><div class="s0"></div>
 58  <div class="s0">        </div><div class="s2">}</div><div class="s0"></div>
 59  <div class="s0">        </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 60  <div class="s0">    </div><div class="s2">}</div><div class="s0"></div>
 61  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
 62  <div class="s0"></div>
 63  <div class="s0"></div><div class="s3">/* This transform is responsible for driving the network port sender interface. For each request enqueued by Receiver</div>
 64  <div class="s3"> * transform, writes an echo reply: header with swapped addresses and adjusted checksums, then the original body</div>
 65  <div class="s3"> */</div><div class="s0"></div>
 66  <div class="s0"></div><div class="s1">transform</div><div class="s0"> PingResponderSender </div><div class="s2">(</div><div class="s0">Network</div><div class="s2">:</div><div class="s0"> Network32Word</div><div class="s2">,</div><div class="s0"> HeaderQueue</div><div class="s2">:</div><div class="s0"> ResponderHeader</div><div class="s2">,</div><div class="s0"> BodyQueue</div><div class="s2">:</div><div class="s0"> BodyWord</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 67  <div class="s0">    </div><div class="s1">while</div><div class="s0"> </div><div class="s2">(</div><div class="s0"></div><div class="s1">true</div><div class="s0"></div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 68  <div class="s0">        </div><div class="s3">/* Check if there&apos;s entry in header queue */</div><div class="s0"></div>
 69  <div class="s0">        </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">HeaderQueueReader </div><div class="s1">is</div><div class="s0"> ResponderHeader</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 70  <div class="s0">            </div><div class="s3">/* If there is, copy it to local variable and pop it off the queue */</div><div class="s0"></div>
 71  <div class="s0">            ResponderHeader Header </div><div class="s2">=</div><div class="s0"> HeaderQueueReader</div><div class="s2">.</div><div class="s0">ResponderHeader</div><div class="s2">;</div><div class="s0"></div>
 72  <div class="s0">            HeaderQueuePop </div><div class="s2">=</div><div class="s0"> 1</div><div class="s2">;</div><div class="s0"></div>
 73  <div class="s0">            </div><div class="s3">/* Calculate checksums</div>
 74  <div class="s3">             * IP checksum is calculated from scratch</div>
 75  <div class="s3">             * 0x5501 is 0x4500 (version+header length) + 0x1000 (time to live) + 0x0001 (ICMP protocol). Identification and flags header fields are 0</div>
 76  <div class="s3">             * ICMP checksum is calculated from original (presumably correct checksum) by adjusting it for replacing</div>
 77  <div class="s3">             * echo request (8) with echo response (0) in high byte of 16-bit packet word</div>
 78  <div class="s3">             */</div><div class="s0"></div>
 79  <div class="s0">            Bit</div><div class="s2">[</div><div class="s0">16</div><div class="s2">]</div><div class="s0"> IPChecksum </div><div class="s2">=</div><div class="s0"> </div><div class="s2">~</div><div class="s0">InternetChecksum</div><div class="s2">(</div><div class="s0">0x5501</div><div class="s2">,</div><div class="s0"> Header</div><div class="s2">.</div><div class="s0">IPTotalLength</div><div class="s2">,</div><div class="s0"> Header</div><div class="s2">.</div><div class="s0">IPSource</div><div class="s2">,</div><div class="s0"> Header</div><div class="s2">.</div><div class="s0">IPDestination</div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 80  <div class="s0">            Bit</div><div class="s2">[</div><div class="s0">16</div><div class="s2">]</div><div class="s0"> ICMPChecksum </div><div class="s2">=</div><div class="s0"> InternetChecksum</div><div class="s2">(</div><div class="s0">Header</div><div class="s2">.</div><div class="s0">ICMPChecksum</div><div class="s2">,</div><div class="s0"> 0x800</div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 81  <div class="s0">            </div><div class="s3">/* Start writing on the next step - checksum calculations are too slow to fit in same step as WriteFixedLength */</div><div class="s0"></div>
 82  <div class="s0">            </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 83  <div class="s0">            </div><div class="s3">/* Write header */</div><div class="s0"></div>
 84  <div class="s0">            WriteFixedLength</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> Network</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> EthernetIPAndICMP</div><div class="s2">{</div><div class="s0"></div>
 85  <div class="s0">                Ethernet</div><div class="s2">:</div><div class="s0"> EthernetHeader</div><div class="s2">{</div><div class="s0">Destination</div><div class="s2">:</div><div class="s0"> Header</div><div class="s2">.</div><div class="s0">EthernetDestination</div><div class="s2">,</div><div class="s0"> Source</div><div class="s2">:</div><div class="s0"> Header</div><div class="s2">.</div><div class="s0">EthernetSource</div><div class="s2">,</div><div class="s0"> EtherType</div><div class="s2">:</div><div class="s0"> ETHERTYPE_IP</div><div class="s2">}</div><div class="s0"></div><div class="s2">,</div><div class="s0"></div>
 86  <div class="s0">                IP</div><div class="s2">:</div><div class="s0"> IPHeader</div><div class="s2">{</div><div class="s0">Version</div><div class="s2">:</div><div class="s0"> 4</div><div class="s2">,</div><div class="s0"> HeaderLength</div><div class="s2">:</div><div class="s0"> 5</div><div class="s2">,</div><div class="s0"> HeaderChecksum</div><div class="s2">:</div><div class="s0"> IPChecksum</div><div class="s2">,</div><div class="s0"> TimeToLive</div><div class="s2">:</div><div class="s0"> 0x10</div><div class="s2">,</div><div class="s0"> TotalLength</div><div class="s2">:</div><div class="s0"> Header</div><div class="s2">.</div><div class="s0">IPTotalLength</div><div class="s2">,</div><div class="s0"></div>
 87  <div class="s0">                             Protocol</div><div class="s2">:</div><div class="s0"> IP_PROTOCOL_ICMP</div><div class="s2">,</div><div class="s0"> Source</div><div class="s2">:</div><div class="s0"> Header</div><div class="s2">.</div><div class="s0">IPSource</div><div class="s2">,</div><div class="s0"> Destination</div><div class="s2">:</div><div class="s0"> Header</div><div class="s2">.</div><div class="s0">IPDestination</div><div class="s2">}</div><div class="s0"></div><div class="s2">,</div><div class="s0"></div>
 88  <div class="s0">                ICMP</div><div class="s2">:</div><div class="s0"> ICMPHeader</div><div class="s2">{</div><div class="s0">Type</div><div class="s2">:</div><div class="s0"> ICMP_ECHO_RESPONSE</div><div class="s2">,</div><div class="s0"> ICMPChecksum</div><div class="s2">:</div><div class="s0"> ICMPChecksum</div><div class="s2">}</div><div class="s0"></div><div class="s2">}</div><div class="s0"></div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 89  <div class="s0">            </div><div class="s1">while</div><div class="s0"> </div><div class="s2">(</div><div class="s0"></div><div class="s1">true</div><div class="s0"></div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 90  <div class="s0">                </div><div class="s3">/* Request body queue should not be empty by now because we are behind Receiver by at least 10 steps that it takes to</div>
 91  <div class="s3">                 * write out headers</div>
 92  <div class="s3">                 */</div><div class="s0"></div>
 93  <div class="s0">                BodyWord DataWord </div><div class="s2">=</div><div class="s0"> BodyQueueReader</div><div class="s2">.</div><div class="s0">BodyWord</div><div class="s2">;</div><div class="s0"></div>
 94  <div class="s0">                BodyQueuePop </div><div class="s2">=</div><div class="s0"> 1</div><div class="s2">;</div><div class="s0"></div>
 95  <div class="s0">                WriteVariableLength</div><div class="s2">(</div><div class="s0">where</div><div class="s2">:</div><div class="s0"> Network</div><div class="s2">,</div><div class="s0"> what</div><div class="s2">:</div><div class="s0"> DataWord</div><div class="s2">)</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 96  <div class="s0">                </div><div class="s1">if</div><div class="s0"> </div><div class="s2">(</div><div class="s0">DataWord</div><div class="s2">.</div><div class="s0">Last</div><div class="s2">)</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
 97  <div class="s0">                    </div><div class="s1">break</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
 98  <div class="s0">                </div><div class="s2">}</div><div class="s0"></div>
 99  <div class="s0">            </div><div class="s2">}</div><div class="s0"></div>
100  <div class="s0">        </div><div class="s2">}</div><div class="s0"></div>
101  <div class="s0">        </div><div class="s1">step</div><div class="s0"></div><div class="s2">;</div><div class="s0"></div>
102  <div class="s0">    </div><div class="s2">}</div><div class="s0"></div>
103  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
104  <div class="s0"></div>
105  <div class="s0"></div><div class="s1">system</div><div class="s0"> </div><div class="s2">{</div><div class="s0"></div>
106  <div class="s0">    </div><div class="s1">instance</div><div class="s0"> Receiver</div><div class="s2">:</div><div class="s0"> PingResponderReceiver</div><div class="s2">;</div><div class="s0"></div>
107  <div class="s0">    </div><div class="s1">instance</div><div class="s0"> Sender</div><div class="s2">:</div><div class="s0"> PingResponderSender</div><div class="s2">;</div><div class="s0"></div>
108  <div class="s0">    </div><div class="s3">/* Native module connecting to Ethernet port */</div><div class="s0"></div>
109  <div class="s0">    </div><div class="s1">wire</div><div class="s0"> Ethernet</div><div class="s2">.</div><div class="s0">Receive </div><div class="s2">-&gt;</div><div class="s0"> Receiver</div><div class="s2">.</div><div class="s0">Network</div><div class="s2">;</div><div class="s0"></div>
110  <div class="s0">    </div><div class="s1">queue</div><div class="s0"> Receiver</div><div class="s2">.</div><div class="s0">HeaderQueue </div><div class="s2">-&gt;</div><div class="s0"> Sender</div><div class="s2">.</div><div class="s0">HeaderQueue</div><div class="s2">;</div><div class="s0"></div>
111  <div class="s0">    </div><div class="s1">queue</div><div class="s0"> Receiver</div><div class="s2">.</div><div class="s0">BodyQueue </div><div class="s2">-&gt;</div><div class="s0"> Sender</div><div class="s2">.</div><div class="s0">BodyQueue</div><div class="s2">;</div><div class="s0"></div>
112  <div class="s0">    </div><div class="s1">wire</div><div class="s0"> </div><div class="s1">with</div><div class="s0"> </div><div class="s1">backpressure</div><div class="s0"> Sender</div><div class="s2">.</div><div class="s0">Network </div><div class="s2">-&gt;</div><div class="s0"> Ethernet</div><div class="s2">.</div><div class="s0">Send</div><div class="s2">;</div><div class="s0"></div>
113  <div class="s0"></div><div class="s2">}</div><div class="s0"></div>
114  <div class="s0"></div>
</pre>

## How is this different from software?

Unlike software, which runs as a series of instructions on a CPU, a Flows program is compiled into a blueprint for a physical circuit
that is then loaded into the FPGA card. Each transform describes how the input signals are transformed into outputs using accumulated
state and becomes a dedicated circuit on the chip. All transforms run continuously in parallel and communicate using the connections
and queues listed in the system section.

<svg width=600 height=440>
  <line x1="30" y1="430" x2="30" y2="30" stroke="white" stroke-width="1"/>
  <line x1="30" y1="30" x2="0" y2="30" stroke="white" stroke-width="1"/>
  <rect x="30" y="100" width="500" height="300" stroke="white" stroke-width="1" fill="none"/>
  <rect x="80" y="400" width="200" height="30" stroke="white" stroke-width="1" fill="none"/>
  <line x1="100" y1="400" x2="100" y2="430" stroke="white" stroke-width="1"/>
  <line x1="120" y1="400" x2="120" y2="430" stroke="white" stroke-width="1"/>
  <line x1="140" y1="400" x2="140" y2="430" stroke="white" stroke-width="1"/>
  <line x1="160" y1="400" x2="160" y2="430" stroke="white" stroke-width="1"/>
  <line x1="180" y1="400" x2="180" y2="430" stroke="white" stroke-width="1"/>
  <line x1="200" y1="400" x2="200" y2="430" stroke="white" stroke-width="1"/>
  <line x1="220" y1="400" x2="220" y2="430" stroke="white" stroke-width="1"/>
  <line x1="240" y1="400" x2="240" y2="430" stroke="white" stroke-width="1"/>
  <line x1="260" y1="400" x2="260" y2="430" stroke="white" stroke-width="1"/>
  <rect x="0" y="240" width="170" height="30" stroke="white" stroke-width="1" fill="none"/>
  <text x="40" y="255" fill="white" alignment-baseline="central">QSFP28 Ethernet port</text>
  <rect x="190" y="130" width="300" height="240" stroke="white" stroke-width="1" fill="#404040"/>
  <text x="200" y="140" fill="white" alignment-baseline="hanging">FPGA chip (XCU50)</text>
  <line x1="170" y1="250" x2="210" y2="250" stroke="white" stroke-width="1"/>
  <line x1="210" y1="250" x2="210" y2="190" stroke="white" stroke-width="1"/>
  <line x1="210" y1="190" x2="230" y2="190" stroke="white" stroke-width="1"/>
  <line x1="230" y1="190" x2="225" y2="187" stroke="white" stroke-width="1"/>
  <line x1="230" y1="190" x2="225" y2="193" stroke="white" stroke-width="1"/>
  <rect x="230" y="160" width="240" height="60" stroke="white" stroke-width="1" fill="none"/>
  <text x="240" y="170" fill="white" alignment-baseline="hanging">Receiver transform</text>
  <line x1="310" y1="220" x2="310" y2="240" stroke="white" stroke-width="1"/>
  <line x1="310" y1="240" x2="307" y2="235" stroke="white" stroke-width="1"/>
  <line x1="310" y1="240" x2="313" y2="235" stroke="white" stroke-width="1"/>
  <line x1="310" y1="270" x2="310" y2="290" stroke="white" stroke-width="1"/>
  <line x1="310" y1="290" x2="307" y2="285" stroke="white" stroke-width="1"/>
  <line x1="310" y1="290" x2="313" y2="285" stroke="white" stroke-width="1"/>
  <rect x="240" y="240" width="140" height="30" stroke="white" stroke-width="1" fill="none"/>
  <text x="250" y="245" fill="white" alignment-baseline="hanging">Header</text>
  <text x="250" y="255" fill="white" alignment-baseline="hanging">Queue</text>
  <line x1="430" y1="220" x2="430" y2="240" stroke="white" stroke-width="1"/>
  <line x1="430" y1="240" x2="427" y2="235" stroke="white" stroke-width="1"/>
  <line x1="430" y1="240" x2="433" y2="235" stroke="white" stroke-width="1"/>
  <line x1="430" y1="270" x2="430" y2="290" stroke="white" stroke-width="1"/>
  <line x1="430" y1="290" x2="427" y2="285" stroke="white" stroke-width="1"/>
  <line x1="430" y1="290" x2="433" y2="285" stroke="white" stroke-width="1"/>
  <rect x="400" y="240" width="60" height="30" stroke="white" stroke-width="1" fill="none"/>
  <text x="410" y="245" fill="white" alignment-baseline="hanging">Body</text>
  <text x="410" y="255" fill="white" alignment-baseline="hanging">Queue</text>
  <rect x="230" y="290" width="240" height="60" stroke="white" stroke-width="1" fill="none"/>
  <text x="240" y="300" fill="white" alignment-baseline="hanging">Sender transform</text>
  <line x1="170" y1="260" x2="175" y2="257" stroke="white" stroke-width="1"/>
  <line x1="170" y1="260" x2="175" y2="263" stroke="white" stroke-width="1"/>
  <line x1="170" y1="260" x2="210" y2="260" stroke="white" stroke-width="1"/>
  <line x1="210" y1="260" x2="210" y2="320" stroke="white" stroke-width="1"/>
  <line x1="210" y1="320" x2="230" y2="320" stroke="white" stroke-width="1"/>
</svg>

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

<details>
<summary>Trace of concurrent execution</summary>
<style>td { vertical-align: top }</style>
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
</details>

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
