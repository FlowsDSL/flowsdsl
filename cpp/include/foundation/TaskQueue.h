// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

#pragma once

#include <atomic>
#include <utility>

class TaskQueue {
    using FunctionPtr = void (*)(void*);
    static constexpr size_t BUFFER_SIZE = 0x1000000;

    struct TaskHeader {
        uint8_t size;
        FunctionPtr invokeAndDestruct;
    };

    std::atomic_int64_t start{0};
    uint8_t buffer[BUFFER_SIZE + 0x100]{};
    std::atomic_int64_t end{0};

    template <typename T>
    static void invokeAndDestruct(void* ptr) {
        auto cast = (T*)ptr;
        (*cast)();
        cast->~T();
    }

    TaskHeader* addCommon(const TaskHeader& header);

public:
    void execute();

    template <typename T>
    void add(T&& lambda) {
        static_assert(sizeof(TaskHeader) + sizeof(T) <= 0xff,
                      "Lambda's total size of captured variables is too big (current limit is 236 bytes)");
        // this call allocates the place for the event and waits for it to free up if it's still used
        // by consumer thread
        auto header = addCommon({.size = sizeof(T), .invokeAndDestruct = &invokeAndDestruct<T>});
        // move the lambda into its slot on the queue
        new(header + 1) T(std::forward<T>(lambda));
        std::atomic_thread_fence(std::memory_order_release);
        // and signal that the entry is ready
        header->size = sizeof(T);
    }

    void waitForRoom() const;
};
