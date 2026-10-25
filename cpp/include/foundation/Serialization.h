// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

#pragma once

#include <string>
#include <vector>
#include <variant>
#include <list>
#include <memory>
#include "foundation/Utility.h"

class SerializerOutput {
public:
    struct FlushParameters {
        size_t size;
        bool objectBoundary;
    };

    virtual uint8_t* getBufferStart() = 0;

    virtual uint8_t* getBufferLimit() = 0;

    virtual void flush(FlushParameters parameters) = 0;

    virtual ~SerializerOutput() = default;
};

class Serializer final {
    SerializerOutput& output;
    uint8_t* writer;
    uint8_t* limit;

public:
    explicit Serializer(SerializerOutput& output);

    void serializeIntegerBody(int64_t value);

    void serializeStringBody(const std::string& value);

    void flush(bool objectBoundary);

    ~Serializer();
};

class FileSerializerOutput final : public SerializerOutput {
    int fileDescriptor;
    uint8_t buffer[0x1000];

public:
    explicit FileSerializerOutput(const std::string& filename);

    FileSerializerOutput(const FileSerializerOutput&) = delete;
    FileSerializerOutput(FileSerializerOutput&&) = delete;

    uint8_t* getBufferStart() override;
    uint8_t* getBufferLimit() override;
    void flush(FlushParameters) override;

    ~FileSerializerOutput() override;
};
