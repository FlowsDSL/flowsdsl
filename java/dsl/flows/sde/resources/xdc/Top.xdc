# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 Flows author <flows.dsl@proton.me>

create_clock -period 10.0 -name sysclk2 [get_ports "SYSCLK2_P"]
create_clock -period 10.0 -name ref_clk [get_ports pcie_mgt_clk_p]
set_property PACKAGE_PIN AF9 [get_ports pcie_mgt_clk_p]
set_property PACKAGE_PIN J18 [get_ports hbm_cattrip]
set_property -dict {IOSTANDARD LVCMOS18 DRIVE 4} [get_ports hbm_cattrip]
set_property PACKAGE_PIN BB26 [get_ports satellite_uart_rxd]
set_property -dict {IOSTANDARD LVCMOS18} [get_ports satellite_uart_rxd]
set_property PACKAGE_PIN BB25 [get_ports satellite_uart_txd]
set_property -dict {IOSTANDARD LVCMOS18 DRIVE 4} [get_ports satellite_uart_txd]
set_property PACKAGE_PIN C16 [get_ports satellite_gpio[0]]
set_property -dict {IOSTANDARD LVCMOS18} [get_ports satellite_gpio[0]]
set_property PACKAGE_PIN C17 [get_ports satellite_gpio[1]]
set_property -dict {IOSTANDARD LVCMOS18} [get_ports satellite_gpio[1]]

create_clock -period 6.206  -name gtrefclk0      [get_ports "SYNCE_CLK_P"]
set_property PACKAGE_PIN N37      [get_ports "SYNCE_CLK_N"]          ;# Bank 131 - MGTREFCLK0N_131
set_property PACKAGE_PIN N36      [get_ports "SYNCE_CLK_P"]          ;# Bank 131 - MGTREFCLK0P_131

set tdata_trace_delay_max 0.47
set tdata_trace_delay_min 0.35
set tclk_trace_delay_max 0.37
set tclk_trace_delay_min 0.31
set tco_max 7.7
set tco_min 0.25
set tsu 1.75
set th 2.5
create_generated_clock -name clk_sck -source [get_pins -hierarchical *FlashProgrammerInstance/ext_spi_clk] [get_pins -hierarchical *inst/CCLK] -edges {1 3 5}
set_input_delay -clock clk_sck -max [expr $tco_max + $tdata_trace_delay_max + $tclk_trace_delay_max] [get_pins -hierarchical *STARTUP*/DATA_IN[*]] -clock_fall;
set_input_delay -clock clk_sck -min [expr $tco_min + $tdata_trace_delay_min + $tclk_trace_delay_min] [get_pins -hierarchical *STARTUP*/DATA_IN[*]] -clock_fall;
set_multicycle_path 2 -setup -from clk_sck -to [get_clocks -of_objects [get_pins -hierarchical *FlashProgrammerInstance/ext_spi_clk]]
set_multicycle_path 1 -hold -end -from clk_sck -to [get_clocks -of_objects [get_pins -hierarchical *FlashProgrammerInstance/ext_spi_clk]]
set_output_delay -clock clk_sck -max [expr $tsu + $tdata_trace_delay_max - $tclk_trace_delay_min] [get_pins -hierarchical *STARTUP*/DATA_OUT[*]];
set_output_delay -clock clk_sck -min [expr $tdata_trace_delay_min -$th - $tclk_trace_delay_max] [get_pins -hierarchical *STARTUP*/DATA_OUT[*]];
set_multicycle_path 1 -hold -from [get_clocks -of_objects [get_pins -hierarchical *FlashProgrammerInstance/ext_spi_clk]] -to clk_sck

set_property CONFIG_VOLTAGE 1.8 [current_design]
set_property BITSTREAM.CONFIG.CONFIGFALLBACK Enable [current_design]
set_property BITSTREAM.GENERAL.COMPRESS TRUE [current_design]
set_property CONFIG_MODE SPIx4 [current_design]
set_property BITSTREAM.CONFIG.SPI_BUSWIDTH 4 [current_design]
set_property BITSTREAM.CONFIG.EXTMASTERCCLK_EN disable [current_design]
set_property BITSTREAM.CONFIG.CONFIGRATE 63.8 [current_design]
set_property BITSTREAM.CONFIG.SPI_FALL_EDGE YES [current_design]
set_property BITSTREAM.CONFIG.UNUSEDPIN Pullup [current_design]
set_property BITSTREAM.CONFIG.SPI_32BIT_ADDR Yes [current_design]

set_property PACKAGE_PIN G16 [get_ports "SYSCLK2_N"]
set_property IOSTANDARD LVDS [get_ports "SYSCLK2_N"]
set_property PACKAGE_PIN G17 [get_ports "SYSCLK2_P"]
set_property IOSTANDARD LVDS [get_ports "SYSCLK2_P"]
set_property DQS_BIAS TRUE [get_ports "SYSCLK2_P"]

set_property LOC BUFGCE_X0Y109 [get_cells Clock50MHzWizardInstance/inst/clkin1_bufg]

create_pblock pblock_core
add_cells_to_pblock [get_pblocks pblock_core] [get_cells [list CoreInstance EthernetSerializerDeserializer EthernetScrambler EthernetUnscrambler EthernetPCSAndMACInstance]]
resize_pblock pblock_core -add {SLICE_X0Y300:SLICE_X94Y479 DSP48E2_X0Y114:DSP48E2_X12Y185 RAMB18_X0Y120:RAMB18_X6Y191 RAMB36_X0Y60:RAMB36_X6Y95 URAM288_X0Y80:URAM288_X0Y127}

create_pblock pblock_pcie
add_cells_to_pblock [get_pblocks pblock_pcie] [get_cells PCIEInstance]
resize_pblock pblock_pcie -add {SLICE_X146Y0:SLICE_X232Y180 DSP48E2_X19Y0:DSP48E2_X31Y65 PCIE4CE4_X1Y0:PCIE4CE4_X1Y0 RAMB18_X10Y0:RAMB18_X13Y71 RAMB36_X10Y0:RAMB36_X13Y35 URAM288_X3Y0:URAM288_X4Y47}

create_pblock pblock_from_core_fifo
add_cells_to_pblock [get_pblocks pblock_from_core_fifo] [get_cells FromCoreQueue]
resize_pblock pblock_from_core_fifo -add CLOCKREGION_X5Y3:CLOCKREGION_X5Y3
