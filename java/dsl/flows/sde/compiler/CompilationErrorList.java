// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.CompilationError;
import dsl.flows.sde.SourceFilenameAndLocationRange;

import java.util.LinkedList;

public class CompilationErrorList {
    public final LinkedList<CompilationError> list = new LinkedList<>();

    public void addError(String error, SourceFilenameAndLocationRange locationRange) {
        list.add(new CompilationError(error, locationRange));
    }
}
