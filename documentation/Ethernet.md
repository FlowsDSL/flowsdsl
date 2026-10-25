# Ethernet under the hood

This page describes how 10 Gigabit Ethernet works. On the physical level, it is just a never-stopping stream of bits. Even when no packets are
transmitted, the data (idle blocks) is sent over the link. The bits are sent in groups of 66 bits (64b/66b encoding) - 2 bits for sync header and 64 bits for data.
This is done to ensure that there is a transition between low state and high state at most every 66 bits (otherwise there's a small chance that there's no
edge for a long time and receiver ends up losing synchronization, either inserting extra bit or dropping a bit). There's also a fairly simple encoding that
transforms the sequence of bits into another sequence that is more likely to have more transitions (particularly since unencoded idle blocks consist of mostly
zeros), to improve physical aspects of the transmission (see scrambling section for details). Only data bits are scrambled, sync header bits are not.

The table below shows an example of raw Ethernet blocks after reversing the scrambling process. Block with sync header value of 1 contains 8 bytes of data in
the middle of packet. Block with sync header value of 2 contains control information, with bottom byte of data indicating type:
<table>
<tr><td>Type</td><td>Meaning</td></tr>
<tr><td><code>1e</code></td><td>Idle block, rest of bytes should be 0</td></tr>
<tr><td><code>78</code></td><td>Block preamble, appears before the first bytes of the packet. Rest of bytes should be <code>d5555555555555</code></td></tr>
<tr><td><code>87</code></td><td>End of packet, no more bytes</td></tr>
<tr><td><code>99</code></td><td>Last 1 byte of packet, appearing in rest of bytes</td></tr>
<tr><td><code>aa</code></td><td>Last 2 bytes of packet, appearing in rest of bytes</td></tr>
<tr><td><code>b4</code></td><td>Last 3 bytes of packet, appearing in rest of bytes</td></tr>
<tr><td><code>cc</code></td><td>Last 4 bytes of packet, appearing in rest of bytes</td></tr>
<tr><td><code>d2</code></td><td>Last 5 bytes of packet, appearing in rest of bytes</td></tr>
<tr><td><code>e1</code></td><td>Last 6 bytes of packet, appearing in rest of bytes</td></tr>
<tr><td><code>ff</code></td><td>Last 7 bytes of packet, appearing in rest of bytes</td></tr>
</table>
Block types 0 and 3 are invalid.

<table><tr><td rowspan=4 colspan=1>Time</td><td rowspan=1 colspan=5>Receiver</td><td rowspan=1 colspan=2>Raw<br/>Unscrambled<br/>Ethernet</td></tr>
<tr><td rowspan=1 colspan=5>Inputs</td><td rowspan=3 colspan=1>Header</td><td rowspan=3 colspan=1>Data</td></tr>
<tr><td rowspan=1 colspan=3>Network</td><td rowspan=2 colspan=1>Header<br/>Queue<br/>Ready</td><td rowspan=2 colspan=1>Body<br/>Queue<br/>Ready</td></tr>
<tr><td rowspan=1 colspan=1>Data</td><td rowspan=1 colspan=1>Byte<br/>Count</td><td rowspan=1 colspan=1>Last</td></tr>
<tr><td><code></code></td><td><code>00000000</code></td><td><code>0</code></td><td><code>0</code></td><td><code>0</code></td><td><code>0</code></td><td><code>2</code></td><td><code>000000000000001e</code></td></tr>
<tr><td><code>+ 5,606,816</code></td><td></td><td></td><td></td><td></td><td></td><td></td><td><code>d555555555555578</code></td></tr>
<tr><td><code>+ 2</code></td><td><code>00005e00</code></td><td><code>4</code></td><td></td><td></td><td></td><td><code>1</code></td><td><code>00000253005e0000</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>53020000</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>5e005301</code></td><td></td><td></td><td></td><td></td><td></td><td><code>004500080153005e</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>08004500</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>00549de4</code></td><td></td><td></td><td></td><td></td><td></td><td><code>01400040e49d5400</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>40004001</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>885d0a00</code></td><td></td><td></td><td></td><td></td><td></td><td><code>000a0800000a5d88</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>00080a00</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>00600800</code></td><td></td><td></td><td></td><td></td><td></td><td><code>0200e41300086000</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>13e40002</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>00017b8f</code></td><td></td><td></td><td></td><td></td><td></td><td><code>00006ac18f7b0100</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>c16a0000</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>0000e14b</code></td><td></td><td></td><td></td><td></td><td></td><td><code>000000074be10000</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>07000000</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>00001011</code></td><td></td><td></td><td></td><td></td><td></td><td><code>1514131211100000</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>12131415</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>16171819</code></td><td></td><td></td><td></td><td></td><td></td><td><code>1d1c1b1a19181716</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>1a1b1c1d</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>1e1f2021</code></td><td></td><td></td><td></td><td></td><td></td><td><code>2524232221201f1e</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>22232425</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>26272829</code></td><td></td><td></td><td></td><td></td><td></td><td><code>2d2c2b2a29282726</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>2a2b2c2d</code></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>2e2f3031</code></td><td></td><td></td><td></td><td></td><td></td><td><code>3534333231302f2e</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>00000000</code></td><td><code>0</code></td><td></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>32333435</code></td><td><code>4</code></td><td></td><td></td><td></td><td><code>2</code></td><td><code>00c1771ccd3736e1</code></td></tr>
<tr><td><code>+ 1</code></td><td><code>3637cd1c</code></td><td><code>2</code></td><td><code>1</code></td><td></td><td></td><td></td><td></td></tr>
<tr><td><code>+ 1</code></td><td><code>00000000</code></td><td><code>0</code></td><td><code>0</code></td><td></td><td></td><td></td><td><code>000000000000001e</code></td></tr>
</table>

## Scrambling

Scrambling is a fairly simple transformation, applied to each incoming bit, starting from bottom bit to top bit:

<img src="Scrambling.svg" width="800" height="200"/>

Unscrambler works the same except that new state bit simply copies incoming bit instead of combining it with bits 38 and 57.
Interesting property about this transformation is that it's self-synchronizing - starting state for scrambler doesn't matter.
Unscrambler gets 58 junk first bits but then recovers the original data.

## Block lock

The bit stream contains no indication of where the blocks start. When Ethernet receiver starts, it has no idea where the blocks start.
It starts at a random bit and then checks sync header in every 66 bit block. If any of them is either 0 or 3, it skips one bit and
starts over (this is a simplified version, the version in the 802.3 standard requires 64 valid headers in a row and doesn't start
block re-alignment until it sees 16 invalid headers in 64 blocks). Because the data bits are scrambled, at a wrong position two
consecutive bits are either 1 or 2 half the time, so a wrong position is usually rejected within a few blocks. The odds of 64
valid-looking headers with wrong alignment are about 10^-19, which is why the standard waits for 64.
