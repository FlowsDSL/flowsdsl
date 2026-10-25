// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

#include <unistd.h>

#include "fpga.h"

struct PrintingListener : fpga::FpgaIO::Listener
{
    void HandleError(fpga::FpgaIO::Error error) override
    {
        std::string description{fpga::FpgaIO::GetErrorDescription(error)};
        printf("received error: %s\n", description.c_str());
    }

    void ReceiveMessageFromCMSBridge(const fpga::CMSReadResponse& response) override
    {
        printf("FPGA chip temperature: %d degrees Celsius\n", response.Value);
    }

    void ReceiveMessageFromCalculator(const fpga::CalculatorResult& result) override
    {
        printf("Result: %d\n", result.Value);
    }
};

int main()
{
    PrintingListener listener;
    std::unique_ptr<TaskQueue> taskQueue = std::make_unique<TaskQueue>();
    fpga::FpgaIO fpgaIO("/dev/fpga_dma_device", listener, "/tmp", taskQueue.get());
    fpgaIO.SendToHostMessageRouter(fpga::StartTracing{});
    fpgaIO.SendToCalculator(fpga::GCDRequest{.A = 12300, .B = 41});
    fpgaIO.SendToCalculator(fpga::MultiplyRequest{.A = 31, .B = 53});
    while (true)
    {
        fpgaIO.Poll();
        taskQueue->execute();
    }
}
