// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde;

public class SourceFilenameAndLocationRange extends SourceLocationRange {
    public final String filename;

    public SourceFilenameAndLocationRange(String filename, SourceLocation start, SourceLocation end) {
        super(start, end);
        this.filename = filename;
    }

    public String toString() {
        return filename + ": " + super.toString();
    }
}
