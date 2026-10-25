// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.*;
import dsl.flows.sde.models.expressions.*;
import dsl.flows.sde.models.statements.*;
import dsl.flows.sde.models.terminals.*;
import dsl.flows.sde.models.types.OneOfType;
import dsl.flows.sde.models.types.StructType;
import dsl.flows.sde.models.types.Type;
import dsl.flows.sde.models.types.TypeReference;

import java.util.*;

public class Compiler {
    public final CompilationElements compilationElements;
    public final CompilationErrorList compilationErrorList;

    public Compiler(CompilationElements compilationElements) {
        this.compilationElements = compilationElements;
        compilationErrorList = new CompilationErrorList();
    }

    public static void resolveTypeReference(CompilationElements compilationElements,
                                            CompilationErrorList compilationErrorList, TypeReference typeReference) {
        typeReference.resolvedType = compilationElements.types.get(typeReference.name);
        if (typeReference.resolvedType == null) {
            compilationErrorList.addError("Missing type '" + typeReference.name + "'", typeReference.locationRange);
        }
    }

    private void resolveInstance(Connection.Endpoint endpoint) {
        endpoint.resolvedInstance = compilationElements.systemArchitecture.instances.get(endpoint.instanceName);
        if (endpoint.resolvedInstance == null) {
            compilationErrorList.addError("Instance '" + endpoint.instanceName + "' doesn't exist",
                    endpoint.locationRange);
        }
    }

    private void resolveEndpoint(Connection.Endpoint endpoint) {
        Transform.PortGroup result = endpoint.resolvedInstance.resolvedTransform.findPortGroup(endpoint.portGroupName);
        if (result == null) {
            compilationErrorList.addError("Port group '" + endpoint.portGroupName + "' doesn't exist",
                    endpoint.locationRange);
            return;
        }
        endpoint.resolvedPortGroup = result;
    }

    public void compile() throws IllegalAccessException {
        if (compilationElements.systemArchitecture == null) {
            compilationErrorList.addError("Please provide system block", null);
            return;
        }
        addImplicitTransforms();
        resolveInstances();
        resolveTypeDefinitions();
        for (Transform transform : compilationElements.transforms.values()) {
            for (Transform.PortGroup entry : transform.portGroups) {
                resolveTypeReference(compilationElements, compilationErrorList, entry.typeReference);
            }
        }
        resolveEndpoints();
        TypeSorterAndSizer typeSorterAndSizer = new TypeSorterAndSizer(compilationErrorList);
        sizeAndSortDeclaredTypes(typeSorterAndSizer);
        for (Map.Entry<String, Transform> transformEntry : compilationElements.transforms.entrySet()) {
            Transform transform = transformEntry.getValue();
            if (transform.body != null) {
                new NameAndTypeResolver(compilationElements, compilationErrorList,
                        transform).resolveNamesAndTypeReferences(transform.body);
                addImplicitVariablesForMacros(transform);
                MacroReplacer.replaceMacros(transform.body, transform, compilationElements.BIT_TYPE,
                        compilationElements.BLANK_TYPE);
                Statement exitStatement = new SuccessorAndPredecessorListBuilder(transform,
                        compilationErrorList).buildSuccessorAndPredecessorLists();
                if (exitStatement != null) {
                    compilationErrorList.addError(
                            "Control reaches the end of transform body after this statement, this is not allowed",
                            exitStatement.locationRange);
                } else {
                    for (StatementStep region : transform.regions) {
                        compileTransformRegion(region, transform);
                    }
                }
                createStateAndInputTypes(transform, transformEntry.getKey(), typeSorterAndSizer);
            }
            if (transform != compilationElements.systemArchitecture.hostMessageRouter.resolvedTransform) {
                addType(transform.stateAndInputsTypeReference, transform.locationRange, typeSorterAndSizer);
            }
        }
        verifyFromCoreMessages();
        buildFromCoreChannelLayout(typeSorterAndSizer);
        for (Connection connection : compilationElements.systemArchitecture.connections) {
            if (connection.source.resolvedPortGroup != null && connection.destination.resolvedPortGroup != null &&
                    connection.source.resolvedPortGroup.typeReference.resolvedType !=
                            connection.destination.resolvedPortGroup.typeReference.resolvedType) {
                compilationErrorList.addError(
                        "Types of connecting port groups '" + connection.source.instanceName + "." +
                                connection.source.portGroupName + "' and '" + connection.destination.instanceName +
                                "." + connection.destination.portGroupName + "' must match", connection.locationRange);
            }
        }
        compilationElements.typeTopologicalOrder = typeSorterAndSizer.result.toArray(new String[0]);
    }

    private void verifyFromCoreMessages() {
        for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination) {
            if (connection.source.resolvedPortGroup != null &&
                    connection.source.resolvedPortGroup.typeReference.resolvedType != null) {
                connection.fromCoreNonBlankType = getNonBlankType(
                        connection.source.resolvedPortGroup.typeReference.resolvedType, compilationElements.BLANK_TYPE);
                if (connection.fromCoreNonBlankType == null) {
                    compilationErrorList.addError(
                            "Type of connection to HostMessageRouter must be oneof with only two options and 'Blank' as first one",
                            connection.locationRange);
                }
            }
        }
    }

    private void createStateAndInputTypes(Transform transform, String transformName,
                                          TypeSorterAndSizer typeSorterAndSizer) {
        transform.stateTypeReference = new TypeReference(transform.locationRange, transformName + "State",
                new StructType(collectStateFields(transform).toArray(StructType.Field.EMPTY_ARRAY)), null);
        addType(transform.stateTypeReference, transform.locationRange, typeSorterAndSizer);
        transform.inputsTypeReference = new TypeReference(transform.locationRange, transformName + "Inputs",
                new StructType(collectInputFields(transform).toArray(StructType.Field.EMPTY_ARRAY)), null);
        addType(transform.inputsTypeReference, transform.locationRange, typeSorterAndSizer);
        transform.stateAndInputsTypeReference = new TypeReference(transform.locationRange,
                transformName + "StateAndInputs", new StructType(
                new StructType.Field[]{new StructType.Field("State", transform.stateTypeReference),
                        new StructType.Field("Inputs", transform.inputsTypeReference)}), null);
    }

    private void buildFromCoreChannelLayout(TypeSorterAndSizer typeSorterAndSizer) {
        LinkedList<StructType.Field> fromCoreMessagesWordFields = new LinkedList<>();
        for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination) {
            if (connection.source.resolvedPortGroup != null && connection.source.resolvedPortGroup.portA != null) {
                fromCoreMessagesWordFields.add(new StructType.Field(connection.source.instanceName,
                        connection.source.resolvedPortGroup.portA.typeReference));
            }
        }
        TypeReference fromCoreMessagesTypeReference = new TypeReference(null, "FromCoreMessagesWord",
                new StructType(fromCoreMessagesWordFields.toArray(StructType.Field.EMPTY_ARRAY)), null);
        addType(fromCoreMessagesTypeReference, null, typeSorterAndSizer);
        ArrayList<StructType.Field> tracingAndFromCoreMessageWordFields = new ArrayList<>();
        TypeReference tracingTypeReference;
        if (compilationElements.includeTracing) {
            LinkedList<StructType.Field> tracingFields = new LinkedList<>();
            for (Map.Entry<String, Instance> entry : compilationElements.systemArchitecture.instances.entrySet()) {
                Instance instance = entry.getValue();
                if (instance != compilationElements.systemArchitecture.hostMessageRouter &&
                        instance.resolvedTransform != null) {
                    tracingFields.add(new StructType.Field(entry.getKey(),
                            instance.resolvedTransform.stateAndInputsTypeReference));
                }
            }
            if (compilationElements.includeRawEthernetFrames) {
                tracingFields.add(new StructType.Field("RawUnscrambledEthernet",
                        new TypeReference(null, "RawUnscrambledEthernetWord",
                                compilationElements.types.get("RawUnscrambledEthernetWord"), null)));
            }
            tracingTypeReference = new TypeReference(null, "TraceWord",
                    new StructType(tracingFields.toArray(StructType.Field.EMPTY_ARRAY)), null);
            addType(tracingTypeReference, null, typeSorterAndSizer);
            tracingAndFromCoreMessageWordFields.add(new StructType.Field("Trace", tracingTypeReference));
        } else {
            tracingTypeReference = null;
        }
        tracingAndFromCoreMessageWordFields.add(
                new StructType.Field("FromCoreMessages", fromCoreMessagesTypeReference));
        TypeReference tracingAndFromCoreMessagesWord = new TypeReference(null, "TraceAndFromCoreMessagesWord",
                new StructType(tracingAndFromCoreMessageWordFields.toArray(StructType.Field.EMPTY_ARRAY)), null);
        addType(tracingAndFromCoreMessagesWord, null, typeSorterAndSizer);
        int fromCoreChannelWidth = SystemArchitecture.TO_HOST_MESSAGE_WORD_WIDTH - 1;
        while (true) {
            int fromCoreTotalChannels = tracingTypeReference != null ?
                    getChannelCount(tracingTypeReference.resolvedType, fromCoreChannelWidth) : 0;
            for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination) {
                if (connection.fromCoreNonBlankType != null && connection.fromCoreNonBlankType.resolvedType != null) {
                    fromCoreTotalChannels += getChannelCount(connection.fromCoreNonBlankType.resolvedType,
                            fromCoreChannelWidth);
                }
            }
            if (fromCoreTotalChannels + 2 <=
                    (1 << (SystemArchitecture.TO_HOST_MESSAGE_WORD_WIDTH - fromCoreChannelWidth))) {
                break;
            }
            fromCoreChannelWidth--;
            if (fromCoreChannelWidth == 0) {
                throw new RuntimeException("Internal error, cannot find tracing chunk bits");
            }
        }
        int fromCoreTraceChannels = tracingTypeReference != null ?
                getChannelCount(tracingTypeReference.resolvedType, fromCoreChannelWidth) : 0;
        int fromCoreTotalChannels = fromCoreTraceChannels;
        for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsSource) {
            if (connection.destination.resolvedPortGroup != null) {
                if (connection.kind == Connection.Kind.Wire) {
                    TypeReference connectionTypeReference = connection.destination.resolvedPortGroup.typeReference;
                    if (connectionTypeReference.resolvedType != null) {
                        connection.toCoreNonBlankType = getNonBlankType(connectionTypeReference.resolvedType,
                                compilationElements.BLANK_TYPE);
                        if (connection.toCoreNonBlankType != null) {
                            compilationElements.addOneOfWrapper(connectionTypeReference, connection.toCoreNonBlankType);
                        } else {
                            compilationErrorList.addError(
                                    "Wire connections from HostMessageRouter must be oneof with only two options and 'Blank' as first one",
                                    connection.locationRange);
                        }
                    }
                } else if (connection.kind == Connection.Kind.Queue) {
                    connection.toCoreNonBlankType = connection.destination.resolvedPortGroup.typeReference;
                }
            }
        }
        for (Connection connection : compilationElements.systemArchitecture.hostMessageRouter.connectionsWithInstanceAsDestination) {
            int end = fromCoreTotalChannels;
            if (connection.fromCoreNonBlankType != null && connection.fromCoreNonBlankType.resolvedType != null) {
                end += getChannelCount(connection.fromCoreNonBlankType.resolvedType, fromCoreChannelWidth);
            }
            connection.fromCoreChannelRange = new Connection.ChannelRange(fromCoreTotalChannels, end);
            fromCoreTotalChannels = end;
        }
        compilationElements.systemArchitecture.fromCoreChannelLayout = new SystemArchitecture.FromCoreChannelLayout(
                fromCoreChannelWidth, tracingTypeReference, fromCoreTraceChannels, fromCoreTotalChannels);
    }

    private void compileTransformRegion(StatementStep region, Transform transform) {
        boolean[] inRegion = new boolean[transform.idGenerator.getCounter()];
        LinkedList<StatementStep> allTerminations = new LinkedList<>();
        RegionMarker regionMarker = new RegionMarker(compilationErrorList,
                new boolean[transform.idGenerator.getCounter()], inRegion, allTerminations);
        regionMarker.markRegionAndCollectTerminations(region.unconditionalSuccessor);
        if (regionMarker.foundCycle) {
            return;
        }
        for (StatementStep termination : allTerminations) {
            new ReachabilityTreeBuilder(region, inRegion).build(termination);
        }
        for (VariableOrPort variableOrPort : transform.variablesOrPorts) {
            for (Statement mergePoint : region.mergePoints) {
                BinaryDecisionTree mergePointTree = BinaryDecisionTree.NOT_REACHED;
                TerminalListBuilder mergePointTerminals = new TerminalListBuilder();
                int registerTerminal = mergePointTerminals.add(VersionTerminalBeginningOfCycle.INSTANCE);
                for (StatementNonbranching predecessor : mergePoint.unconditionalPredecessors) {
                    if (predecessor instanceof StatementStep) {
                        if (predecessor == region) {
                            mergePointTree = new BinaryDecisionTree(new BinaryDecisionTree.Node[0], registerTerminal);
                        }
                    } else if (inRegion[predecessor.id]) {
                        BinaryDecisionTree predecessorReachabilityTree = predecessor.regionAttributes.get(
                                region).reachabilityTree;
                        mergePointTree = BinaryDecisionTree.merge(mergePointTree,
                                BinaryDecisionTree.replaceReachedWithTerminal(predecessorReachabilityTree,
                                        mergePointTerminals.add(
                                                findLastAssignedValue(region, variableOrPort, predecessor))));
                    }
                }
                for (StatementIfWhile predecessor : mergePoint.thenPredecessors) {
                    if (inRegion[predecessor.id]) {
                        BinaryDecisionTree predecessorReachabilityTree = predecessor.regionAttributes.get(
                                region).reachabilityTree;
                        mergePointTree = BinaryDecisionTree.merge(mergePointTree,
                                BinaryDecisionTree.replaceReachedWithCondition(predecessorReachabilityTree, predecessor,
                                        mergePointTerminals.add(
                                                findLastAssignedValue(region, variableOrPort, predecessor)),
                                        BinaryDecisionTree.NOT_REACHED_TERMINAL));
                    }
                }
                for (StatementIfWhile predecessor : mergePoint.elsePredecessors) {
                    if (inRegion[predecessor.id]) {
                        BinaryDecisionTree predecessorReachabilityTree = predecessor.regionAttributes.get(
                                region).reachabilityTree;
                        mergePointTree = BinaryDecisionTree.merge(mergePointTree,
                                BinaryDecisionTree.replaceReachedWithCondition(predecessorReachabilityTree, predecessor,
                                        BinaryDecisionTree.NOT_REACHED_TERMINAL, mergePointTerminals.add(
                                                findLastAssignedValue(region, variableOrPort, predecessor))));
                    }
                }
                if (mergePointTree.getRoot() != BinaryDecisionTree.NOT_REACHED_TERMINAL) {
                    mergePoint.regionAttributes.get(region).mergedValueByVariableOrPort.put(variableOrPort,
                            new BinaryDecisionTreeAndVariableTerminals(mergePointTree,
                                    mergePointTerminals.list.toArray(VersionTerminal.EMPTY_LIST)));
                }
            }
            for (StatementExpression assignment : variableOrPort.getAssignments()) {
                StatementRegionAttributes regionAttributes = assignment.regionAttributes.get(region);
                if (regionAttributes != null) {
                    collectVariableOrPortVersions(regionAttributes.versionAtEntryByVariableOrPort, assignment, region,
                            assignment.expression);
                }
            }
            for (StatementIfWhile statement : transform.ifWhiles) {
                StatementRegionAttributes regionAttributes = statement.regionAttributes.get(region);
                if (regionAttributes != null) {
                    collectVariableOrPortVersions(regionAttributes.versionAtEntryByVariableOrPort, statement, region,
                            statement.condition);
                }
            }
            BinaryDecisionTree nextStateTree = BinaryDecisionTree.NOT_REACHED;
            TerminalListBuilder nextStateTerminals = new TerminalListBuilder();
            for (StatementStep termination : allTerminations) {
                nextStateTree = BinaryDecisionTree.merge(nextStateTree, BinaryDecisionTree.replaceReachedWithTerminal(
                        termination.regionAttributes.get(region).reachabilityTree,
                        nextStateTerminals.add(getVersionAtStatementEntry(termination, region, variableOrPort))));
            }
            region.variableNextState.put(variableOrPort, new BinaryDecisionTreeAndVariableTerminals(nextStateTree,
                    nextStateTerminals.list.toArray(VersionTerminal.EMPTY_LIST)));
        }
    }

    private LinkedList<StructType.Field> collectStateFields(Transform transform) {
        LinkedList<StructType.Field> result = new LinkedList<>();
        for (VariableOrPort variable : transform.variablesOrPorts) {
            if (variable instanceof StatementVariableDeclaration cast) {
                result.add(new StructType.Field(cast.getName(), cast.typeReference));
            }
        }
        for (StatementStep region : transform.regions) {
            result.add(new StructType.Field(region.getAtVariableName(),
                    new TypeReference(null, "Bit", compilationElements.BIT_TYPE, null)));
        }
        return result;
    }

    private static LinkedList<StructType.Field> collectInputFields(Transform transform) {
        LinkedList<StructType.Field> result = new LinkedList<>();
        for (Transform.PortGroup portGroup : transform.portGroups) {
            if (portGroup.portA != null && portGroup.portA.direction == Transform.Direction.Input) {
                result.add(new StructType.Field(portGroup.portA.name, portGroup.portA.typeReference));
            }
            if (portGroup.portB != null && portGroup.portB.direction == Transform.Direction.Input) {
                result.add(new StructType.Field(portGroup.portB.name, portGroup.portB.typeReference));
            }
        }
        return result;
    }

    private void addImplicitVariablesForMacros(Transform transform) {
        for (Transform.PortGroup entry : transform.portGroups) {
            if (entry.largestWriteWhatByteCount != 0) {
                entry.writeAccumulatorValueVariable = new StatementVariableDeclaration(entry.locationRange,
                        transform.locationRange,
                        new TypeReference(entry.locationRange, "Bit", compilationElements.BIT_TYPE,
                                (entry.largestWriteWhatByteCount + entry.dataFieldByteCount) << 3), null,
                        entry.name + "WriteAccumulatorValue", transform.idGenerator.generateId());
                entry.writeAccumulatorByteCountVariable = new StatementVariableDeclaration(entry.locationRange,
                        transform.locationRange,
                        new TypeReference(entry.locationRange, "Bit", compilationElements.BIT_TYPE, 32 -
                                Integer.numberOfLeadingZeros(
                                        entry.largestWriteWhatByteCount + entry.dataFieldByteCount)), null,
                        entry.name + "WriteAccumulatorByteCount", transform.idGenerator.generateId());
                entry.writeAccumulatorLastVariable = new StatementVariableDeclaration(entry.locationRange,
                        transform.locationRange,
                        new TypeReference(entry.locationRange, "Bit", compilationElements.BIT_TYPE, null), null,
                        entry.name + "WriteAccumulatorLast", transform.idGenerator.generateId());
                transform.variablesOrPorts.add(entry.writeAccumulatorValueVariable);
                transform.variablesOrPorts.add(entry.writeAccumulatorByteCountVariable);
                transform.variablesOrPorts.add(entry.writeAccumulatorLastVariable);
            }
            if (entry.dataFieldByteCount != 0) {
                entry.readBusPosition = new StatementVariableDeclaration(entry.locationRange, transform.locationRange,
                        new TypeReference(entry.locationRange, "Bit", compilationElements.BIT_TYPE,
                                32 - Integer.numberOfLeadingZeros(entry.dataFieldByteCount)), null,
                        entry.name + "ReadBusPosition", transform.idGenerator.generateId());
                transform.variablesOrPorts.add(entry.readBusPosition);
            }
        }
    }

    private void sizeAndSortDeclaredTypes(TypeSorterAndSizer typeSorterAndSizer) {
        for (Map.Entry<String, Type> entry : compilationElements.types.entrySet()) {
            typeSorterAndSizer.add(null, entry.getKey(), entry.getValue());
        }
    }

    private void resolveTypeDefinitions() {
        for (Type type : compilationElements.types.values()) {
            if (type instanceof StructType cast) {
                for (StructType.Field field : cast.fields) {
                    resolveTypeReference(compilationElements, compilationErrorList, field.type());
                }
            } else if (type instanceof OneOfType cast) {
                for (TypeReference option : cast.options) {
                    resolveTypeReference(compilationElements, compilationErrorList, option);
                }
            }
        }
    }

    private void resolveInstances() {
        for (Instance instance : compilationElements.systemArchitecture.instances.values()) {
            instance.resolvedTransform = compilationElements.transforms.get(instance.transformName);
            if (instance.resolvedTransform == null) {
                compilationErrorList.addError("Transform '" + instance.transformName + "' doesn't exist",
                        instance.locationRange);
            } else {
                instance.resolvedTransform.isInstantiated = true;
                instance.outputEndpoints.add("Error");
            }
        }
    }

    private void resolveEndpoints() {
        int toCoreDestinationCounter = 1;
        for (Connection connection : compilationElements.systemArchitecture.connections) {
            resolveInstance(connection.source);
            resolveInstance(connection.destination);
            if (connection.source.resolvedInstance != null &&
                    !connection.source.resolvedInstance.outputEndpoints.add(connection.source.portGroupName)) {
                compilationErrorList.addError("Port group '" + connection.source.portGroupName +
                                "' is connected to more than one destination" +
                                (connection.kind == Connection.Kind.Wire ? ", fan-out is not supported yet" : ""),
                        connection.source.locationRange);
            }
            if (connection.destination.resolvedInstance != null &&
                    !connection.destination.resolvedInstance.inputEndpoints.add(connection.destination.portGroupName)) {
                compilationErrorList.addError("Port group '" + connection.destination.portGroupName +
                        "' is connected to more than one source", connection.destination.locationRange);
            }
            if (connection.source.resolvedInstance == null || connection.destination.resolvedInstance == null ||
                    connection.source.resolvedInstance.resolvedTransform == null ||
                    connection.destination.resolvedInstance.resolvedTransform == null) {
                continue;
            }
            connection.source.resolvedInstance.connectionsWithInstanceAsSource.add(connection);
            connection.destination.resolvedInstance.connectionsWithInstanceAsDestination.add(connection);
            if (connection.source.resolvedInstance != compilationElements.systemArchitecture.hostMessageRouter) {
                resolveEndpoint(connection.source);
            }
            if (connection.destination.resolvedInstance != compilationElements.systemArchitecture.hostMessageRouter) {
                resolveEndpoint(connection.destination);
            }
            if (connection.source.resolvedInstance == compilationElements.systemArchitecture.hostMessageRouter) {
                if (!connection.source.portGroupName.equals("To" + connection.destination.instanceName)) {
                    compilationErrorList.addError(
                            "HostMessageRouter source port group name should be 'To' + destination instance name",
                            connection.source.locationRange);
                    continue;
                }
                if (connection.destination.resolvedPortGroup == null) {
                    continue;
                }
                connection.source.resolvedPortGroup = new Transform.PortGroup(null, connection.source.portGroupName,
                        connection.destination.resolvedPortGroup.typeReference);
                compilationElements.systemArchitecture.hostMessageRouter.resolvedTransform.portGroups.add(
                        connection.source.resolvedPortGroup);
                connection.destination.resolvedInstance.toCoreDestinationIndex = toCoreDestinationCounter++;
            }
            if (connection.destination.resolvedInstance == compilationElements.systemArchitecture.hostMessageRouter) {
                if (!connection.destination.portGroupName.equals("From" + connection.source.instanceName)) {
                    compilationErrorList.addError(
                            "HostMessageRouter destination port group name should be 'From' + source instance name",
                            connection.destination.locationRange);
                    continue;
                }
                if (connection.source.resolvedPortGroup == null) {
                    continue;
                }
                connection.destination.resolvedPortGroup = new Transform.PortGroup(null,
                        connection.destination.portGroupName, connection.source.resolvedPortGroup.typeReference);
                compilationElements.systemArchitecture.hostMessageRouter.resolvedTransform.portGroups.add(
                        connection.destination.resolvedPortGroup);
            }
            createPorts(connection.source, connection.kind, Transform.Direction.Output);
            createPorts(connection.destination, connection.kind, Transform.Direction.Input);
        }
        compilationElements.systemArchitecture.toCoreDestinationBits =
                toCoreDestinationCounter != 0 ? 32 - Integer.numberOfLeadingZeros(toCoreDestinationCounter - 1) : 0;
        for (Map.Entry<String, Instance> instanceEntry : compilationElements.systemArchitecture.instances.entrySet()) {
            Instance instance = instanceEntry.getValue();
            if (instance.resolvedTransform != null && instance.resolvedTransform.body != null) {
                for (Transform.PortGroup portGroup : instance.resolvedTransform.portGroups) {
                    if (portGroup.connectionKind == null ||
                            !(portGroup.portA.direction == Transform.Direction.Input ? instance.inputEndpoints :
                                    instance.outputEndpoints).contains(portGroup.name)) {
                        compilationErrorList.addError(
                                "Port group '" + portGroup.name + "' of instance '" + instanceEntry.getKey() +
                                        "' is not connected", instance.locationRange);
                    }
                }
            }
        }
    }

    private void addImplicitTransforms() {
        compilationElements.systemArchitecture.hostMessageRouter = new Instance(null, "HostMessageRouterTransform");
        compilationElements.systemArchitecture.instances.put("HostMessageRouter",
                compilationElements.systemArchitecture.hostMessageRouter);
        compilationElements.transforms.put("HostMessageRouterTransform",
                new Transform(null, new LinkedList<>(), null, compilationElements.BIT_TYPE, new IdGenerator()));
        compilationElements.systemArchitecture.instances.put("Ethernet", new Instance(null, "EthernetTransform"));
        LinkedList<Transform.PortGroup> ethernetPorts = new LinkedList<>();
        TypeReference network32WordTypeReference = new TypeReference(null, "Network32Word",
                compilationElements.types.get("Network32Word"), null);
        ethernetPorts.add(new Transform.PortGroup(null, "Send", network32WordTypeReference));
        ethernetPorts.add(new Transform.PortGroup(null, "Receive", network32WordTypeReference));
        Transform ethernetTransform = new Transform(null, ethernetPorts, null, compilationElements.BIT_TYPE,
                new IdGenerator());
        ethernetTransform.stateAndInputsTypeReference = new TypeReference(null, "EthernetStateAndInputs",
                new StructType(new StructType.Field[]{new StructType.Field("Send", network32WordTypeReference),}),
                null);
        compilationElements.transforms.put("EthernetTransform", ethernetTransform);
        compilationElements.systemArchitecture.instances.put("CMSBridge", new Instance(null, "CMSBridgeTransform"));
        LinkedList<Transform.PortGroup> cmsBridgePorts = new LinkedList<>();
        TypeReference cmsReadRequestTypeReference = new TypeReference(null, "BlankOrCMSReadRequest",
                compilationElements.types.get("BlankOrCMSReadRequest"), null);
        cmsBridgePorts.add(new Transform.PortGroup(null, "Request", cmsReadRequestTypeReference));
        cmsBridgePorts.add(new Transform.PortGroup(null, "Response", new TypeReference(null, "BlankOrCMSReadResponse",
                compilationElements.types.get("BlankOrCMSReadResponse"), null)));
        Transform cmsTransform = new Transform(null, cmsBridgePorts, null, compilationElements.BIT_TYPE,
                new IdGenerator());
        cmsTransform.stateAndInputsTypeReference = new TypeReference(null, "CMSBridgeStateAndInputs",
                new StructType(new StructType.Field[]{new StructType.Field("Request", cmsReadRequestTypeReference),}),
                null);
        compilationElements.transforms.put("CMSBridgeTransform", cmsTransform);
        compilationElements.systemArchitecture.connections.add(new Connection(null, Connection.Kind.Wire,
                new Connection.Endpoint(null, "HostMessageRouter", "ToCMSBridge"),
                new Connection.Endpoint(null, "CMSBridge", "Request")));
        compilationElements.systemArchitecture.connections.add(
                new Connection(null, Connection.Kind.Wire, new Connection.Endpoint(null, "CMSBridge", "Response"),
                        new Connection.Endpoint(null, "HostMessageRouter", "FromCMSBridge")));
    }

    private void collectVariableOrPortVersions(TreeMap<VariableOrPort, VersionTerminal> versionAtEntryByVariableOrPort,
                                               Statement location, StatementStep region, Expression expression) {
        switch (expression) {
            case ExpressionName castName -> {
                if (castName.resolvedEntity instanceof VariableOrPort castVariableOrPort) {
                    versionAtEntryByVariableOrPort.put(castVariableOrPort,
                            getVersionAtStatementEntry(location, region, castVariableOrPort));
                }
            }
            case ExpressionConstant expressionConstant -> {
            }
            case ExpressionBinaryOperator castBinaryOperator -> {
                collectVariableOrPortVersions(versionAtEntryByVariableOrPort, location, region, castBinaryOperator.a);
                collectVariableOrPortVersions(versionAtEntryByVariableOrPort, location, region, castBinaryOperator.b);
            }
            case ExpressionPart castPart -> {
                collectVariableOrPortVersions(versionAtEntryByVariableOrPort, location, region, castPart.sub);
            }
            case ExpressionObject castObject -> {
                for (FieldOrParameter fieldOrParameter : castObject.fields.values()) {
                    collectVariableOrPortVersions(versionAtEntryByVariableOrPort, location, region,
                            fieldOrParameter.expression());
                }
            }
            case ExpressionFunction castFunction -> {
                for (Expression parameter : castFunction.positionalParameters) {
                    collectVariableOrPortVersions(versionAtEntryByVariableOrPort, location, region, parameter);
                }
                for (FieldOrParameter fieldOrParameter : castFunction.namedParameters.values()) {
                    collectVariableOrPortVersions(versionAtEntryByVariableOrPort, location, region,
                            fieldOrParameter.expression());
                }
            }
            case ExpressionUnaryOperator castUnaryOperator -> {
                collectVariableOrPortVersions(versionAtEntryByVariableOrPort, location, region, castUnaryOperator.sub);
            }
            case ExpressionIs castIs -> {
                collectVariableOrPortVersions(versionAtEntryByVariableOrPort, location, region, castIs.sub);
            }
            case null, default -> throw new RuntimeException("Unhandled case");
        }
    }

    static TypeReference getNonBlankType(Type type, StructType blankType) {
        return type instanceof OneOfType castOneOf && castOneOf.options.length == 2 &&
                castOneOf.options[0].resolvedType == blankType ? castOneOf.options[1] : null;
    }

    private static int getChannelCount(Type type, int tracingChannelWidth) {
        return (type.totalBits + tracingChannelWidth - 1) / tracingChannelWidth;
    }

    private void addType(TypeReference typeReference, SourceFilenameAndLocationRange locationRange,
                         TypeSorterAndSizer sorter) {
        if (compilationElements.types.put(typeReference.name, typeReference.resolvedType) != null) {
            compilationErrorList.addError("Name '" + typeReference.name + "' is reserved", locationRange);
        }
        sorter.add(typeReference.locationRange, typeReference.name, typeReference.resolvedType);
    }

    private void createPorts(Connection.Endpoint endpoint, Connection.Kind connectionKind,
                             Transform.Direction direction) {
        Transform.PortGroup resolvedPortGroup = endpoint.resolvedPortGroup;
        if (resolvedPortGroup == null) {
            return;
        }
        if (resolvedPortGroup.connectionKind != null) {
            if (resolvedPortGroup.connectionKind != connectionKind || resolvedPortGroup.portA.direction != direction) {
                compilationErrorList.addError(
                        "Connection kind and direction for port group '" + endpoint.portGroupName +
                                "' must agree when port group appears in more than one connection",
                        endpoint.locationRange);
            }
            return;
        }
        resolvedPortGroup.connectionKind = connectionKind;
        Transform resolvedTransform = endpoint.resolvedInstance.resolvedTransform;
        switch (connectionKind) {
            case Wire -> resolvedPortGroup.portA = new Transform.Port(resolvedPortGroup.locationRange,
                    resolvedPortGroup.name, resolvedPortGroup.typeReference, direction,
                    resolvedTransform.idGenerator.generateId());
            case WireWithBackpressure -> {
                resolvedPortGroup.portA = new Transform.Port(resolvedPortGroup.locationRange,
                        resolvedPortGroup.name + "Word", resolvedPortGroup.typeReference, direction,
                        resolvedTransform.idGenerator.generateId());
                resolvedPortGroup.portB = new Transform.Port(resolvedPortGroup.locationRange,
                        resolvedPortGroup.name + "Ready",
                        new TypeReference(null, "Bit", compilationElements.BIT_TYPE, null),
                        direction == Transform.Direction.Output ? Transform.Direction.Input :
                                Transform.Direction.Output, resolvedTransform.idGenerator.generateId());
            }
            case Queue -> {
                TypeReference queueNonBlankTypeReference = getNonBlankType(resolvedPortGroup.typeReference.resolvedType,
                        compilationElements.BLANK_TYPE);
                if (queueNonBlankTypeReference == null) {
                    compilationErrorList.addError("Queue type must be oneof with two options: Blank and element type",
                            endpoint.locationRange);
                } else if (queueNonBlankTypeReference.resolvedType != null) {
                    resolvedPortGroup.queueNonBlankTypeReference = queueNonBlankTypeReference;
                }
                if (direction == Transform.Direction.Output) {
                    resolvedPortGroup.portA = new Transform.Port(resolvedPortGroup.locationRange,
                            resolvedPortGroup.name + "Writer", resolvedPortGroup.typeReference, direction,
                            resolvedTransform.idGenerator.generateId());
                    resolvedPortGroup.portB = new Transform.Port(resolvedPortGroup.locationRange,
                            resolvedPortGroup.name + "Ready",
                            new TypeReference(null, "Bit", compilationElements.BIT_TYPE, null),
                            Transform.Direction.Input, resolvedTransform.idGenerator.generateId());
                } else {
                    resolvedPortGroup.portA = new Transform.Port(resolvedPortGroup.locationRange,
                            resolvedPortGroup.name + "Reader", resolvedPortGroup.typeReference, direction,
                            resolvedTransform.idGenerator.generateId());
                    resolvedPortGroup.portB = new Transform.Port(resolvedPortGroup.locationRange,
                            resolvedPortGroup.name + "Pop",
                            new TypeReference(null, "Bit", compilationElements.BIT_TYPE, null),
                            Transform.Direction.Output, resolvedTransform.idGenerator.generateId());
                }
            }
            default -> throw new RuntimeException("Unhandled case");
        }
    }

    static VersionTerminal getVersionAtStatementEntry(Statement location, StatementStep region,
                                                      VariableOrPort variableOrPort) {
        if (variableOrPort instanceof Transform.Port castPort && castPort.direction == Transform.Direction.Input) {
            return VersionTerminalInputPort.INSTANCE;
        }
        return location.regionAttributes.get(region).mergedValueByVariableOrPort != null ?
                new VersionTerminalMerged(region, location) :
                findLastAssignedValue(region, variableOrPort, getOnlyPredecessor(region, location));
    }

    static VersionTerminal findLastAssignedValue(StatementStep region, VariableOrPort variableOrPort, Statement node) {
        while (true) {
            if (node instanceof StatementExpression cast && cast.resolvedAssignmentTarget == variableOrPort) {
                return new VersionTerminalAssigned(region, node);
            }
            if (node instanceof StatementStep) {
                return VersionTerminalBeginningOfCycle.INSTANCE;
            }
            if (node.regionAttributes.get(region).mergedValueByVariableOrPort != null) {
                return new VersionTerminalMerged(region, node);
            }
            node = getOnlyPredecessor(region, node);
        }
    }

    static Statement getOnlyPredecessor(StatementStep region, Statement node) {
        return node.regionAttributes.get(region).onlyPredecessor;
    }
}
