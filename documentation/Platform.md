# Platform underneath

This page goes into detailed design choices for SystemVerilog plumbing and assumes familiarity with RTL design.

Flows runtime builds on raw PCIe block (`pcie4c_uscale_plus`) rather than `XDMA` for a few reasons:
* Latency. Both sides of communication are responsible for initiating posted writes - CPU using memory-mapped I/O
  and card using DMA. XDMA is designed around host-managed descriptor rings: the host driver sets up transfers and the
  engine executes them. Card-initiated transfers are possible through descriptor bypass, but they still go through the
  DMA engine and its AXI data path. Flows treats the card as a peer that initiates traffic itself, so it issues RQ
  messages directly instead.
* Driver. XDMA's reference driver is designed for bulk transfers: each one goes through a system call, and that kernel
  round trip dominates for small messages.
* Lean implementation. XDMA's engine provides general-purpose features (scatter-gather descriptor rings, interrupts,
  multiple channels) that Flows' message model doesn't need. Flows implements the very minimum of the features needed
  in order to minimize resource utilization.
  <table>
  <tr><th rowspan=2></th><th colspan=3>XDMA</th><th colspan=3>Flows</th></tr>
  <tr><th>LUTs</th><th>FFs</th><th>RAMB36</th><th>LUTs</th><th>FFs</th><th>RAMB36</th></tr>
  <tr><td>PCIe core</td><td>10,023</td><td>25,151</td><td>22</td><td>7,344</td><td>19,018</td><td>22</td></tr>
  <tr><td>DMA engine / PCIe
  plumbing</td><td>37,615</td><td>24,728</td><td>42</td><td>1,288</td><td>1,062</td><td>0</td></tr>
  <tr><td>Total</td><td>47,638</td><td>49,879</td><td>64</td><td>8,632</td><td>20,080</td><td>22</td></tr>
  </table>
  <sub>Implemented designs, Vivado 2023.2, xcu50. XDMA: Gen3 x16, 512-bit, AXI-Stream, 1 H2C + 1 C2H with descriptor
  bypass. Flows figures exclude the host message router and the clock-crossing message queues, which an XDMA-based design
  also needs.</sub>

Flows runtime builds on GTY transceiver wizard (`gtwizard_ultrascale`) rather than `xxv_ethernet` block for two reasons:
* License cost. The `xxv_ethernet` MAC requires a separately purchased
  [license](https://www.digikey.com/en/products/detail/amd/EF-DI-25GEMAC-PROJ/6132226), which is difficult to justify for experimentation.
* Lean implementation. `xxv_ethernet` provides a lot of features (`RS-FEC`, `AN/LT`, statistics, `PTP`) that aren't needed
  for basic 10 Gigabit Ethernet link but are required for 25 Gigabit Ethernet.
 
  <table>
  <tr><th rowspan=2></th><th colspan=2>xxv_ethernet</th><th colspan=2>Flows</th></tr>
  <tr><th>LUTs</th><th>FFs</th><th>LUTs</th><th>FFs</th></tr>
  <tr><td>MAC + PCS</td><td>1,432</td><td>1,226</td><td>413</td><td>303</td></tr>
  <tr><td>Transceiver wizard</td><td>92</td><td>211</td><td>100</td><td>301</td></tr>
  <tr><td><b>Total, one 10G lane</b></td><td><b>1,524</b></td><td><b>1,437</b></td><td><b>513</b></td><td 
  align="right"><b>604</b></td></tr>
  </table>
  <sub>xxv_ethernet in its leanest 10G BASE-R configuration; figures for the lane in use in a four-lane build, with shared
  logic apportioned. The Flows unscrambler is estimated from the identical scrambler.</sub>

That said, `xxv_ethernet` can be easily swapped in (with trivial conversion of `tvalid/tkeep/tlast` signals into
`ByteCount/Last`).

Flows runtime uses two dedicated BARs rather than AXI Interconnect for two reasons:
* `xbflash2` requires a dedicated BAR path to Quad SPI block
* `cms_subsystem` sits behind the card ↔ host queues so that reads don't stall CPU - following the same asynchronous
  message exchange scheme as all other messages

The remaining base modules are common utility building blocks:
* `axi_quad_spi` - flash programming module
* `cms_subsystem` - card management subsystem, providing features like temperature monitoring
* `clk_wiz` - utility module for dividing 100 MHz clock into 50 MHz
* `util_ds_buf` - utility module for low-level pin plumbing
* `proc_sys_reset` - utility module for card initialization
