// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde;

public class ParseException extends Exception {
    public final SourceFilenameAndLocationRange locationRange;

    public ParseException(SourceFilenameAndLocationRange locationRange, String error) {
        super(error);
        this.locationRange = locationRange;
    }

    @Override
    public String toString() {
        return getMessage() + " at " + locationRange;
    }
}
