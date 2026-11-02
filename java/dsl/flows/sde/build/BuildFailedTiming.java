// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.build;

import dsl.flows.sde.ui.SourceGraphEdge;

public final class BuildFailedTiming extends BuildResult {
    public static final BuildFailedTiming BUILD_FAILED_TIMING_NO_DETAILS = new BuildFailedTiming(0,
            SourceGraphEdge.EMPTY_ARRAY);

    public final double slackFraction;
    public final SourceGraphEdge[] failedPath;

    BuildFailedTiming(double slackFraction, SourceGraphEdge[] failedPath) {
        this.slackFraction = slackFraction;
        this.failedPath = failedPath;
    }
}
