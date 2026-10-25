# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 Flows author <flows.dsl@proton.me>

if {[file exists flows.xpr]} {
    open_project flows.xpr
} else {
    create_project -force flows . -part XCU50-FSVH2104-2-E
    set_property board_part xilinx.com:au50:part0:1.3 [current_project]
    create_ip -name pcie4c_uscale_plus -vendor xilinx.com -library ip -version 1.0 -module_name PCIEBlock
    set_property -dict [list \
        CONFIG.PF0_LINK_STATUS_SLOT_CLOCK_CONFIG {false} \
        CONFIG.TL_PF_ENABLE_REG {1} \
        CONFIG.AXISTEN_IF_EXT_512_CQ_STRADDLE {false} \
        CONFIG.AXISTEN_IF_EXT_512_RQ_STRADDLE {false} \
        CONFIG.PL_LINK_CAP_MAX_LINK_SPEED {8.0_GT/s} \
        CONFIG.PL_LINK_CAP_MAX_LINK_WIDTH {X16} \
        CONFIG.pf0_bar1_enabled {true} \
        CONFIG.pf0_bar0_size {64} \
        CONFIG.pf0_bar1_size {4} \
        CONFIG.PCIE_BOARD_INTERFACE {pci_express_x16} \
        CONFIG.SYS_RST_N_BOARD_INTERFACE {pcie_perstn} \
        CONFIG.cfg_ctl_if {false} \
        CONFIG.cfg_fc_if {false} \
        CONFIG.cfg_mgmt_if {false} \
        CONFIG.cfg_pm_if {false} \
        CONFIG.cfg_status_if {false} \
        CONFIG.cfg_tx_msg_if {false} \
        CONFIG.rcv_msg_if {false} \
        CONFIG.tx_fc_if {false} \
        CONFIG.extended_tag_field {false} \
        CONFIG.pf0_aer_enabled {false} \
        CONFIG.pf0_msi_enabled {false} \
        CONFIG.pf0_vc_cap_enabled {false} \
    ] [get_ips PCIEBlock]
    create_ip -name util_ds_buf -vendor xilinx.com -library ip -version 2.2 -module_name UtilityBufferBlock
    set_property CONFIG.C_BUF_TYPE {IBUFDSGTE} [get_ips UtilityBufferBlock]
    create_ip -name axi_quad_spi -vendor xilinx.com -library ip -version 3.2 -module_name FlashProgrammerBlock
    set_property -dict [list \
        CONFIG.C_SPI_MEMORY {2} \
        CONFIG.C_SPI_MODE {2} \
        CONFIG.C_USE_STARTUP {1} \
        CONFIG.C_USE_STARTUP_INT {1} \
        CONFIG.C_FIFO_DEPTH {256} \
    ] [get_ips FlashProgrammerBlock]
    create_ip -name cms_subsystem -vendor xilinx.com -library ip -version 4.0 -module_name CMSSubsystemBlock
    create_ip -name clk_wiz -vendor xilinx.com -library ip -version 6.0 -module_name ClockWizardBlock50MHz
    set_property -dict [list \
        CONFIG.CLKOUT1_DRIVES {Buffer} \
        CONFIG.CLKOUT1_JITTER {167.017} \
        CONFIG.CLKOUT1_PHASE_ERROR {114.212} \
        CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {50} \
        CONFIG.CLKOUT2_DRIVES {Buffer} \
        CONFIG.CLKOUT3_DRIVES {Buffer} \
        CONFIG.CLKOUT4_DRIVES {Buffer} \
        CONFIG.CLKOUT5_DRIVES {Buffer} \
        CONFIG.CLKOUT6_DRIVES {Buffer} \
        CONFIG.CLKOUT7_DRIVES {Buffer} \
        CONFIG.MMCM_BANDWIDTH {OPTIMIZED} \
        CONFIG.MMCM_CLKFBOUT_MULT_F {8} \
        CONFIG.MMCM_CLKOUT0_DIVIDE_F {16} \
        CONFIG.MMCM_COMPENSATION {AUTO} \
        CONFIG.PRIMITIVE {PLL} \
        CONFIG.PRIM_SOURCE {Global_buffer} \
        CONFIG.RESET_PORT {resetn} \
        CONFIG.RESET_TYPE {ACTIVE_LOW} \
    ] [get_ips ClockWizardBlock50MHz]
    create_ip -name proc_sys_reset -vendor xilinx.com -library ip -version 5.0 -module_name ProcessorSystemResetBlock
    set_property -dict [list \
        CONFIG.C_AUX_RESET_HIGH {0} \
        CONFIG.C_AUX_RST_WIDTH {1} \
        CONFIG.C_EXT_RESET_HIGH {0} \
        CONFIG.C_EXT_RST_WIDTH {1} \
    ] [get_ips ProcessorSystemResetBlock]
    create_ip -name gtwizard_ultrascale -vendor xilinx.com -library ip -version 1.7 -module_name EthernetSerializerDeserializerBlock
    set_property CONFIG.preset {GTY-10GBASE-R} [get_ips EthernetSerializerDeserializerBlock]
    set_property -dict [list \
      CONFIG.RX_REFCLK_FREQUENCY {161.1328125} \
      CONFIG.TX_REFCLK_FREQUENCY {161.1328125} \
      CONFIG.CHANNEL_ENABLE {X0Y31 X0Y30 X0Y29 X0Y28} \
      CONFIG.LOCATE_RX_USER_CLOCKING {CORE} \
      CONFIG.LOCATE_TX_USER_CLOCKING {CORE} \
      CONFIG.RX_DATA_DECODING {64B66B} \
      CONFIG.TX_DATA_ENCODING {64B66B} \
      CONFIG.RX_USER_DATA_WIDTH {32} \
      CONFIG.TX_USER_DATA_WIDTH {32} \
    ] [get_ips EthernetSerializerDeserializerBlock]
    add_files -fileset constrs_1 Top.xdc
    generate_target all [get_ips]
    create_ip_run [get_files -of_objects [get_fileset sources_1] flows.srcs/sources_1/ip/PCIEBlock/PCIEBlock.xci]
    create_ip_run [get_files -of_objects [get_fileset sources_1] flows.srcs/sources_1/ip/UtilityBufferBlock/UtilityBufferBlock.xci]
    create_ip_run [get_files -of_objects [get_fileset sources_1] flows.srcs/sources_1/ip/FlashProgrammerBlock/FlashProgrammerBlock.xci]
    create_ip_run [get_files -of_objects [get_fileset sources_1] flows.srcs/sources_1/ip/CMSSubsystemBlock/CMSSubsystemBlock.xci]
    create_ip_run [get_files -of_objects [get_fileset sources_1] flows.srcs/sources_1/ip/ClockWizardBlock50MHz/ClockWizardBlock50MHz.xci]
    create_ip_run [get_files -of_objects [get_fileset sources_1] flows.srcs/sources_1/ip/ProcessorSystemResetBlock/ProcessorSystemResetBlock.xci]
    create_ip_run [get_files -of_objects [get_fileset sources_1] flows.srcs/sources_1/ip/EthernetSerializerDeserializerBlock/EthernetSerializerDeserializerBlock.xci]
    launch_runs PCIEBlock_synth_1 UtilityBufferBlock_synth_1 FlashProgrammerBlock_synth_1 CMSSubsystemBlock_synth_1 ClockWizardBlock50MHz_synth_1 ProcessorSystemResetBlock_synth_1 EthernetSerializerDeserializerBlock_synth_1 -jobs 11
    wait_on_runs PCIEBlock_synth_1 UtilityBufferBlock_synth_1 FlashProgrammerBlock_synth_1 CMSSubsystemBlock_synth_1 ClockWizardBlock50MHz_synth_1 ProcessorSystemResetBlock_synth_1 EthernetSerializerDeserializerBlock_synth_1
}
add_files -fileset sources_1 $::env(ALL_SOURCE_FILES)
reset_run synth_1
reset_run impl_1
update_compile_order -fileset sources_1
synth_design -top Top -part XCU50-FSVH2104-2-E -flatten_hierarchy none
write_checkpoint -force synthesized.dcp
opt_design
place_design
route_design
write_checkpoint -force routed.dcp
phys_opt_design
report_drc
write_checkpoint -force implemented.dcp
set failing_paths [get_timing_paths -nworst 1 -filter {SLACK < 0}]
if {[llength $failing_paths] > 0} {
    foreach path $failing_paths {
        puts [format {#FailedTiming %s} [get_nets -of $path]]
    }
    exit
}
set latches [all_latches]
if {[llength $latches] > 0} {
    foreach latch $latches {
        set name [get_property NAME $latch]
        puts [format {#Latch %s} $name]
    }
    exit
}
write_bitstream -force top.bit
write_cfgmem -force -format mcs -interface spix4 -size 128 -loadbit "up 0x01002000 top.bit" -file "top.mcs"
puts {#Success}
