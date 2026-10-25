// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

#include <memory.h>
#include "foundation/TaskQueue.h"

#include "foundation/Utility.h"

/*
  Task queue works by filling circular buffer with variable length entries, consisting
  of header and lambda captures. Start and end pointer determine the utilized section
  of the queue. Because the complete process of adding an entry is not atomic, it is
  possible that the space has been reserved but entry hasn't been written completely yet.
  In order to make sure that entry is written fully before being read, one-byte size
  field is written last after memory fence. Another potential issue is overflowing the
  queue - to make sure it doesn't happen, producers can wait until consumer finishes pulling
  tasks out of the queue that may overlap with new one (if they don't the exception is
  thrown). Also, once the consumer is done processing the task, it clears the entire entry
  because (due to variable length) a new entry may end up starting in the middle of old one
  and size field is relied upon to signal completion of writing of the entry. Finally,
  there's some additional space at the end of the queue because lambda has to live in
  contiguous range of addresses (it may be possible to wrap it around using virtual address
  space mapping but the additional complexity is just not worth the trouble).

  std::function does small object optimization, however the threshold for allocation is
  pretty low (24 bytes on arm64 clang16), particularly for logged objects. Supporting
  variable-length task lambdas allows avoiding allocations on the critical path.
 */
TaskQueue::TaskHeader* TaskQueue::addCommon(const TaskHeader& header) {
    const auto location = end.fetch_add(static_cast<int64_t>(sizeof(TaskHeader) + header.size));
    if (location + sizeof(TaskHeader) + header.size - start >= BUFFER_SIZE) {
        throw ApplicationException("Queue overflow");
    }
    const auto target = reinterpret_cast<TaskHeader*>(buffer + (location & (BUFFER_SIZE - 1)));
    target->invokeAndDestruct = header.invokeAndDestruct;
    return target;
}

void TaskQueue::execute() {
    if (start == end) {
        return;
    }
    const auto header = reinterpret_cast<TaskHeader*>(buffer + (start & (BUFFER_SIZE - 1)));
    const auto size = header->size;
    if (size == 0) {
        return;
    }
    std::atomic_thread_fence(std::memory_order_consume);
    header->invokeAndDestruct(header + 1);
    memset(header, 0, sizeof(TaskHeader) + size);
    std::atomic_thread_fence(std::memory_order_release);
    start += static_cast<int64_t>(sizeof(TaskHeader)) + size;
}

void TaskQueue::waitForRoom() const {
    while (end.load() - start >= BUFFER_SIZE / 2) {
    }
}
