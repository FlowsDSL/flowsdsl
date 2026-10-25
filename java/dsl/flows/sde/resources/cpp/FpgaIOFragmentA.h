#pragma once

#include <chrono>
#include <array>
#include <vector>
#include <string>
#include <cstdint>
#include <variant>
#include "foundation/TaskQueue.h"
#include "foundation/Serialization.h"

namespace fpga {

struct FpgaException : public std::exception {
    std::string details;
    explicit FpgaException(std::string details) : details(std::move(details)) {}
    [[nodiscard]] const char *what() const noexcept override { return details.c_str(); }
};
