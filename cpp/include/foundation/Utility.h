// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

#pragma once

#include <stdexcept>
#include <string>
#include <chrono>

class ApplicationException : public std::exception {
    std::string message;

public:
    explicit ApplicationException(std::string message);
    [[nodiscard]] const char* what() const noexcept final;
};

int openForWritingAndTruncateWithException(const std::string& filename);
void readWithException(int fileDescriptor, uint8_t* data, size_t);
void writeWithException(int fileDescriptor, const uint8_t* data, size_t);
