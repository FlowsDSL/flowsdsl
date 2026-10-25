// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

import dsl.flows.sde.SourceFilenameAndLocationRange;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeSet;

public class Instance {
    public final SourceFilenameAndLocationRange locationRange;
    public final String transformName;
    public final ArrayList<Connection> connectionsWithInstanceAsDestination = new ArrayList<>();
    public final LinkedList<Connection> connectionsWithInstanceAsSource = new LinkedList<>();
    public final TreeSet<String> outputEndpoints = new TreeSet<>();
    public final TreeSet<String> inputEndpoints = new TreeSet<>();
    public Transform resolvedTransform;
    public Integer toCoreDestinationIndex;

    public Instance(SourceFilenameAndLocationRange locationRange, String transformName) {
        this.locationRange = locationRange;
        this.transformName = transformName;
    }
}
