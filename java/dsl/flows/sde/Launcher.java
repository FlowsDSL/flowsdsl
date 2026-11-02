// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde;

import dsl.flows.sde.build.BuildFailedTiming;
import dsl.flows.sde.build.BuildResultParser;
import dsl.flows.sde.build.StreamErrorAdjuster;
import dsl.flows.sde.compiler.CompilationError;
import dsl.flows.sde.compiler.Compiler;
import dsl.flows.sde.generator.Generator;
import dsl.flows.sde.models.CompilationElements;
import dsl.flows.sde.parser.FlowsParser;
import dsl.flows.sde.parser.ParseException;
import dsl.flows.sde.resources.ResourceAnchor;
import dsl.flows.sde.ui.SDE;
import dsl.flows.sde.utility.LineSplitter;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class Launcher {
    public static void main(String[] args)
            throws IllegalAccessException, URISyntaxException, IOException, ParseException, InterruptedException {
        boolean batch = false;
        boolean skipBitfile = false;
        boolean includeTracing = true;
        String projectName = null;
        for (String arg : args) {
            if (arg.isEmpty()) {
                throw new RuntimeException("Empty arguments are not valid");
            }
            if (arg.charAt(0) == '-') {
                if (arg.equals("-batch")) {
                    batch = true;
                } else if (arg.equals("-skipBitfile")) {
                    skipBitfile = true;
                } else if (arg.equals("-doNotIncludeTracing")) {
                    includeTracing = false;
                } else {
                    throw new RuntimeException("Invalid option: " + arg);
                }
            } else {
                if (projectName != null) {
                    throw new RuntimeException("Cannot specify multiple projects");
                }
                projectName = arg;
            }
        }
        String settingsFileName = System.getenv("HOME") + "/.flowsSettings";
        Map<String, String> settingsMap = new TreeMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(settingsFileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals != -1) {
                    settingsMap.put(line.substring(0, equals), line.substring(equals + 1));
                }
            }
        } catch (FileNotFoundException _) {
        } catch (IOException exception) {
            if (batch) {
                exception.printStackTrace();
                System.exit(1);
            } else {
                SDE.showErrorMessage(exception.getMessage());
            }
            return;
        }
        if (batch) {
            if (projectName == null) {
                throw new RuntimeException("Please specify project name for batch mode");
            }
            String buildPath = settingsMap.get("buildPath");
            String vivadoPath = settingsMap.get("vivadoPath");
            String projectsPath = settingsMap.get("projectsPath");
            if (buildPath == null || vivadoPath == null || projectsPath == null) {
                throw new RuntimeException(
                        "Please set build path, vivado path and project path in interactive mode before using batch mode");
            }
            CompilationElements compilationElements = new CompilationElements(includeTracing, false);
            new FlowsParser("base.flows", LineSplitter.splitLines(
                    Files.readString(Path.of(ResourceAnchor.class.getResource("flows/base.flows").toURI()))),
                    false).parse(compilationElements);
            new FlowsParser(projectName, LineSplitter.splitLines(Files.readString(Path.of(projectsPath, projectName))),
                    false).parse(compilationElements);
            Compiler compiler = new Compiler(compilationElements);
            compiler.compile();
            if (!compiler.compilationErrorList.list.isEmpty()) {
                for (CompilationError error : compiler.compilationErrorList.list) {
                    System.err.println(error);
                }
                System.exit(1);
                return;
            }
            Generator generator = new Generator(buildPath + projectName, compilationElements);
            generator.generate();
            if (skipBitfile) {
                System.err.println("Compilation successful, output files written to " + generator.outputDirectory);
                System.exit(0);
            }
            ProcessBuilder builder = new ProcessBuilder("setsid", vivadoPath + "/bin/vivado", "-mode", "batch",
                    "-source", generator.outputDirectory + "/Compile.tcl").directory(
                    new File(generator.outputDirectory));
            builder.environment().put("ALL_SOURCE_FILES", Generator.getAllFiles(compilationElements));
            Process vivadoProcess = builder.start();
            BuildResultParser buildResultParser = new BuildResultParser(compilationElements, (result, elapsed) -> {
                System.err.println(result instanceof BuildFailedTiming ? "Failed timing" : "Success");
            });
            Thread stdoutThread = new Thread(
                    new StreamErrorAdjuster(new BufferedReader(new InputStreamReader(vivadoProcess.getInputStream())),
                            line -> {
                                buildResultParser.accept(line);
                                System.out.println(line);
                            }));
            Thread stderrThread = new Thread(
                    new StreamErrorAdjuster(new BufferedReader(new InputStreamReader(vivadoProcess.getErrorStream())),
                            System.out::println));
            stdoutThread.start();
            stderrThread.start();
            StreamErrorAdjuster.waitWhileCheckingForOOM(vivadoProcess);
            stdoutThread.join();
            stderrThread.join();
            System.exit(vivadoProcess.exitValue());
            return;
        }
        SDE.start(settingsFileName, settingsMap);
    }
}
