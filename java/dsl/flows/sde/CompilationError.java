// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde;

public record CompilationError(String details, SourceFilenameAndLocationRange locationRange) {
    @Override
    public String toString() {
        return (locationRange != null ? locationRange.filename + ":" + locationRange.start + ": " : "") + "error: " +
                details;
    }
}
