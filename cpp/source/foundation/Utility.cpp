// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <cstring>
#include <optional>
#include "foundation/Utility.h"

ApplicationException::ApplicationException(std::string message) : message(std::move(message)) {
}

const char* ApplicationException::what() const noexcept {
    return message.c_str();
}

uint64_t getGreatestCommonDivisor(uint64_t a, uint64_t b) {
    while (a != 0) {
        const uint64_t newB = a;
        a = b % a;
        b = newB;
    }
    return b;
}

int openForWritingWithException(const std::string& filename) {
    const auto result = open(filename.c_str(), O_RDWR | O_CREAT, 0644);
    if (result == -1) {
        throw ApplicationException("Could not open " + filename + " for writing: " + strerror(errno));
    }
    return result;
}

int openForWritingAndTruncateWithException(const std::string& filename) {
    const auto result = openForWritingWithException(filename);
    if (ftruncate(result, 0) == -1) {
        throw ApplicationException("Could not ftruncate " + filename + ": " + strerror(errno));
    }
    return result;
}

void readWithException(int const fileDescriptor, uint8_t* data, size_t const size) {
    const auto limit = data + size;
    while (data != limit) {
        const auto chunk = read(fileDescriptor, data, limit - data);
        if (chunk < 0) {
            throw ApplicationException(std::string{"Could not read from file: "} + strerror(errno));
        }
        if (chunk == 0) {
            throw ApplicationException("Could not read from file: end of file reached");
        }
        data += chunk;
    }
}

void writeWithException(int const fileDescriptor, const uint8_t* data, size_t const size) {
    const auto limit = data + size;
    while (data != limit) {
        const auto chunk = write(fileDescriptor, data, limit - data);
        if (chunk == -1) {
            throw ApplicationException(std::string{"Could not write to file: "} + strerror(errno));
        }
        data += chunk;
    }
}
