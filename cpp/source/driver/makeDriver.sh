#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 Flows author <flows.dsl@proton.me>

mkdir -p build/driver
ln -sfr cpp/source/driver/fpga_driver.c build/driver/fpga_driver.c
ln -sfr cpp/source/driver/Makefile build/driver/Makefile
cd build/driver
make -C /lib/modules/`uname -r`/build M=`pwd` clean 
make -C /lib/modules/`uname -r`/build M=`pwd` modules
