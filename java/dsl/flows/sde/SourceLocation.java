// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde;

public record SourceLocation(int line, int position) implements Comparable<SourceLocation> {
    public String toString() {
        return (line + 1) + ":" + (position + 1);
    }

    public String getNameSuffixForVariables() {
        return (line + 1) + "_" + (position + 1);
    }

    @Override
    public int compareTo(SourceLocation other) {
        int result = line - other.line;
        if (result == 0) {
            result = position - other.position;
        }
        return result;
    }
}
