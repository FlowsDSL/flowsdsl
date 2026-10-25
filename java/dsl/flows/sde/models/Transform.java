// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.statements.*;
import dsl.flows.sde.models.types.BitType;
import dsl.flows.sde.models.types.Type;
import dsl.flows.sde.models.types.TypeReference;

import java.util.LinkedList;
import java.util.TreeSet;

public class Transform {
    public enum Direction {Input, Output}

    ;

    public static class Port implements VariableOrPort {
        public final SourceFilenameAndLocationRange locationRange;
        public final String name;
        public final TypeReference typeReference;
        public final Direction direction;
        public final int id;
        public TreeSet<StatementExpression> assignments = new TreeSet<>(Statement.COMPARATOR);

        public Port(SourceFilenameAndLocationRange locationRange, String name, TypeReference typeReference,
                    Direction direction, int id) {
            this.locationRange = locationRange;
            this.name = name;
            this.typeReference = typeReference;
            this.direction = direction;
            this.id = id;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public String getName() {
            return 'S' + name;
        }

        @Override
        public TypeReference getTypeReference(BitType bitType) {
            return typeReference;
        }

        @Override
        public TreeSet<StatementExpression> getAssignments() {
            return assignments;
        }

        @Override
        public SourceFilenameAndLocationRange getLocationRange() {
            return locationRange;
        }

        @Override
        public int compareTo(VariableOrPort other) {
            return getId() - other.getId();
        }
    }

    public static class PortGroup {
        public final SourceFilenameAndLocationRange locationRange;
        public final String name;
        public final TypeReference typeReference;
        public Connection.Kind connectionKind;
        public Port portA, portB;
        public int largestWriteWhatByteCount, dataFieldByteCount;
        public StatementVariableDeclaration writeAccumulatorValueVariable;
        public StatementVariableDeclaration writeAccumulatorByteCountVariable;
        public StatementVariableDeclaration writeAccumulatorLastVariable;
        public StatementVariableDeclaration readBusPosition;
        public TypeReference queueNonBlankTypeReference;

        public PortGroup(SourceFilenameAndLocationRange locationRange, String name, TypeReference typeReference) {
            this.locationRange = locationRange;
            this.name = name;
            this.typeReference = typeReference;
        }
    }

    public final SourceFilenameAndLocationRange locationRange;
    public final LinkedList<PortGroup> portGroups;
    public final Port errorPort;
    public final StatementBlock body;
    public final StatementStep entry;
    public final IdGenerator idGenerator;
    public LinkedList<StatementStep> regions = new LinkedList<>();
    public LinkedList<StatementIfWhile> ifWhiles = new LinkedList<>();
    public LinkedList<VariableOrPort> variablesOrPorts = new LinkedList<>();
    public TypeReference stateTypeReference;
    public TypeReference inputsTypeReference;
    public TypeReference stateAndInputsTypeReference;
    public boolean isInstantiated;

    public Transform(SourceFilenameAndLocationRange locationRange, LinkedList<PortGroup> portGroups,
                     StatementBlock body, Type bitType, IdGenerator idGenerator) {
        this.locationRange = locationRange;
        this.portGroups = portGroups;
        this.body = body;
        this.idGenerator = idGenerator;
        if (body != null) {
            entry = new StatementStep(new SourceFilenameAndLocationRange(locationRange.filename, locationRange.start,
                    locationRange.start), null, idGenerator.generateId());
            regions.add(entry);
        } else {
            entry = null;
        }
        errorPort = new Port(locationRange, "Error", new TypeReference(locationRange, "Bit", bitType, null),
                Direction.Output, idGenerator.generateId());
        PortGroup portGroup = new PortGroup(locationRange, "Error",
                new TypeReference(locationRange, "Bit", bitType, null));
        portGroup.connectionKind = Connection.Kind.Wire;
        portGroup.portA = errorPort;
        portGroups.add(portGroup);
    }

    public PortGroup findPortGroup(String name) {
        for (PortGroup entry : portGroups) {
            if (entry.name.equals(name)) {
                return entry;
            }
        }
        return null;
    }

    public Port findPort(String name) {
        for (PortGroup entry : portGroups) {
            if (entry.portA != null && entry.portA.name.equals(name)) {
                return entry.portA;
            }
            if (entry.portB != null && entry.portB.name.equals(name)) {
                return entry.portB;
            }
        }
        return null;
    }
}
