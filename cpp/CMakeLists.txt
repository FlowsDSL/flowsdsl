# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 Flows author <flows.dsl@proton.me>

cmake_minimum_required(VERSION 3.22)
project(FlowsCppDemo)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "-g")
set(CMAKE_CXX_FLAGS_RELEASE -O3)
include_directories(include)

add_library(UtilityObject OBJECT source/foundation/Utility.cpp)

add_library(SerializationObject OBJECT source/foundation/Serialization.cpp)

add_library(TaskQueueObject OBJECT source/foundation/TaskQueue.cpp)

if(NOT FLOWS_BUILD_DIRECTORY)
    message(FATAL_ERROR "Set FLOWS_BUILD_DIRECTORY to the SDE build directory: cmake -DFLOWS_BUILD_DIRECTORY=<build directory> ...")
endif()

function(add_demo name project)
    set(generated ${FLOWS_BUILD_DIRECTORY}/${project}.flows/withTrace)
    if(EXISTS ${generated}/fpga.cpp)
        add_executable(${name} source/demo/${name}.cpp ${generated}/fpga.cpp ${generated}/fpgaTracing.cpp)
        target_include_directories(${name} PRIVATE ${generated})
        target_link_libraries(${name} UtilityObject SerializationObject TaskQueueObject)
    else()
        message(STATUS "Skipping ${name}: ${generated} not built yet")
    endif()
endfunction()

add_demo(PingResponderDemo PingResponder)
add_demo(CalculatorDemo Calculator)
