// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.build;

import dsl.flows.sde.models.CompilationElements;
import dsl.flows.sde.models.Instance;
import dsl.flows.sde.models.Transform;
import dsl.flows.sde.models.statements.StatementStep;
import dsl.flows.sde.models.statements.VariableOrPort;
import dsl.flows.sde.models.terminals.VersionTerminal;
import dsl.flows.sde.parser.SourceLocation;
import dsl.flows.sde.ui.SourceGraphEdge;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.function.Consumer;

public class BuildResultParser implements Consumer<String> {
    record ParseRegisterResult(String instanceName, String registerName, SourceLocation location) {
    }

    record FindVariableOrPortResult(String instanceName, Transform transform, VariableOrPort variableOrPort) {
    }

    final CompilationElements compilationElements;
    final BuildResultListener listener;
    final long start;
    Double slackFraction;
    LinkedList<Cell> failingPath;

    public BuildResultParser(CompilationElements compilationElements, BuildResultListener listener) {
        this.compilationElements = compilationElements;
        this.listener = listener;
        start = System.currentTimeMillis();
    }

    @Override
    public void accept(String line) {
        String[] split = line.split(" ");
        if (split.length > 0) {
            if (split[0].equals("#FailedTimingStart")) {
                if (split.length != 4) {
                    throw new RuntimeException("Failed timing start line should have 3 parameters");
                }
                if (failingPath != null) {
                    throw new RuntimeException("Duplicate failed timing start line");
                }
                slackFraction = -Double.valueOf(split[1])/Double.valueOf(split[2]);
                failingPath = new LinkedList<>();
            }
            if (split[0].equals("#PathCell")) {
                if (split.length != 3) {
                    throw new RuntimeException("Path cell line should have 2 parameters");
                }
                if (failingPath == null) {
                    throw new RuntimeException("Path cell line without failed timing start");
                }
                failingPath.add(new Cell(split[1], split[2]));
            }
            if (split[0].equals("#TruthTable")) {
                if (split.length != 2) {
                    throw new RuntimeException("Truth table line should have 1 parameter");
                }
                if (failingPath == null || failingPath.isEmpty()) {
                    throw new RuntimeException("Truth table line without path cell");
                }
                if (failingPath.getLast().truthTable != null) {
                    throw new RuntimeException("Duplicate truth table line");
                }
                String[] subSplit = split[1].split("'h");
                if (subSplit.length != 2) {
                    throw new RuntimeException("Truth table line's parameter should be of form <N>'h<M>");
                }
                Integer.parseInt(subSplit[0]);
                failingPath.getLast().truthTable = Long.parseUnsignedLong(subSplit[1], 0x10);
            }
            if (split[0].equals("#Input")) {
                if (split.length != 4 && split.length != 5) {
                    throw new RuntimeException("Input line should have 3 or 4 parameters");
                }
                if (failingPath == null || failingPath.isEmpty()) {
                    throw new RuntimeException("Input line without path cell");
                }
                Cell last = failingPath.getLast();
                if (!split[1].equals("I" + last.inputs.size())) {
                    throw new RuntimeException("Input line name should have form I<N>");
                }
                if (split.length == 5) {
                    if (!split[4].equals("OnPath")) {
                        throw new RuntimeException("Last parameter to Input line, if present, must be OnPath");
                    }
                    if (last.onPathInputIndex != null) {
                        throw new RuntimeException("Input line indicates more than one on path input");
                    }
                    last.onPathInputIndex = last.inputs.size();
                }
                last.inputs.add(new Cell(split[2], split[3]));
            }
            if (split[0].equals("#FailedTimingEnd")) {
                if (split.length != 1) {
                    throw new RuntimeException("Failed timing end line should have no parameters");
                }
                long elapsed = System.currentTimeMillis() - start;
                if (failingPath == null) {
                    throw new RuntimeException("Failed timing end without start");
                }
                listener.showBuildResult(analyzeFailingPath(), elapsed);
            }
            if (split[0].equals("#Success")) {
                if (split.length != 1) {
                    throw new RuntimeException("Success line should have no parameter");
                }
                if (failingPath != null) {
                    throw new RuntimeException("Failed timing start followed by success");
                }
                long elapsed = System.currentTimeMillis() - start;
                listener.showBuildResult(new BuildSucceeded(), elapsed);
            }
        }
    }

    private FindVariableOrPortResult findVariableOrPort(Cell cell) {
        if (!cell.isRegister()) {
            return null;
        }
        ParseRegisterResult result = new VivadoOutputParser(cell.name).parseRegister();
        if (result == null || result.location != null) {
            return null;
        }
        Instance instance = compilationElements.systemArchitecture.instances.get(result.instanceName);
        for (VariableOrPort variableOrPort : instance.resolvedTransform.variablesOrPorts) {
            if (variableOrPort.getName().equals(result.registerName)) {
                return new FindVariableOrPortResult(result.registerName, instance.resolvedTransform, variableOrPort);
            }
        }
        return null;
    }

    private BuildFailedTiming analyzeFailingPath() {
        if (failingPath.size() < 3) {
            return BuildFailedTiming.BUILD_FAILED_TIMING_NO_DETAILS;
        }
        FindVariableOrPortResult start = findVariableOrPort(failingPath.getFirst());
        if (start == null) {
            return BuildFailedTiming.BUILD_FAILED_TIMING_NO_DETAILS;
        }
        FindVariableOrPortResult end = findVariableOrPort(failingPath.removeLast());
        if (end == null) {
            return BuildFailedTiming.BUILD_FAILED_TIMING_NO_DETAILS;
        }
        Cell last = failingPath.getLast();
        if (!last.type.startsWith("LUT") || last.truthTable == null || last.onPathInputIndex == null) {
            return BuildFailedTiming.BUILD_FAILED_TIMING_NO_DETAILS;
        }
        StatementStep region = null;
        Transform transform = null;
        ParseRegisterResult[] parseResults = new ParseRegisterResult[last.inputs.size()];
        ArrayList<Integer> aInputs = new ArrayList<>();
        ArrayList<Integer> nonAInputs = new ArrayList<>();
        for (int inputIndex = 0; inputIndex < last.inputs.size(); inputIndex++) {
            Cell input = last.inputs.get(inputIndex);
            ParseRegisterResult parseResult = new VivadoOutputParser(input.name).parseRegister();
            parseResults[inputIndex] = parseResult;
            if (parseResult != null && input.isRegister() && parseResult.registerName.equals("A") && parseResult.location != null) {
                aInputs.add(inputIndex);
            } else if (inputIndex != last.onPathInputIndex) {
                nonAInputs.add(inputIndex);
            }
        }
        outer:
        for (Integer inputIndex : aInputs) {
            int combinations = 1 << nonAInputs.size();
            for (int combination = 0; combination < combinations; combination++) {
                int truthTableIndex = 1 << inputIndex;
                for (int bitIndex = 0; bitIndex < nonAInputs.size(); bitIndex++) {
                    if ((combination & (1 << bitIndex)) != 0) {
                        truthTableIndex |= (1 << nonAInputs.get(bitIndex));
                    }
                }
                long flippedIndex = truthTableIndex ^ (1L << last.onPathInputIndex);
                boolean output = (last.truthTable & (1L << truthTableIndex)) != 0;
                boolean flippedOutput = (last.truthTable & (1L << flippedIndex)) != 0;
                if (output != flippedOutput) {
                    ParseRegisterResult input = parseResults[inputIndex];
                    Instance instance = compilationElements.systemArchitecture.instances.get(input.instanceName);
                    if (instance != null) {
                        for (StatementStep candidateRegion : instance.resolvedTransform.regions) {
                            if (candidateRegion.locationRange.start.compareTo(input.location) == 0) {
                                region = candidateRegion;
                                transform = instance.resolvedTransform;
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        if (region == null) {
            return BuildFailedTiming.BUILD_FAILED_TIMING_NO_DETAILS;
        }
        DependencyGraphTracer tracer = new DependencyGraphTracer(region, start.variableOrPort, transform);
        for (StatementStep.TerminatingRegionAndVersion terminatingRegion : region.terminatingRegions) {
            VersionTerminal version = terminatingRegion.versionByVariableOrPort.get(end.variableOrPort);
            if (tracer.traceVersion(end.variableOrPort, version)) {
                tracer.dependencyGraph.add(new SourceGraphEdge(tracer.definitionLocation(version),
                        terminatingRegion.terminatingRegion.locationRange));
            }
        }
        return new BuildFailedTiming(slackFraction, tracer.dependencyGraph.toArray(SourceGraphEdge.EMPTY_ARRAY));
    }
}
