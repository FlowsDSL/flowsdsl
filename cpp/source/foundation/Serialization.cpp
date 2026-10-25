// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

#include <unistd.h>
#include "foundation/Serialization.h"

void Serializer::flush(bool const objectBoundary) {
    output.flush({.size = static_cast<size_t>(writer - output.getBufferStart()), .objectBoundary = objectBoundary});
    writer = output.getBufferStart();
    limit = output.getBufferLimit();
}

Serializer::Serializer(SerializerOutput& output) : output(output) {
    writer = output.getBufferStart();
    limit = output.getBufferLimit();
}

void Serializer::serializeIntegerBody(int64_t value) {
    // encoded value can be at most 10 bytes long
    if (writer + 10 > limit) {
        flush(false);
    }
    while (true) {
        if (const auto top58 = value >> 6; top58 == 0 || top58 == -1) {
            *writer++ = value & 0x7f;
            return;
        }
        *writer++ = (value & 0x7f) | 0x80;
        value >>= 7;
    }
}

void Serializer::serializeStringBody(const std::string& value) {
    serializeIntegerBody(static_cast<int64_t>(value.length()));
    size_t offset = 0;
    while (true) {
        size_t chunk = value.length() - offset;
        if (chunk == 0) {
            return;
        }
        if (writer == limit) {
            flush(false);
        }
        if (chunk > limit - writer) {
            chunk = limit - writer;
        }
        value.copy(reinterpret_cast<char*>(writer), chunk, offset);
        writer += chunk;
        offset += chunk;
    }
}

Serializer::~Serializer() {
    flush(true);
}

FileSerializerOutput::FileSerializerOutput(const std::string& filename) : buffer() {
    fileDescriptor = openForWritingAndTruncateWithException(filename);
    writeWithException(fileDescriptor, reinterpret_cast<const uint8_t*>("ISF"), 3);
}

uint8_t* FileSerializerOutput::getBufferStart() {
    return buffer;
}

uint8_t* FileSerializerOutput::getBufferLimit() {
    return &buffer[sizeof(buffer)];
}

void FileSerializerOutput::flush(FlushParameters const parameters) {
    if (parameters.size != 0) {
        writeWithException(fileDescriptor, buffer, parameters.size);
    }
}

FileSerializerOutput::~FileSerializerOutput() {
    close(fileDescriptor);
}
