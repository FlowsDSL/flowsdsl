// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.types.TypeReference;

public class Connection {
    public enum Kind {Wire, WireWithBackpressure, Queue}

    public record ChannelRange(int start, int end) {
    }

    public static class Endpoint {
        public final SourceFilenameAndLocationRange locationRange;
        public final String instanceName;
        public final String portGroupName;
        public Instance resolvedInstance;
        public Transform.PortGroup resolvedPortGroup;

        public Endpoint(SourceFilenameAndLocationRange locationRange, String instanceName, String portGroupName) {
            this.locationRange = locationRange;
            this.instanceName = instanceName;
            this.portGroupName = portGroupName;
        }
    }

    public final SourceFilenameAndLocationRange locationRange;
    public final Kind kind;
    public final Endpoint source;
    public final Endpoint destination;
    public ChannelRange fromCoreChannelRange;
    public TypeReference fromCoreNonBlankType;
    public TypeReference toCoreNonBlankType;

    public Connection(SourceFilenameAndLocationRange locationRange, Kind kind, Endpoint source, Endpoint destination) {
        this.locationRange = locationRange;
        this.kind = kind;
        this.source = source;
        this.destination = destination;
    }

    public String getFullName() {
        return source.instanceName + "_" + source.portGroupName + "_" + destination.instanceName + "_" +
                destination.portGroupName;
    }
}
