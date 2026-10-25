// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde;

public class SourceLocationRange {
    public final SourceLocation start, end;

    public SourceLocationRange(SourceLocation start, SourceLocation end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return start + " - " + end;
    }
}
