// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

import dsl.flows.sde.SourceLocationRange;
import dsl.flows.sde.models.types.TypeReference;

import java.util.LinkedList;
import java.util.TreeMap;

public class SystemArchitecture {
    public static final int FROM_HOST_MESSAGE_WORD_WIDTH = 32;
    public static final int TO_HOST_MESSAGE_WORD_WIDTH = 31;

    public record FromCoreChannelLayout(int channelWidth, TypeReference tracingTypeReference, int tracingChannels,
                                        int totalChannels) {
    }

    public final SourceLocationRange locationRange;
    public final TreeMap<String, Instance> instances;
    public final LinkedList<Connection> connections;
    public Instance hostMessageRouter;
    public FromCoreChannelLayout fromCoreChannelLayout;
    public Integer toCoreDestinationBits;

    public SystemArchitecture(SourceLocationRange locationRange, TreeMap<String, Instance> instances,
                              LinkedList<Connection> connections) {
        this.locationRange = locationRange;
        this.instances = instances;
        this.connections = connections;
    }
}
