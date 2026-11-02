// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import dsl.flows.sde.build.*;
import dsl.flows.sde.compiler.CompilationError;
import dsl.flows.sde.compiler.Compiler;
import dsl.flows.sde.generator.Generator;
import dsl.flows.sde.models.CompilationElements;
import dsl.flows.sde.parser.FlowsParser;
import dsl.flows.sde.parser.ParseException;
import dsl.flows.sde.parser.SourceFilenameAndLocationRange;
import dsl.flows.sde.parser.SourceLocation;
import dsl.flows.sde.resources.ResourceAnchor;
import dsl.flows.sde.utility.LineSplitter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.function.Consumer;

public final class ProjectTabComponent extends JPanel implements ActionListener, BuildResultListener {
    final SettingsSupplier settingsSupplier;
    final String projectName;
    CompilationElements compilationElements;
    EditorComponent editorComponent;
    JTabbedPane tabbedPane;
    DefaultTableModel errorsModel;
    JTextArea messages;
    JCheckBox includeTraceCheckbox;
    JButton buildButton, generateSourceButton;
    Long runningVivadoProcess;

    public ProjectTabComponent(SettingsSupplier settingsSupplier, String projectName,
                               Consumer<EditorComponent> modificationStatusChangeListener) {
        this.settingsSupplier = settingsSupplier;
        this.projectName = projectName;
        setLayout(new BorderLayout());
        JPanel compilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        errorsModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        errorsModel.setColumnIdentifiers(new String[]{"Location", "Details"});
        JTable errorsTable = new JTable(errorsModel);
        errorsTable.setFocusable(false);
        messages = new JTextArea(10, 5);
        messages.setEditable(false);
        includeTraceCheckbox = new JCheckBox("Include trace", true);
        compilePanel.add(includeTraceCheckbox);
        includeTraceCheckbox.addItemListener(e -> {
            editorComponent.regenerateFragments();
        });
        buildButton = new JButton("Build");
        compilePanel.add(buildButton);
        generateSourceButton = new JButton("Generate SystemVerilog");
        compilePanel.add(generateSourceButton);
        generateSourceButton.addActionListener(actionEvent -> {
            String buildPath = settingsSupplier.getBuildPath() + projectName;
            try {
                new Generator(buildPath, compilationElements).generate();
            } catch (IOException | IllegalAccessException e) {
                e.printStackTrace();
            }
        });
        add(compilePanel, BorderLayout.NORTH);
        editorComponent = new EditorComponent(settingsSupplier.getProjectPath() + "/" + projectName,
                this::generateHighlighting, modificationStatusChangeListener);
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Errors", new JScrollPane(errorsTable));
        tabbedPane.addTab("Build", new JScrollPane(messages));
        errorsTable.getSelectionModel().addListSelectionListener(_ -> {
            int selectedRow = errorsTable.getSelectedRow();
            if (selectedRow != -1) {
                editorComponent.select((SourceFilenameAndLocationRange) errorsModel.getValueAt(selectedRow, 0));
            }
        });
        JScrollPane scrollPane = new JScrollPane(editorComponent);
        for (MouseWheelListener listener : scrollPane.getMouseWheelListeners()) {
            scrollPane.removeMouseWheelListener(listener);
        }
        scrollPane.addMouseWheelListener(e -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            int fontHeight = editorComponent.getFontMetrics(editorComponent.getFont()).getHeight();
            bar.setValue(bar.getValue() + fontHeight * e.getWheelRotation());
        });
        scrollPane.setRowHeaderView(new LineNumberComponent(editorComponent));
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, tabbedPane);
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int locationWidth = getFontMetrics(errorsTable.getTableHeader().getFont()).stringWidth("Location") * 5;
                errorsTable.getColumn("Location").setPreferredWidth(locationWidth);
                errorsTable.getColumn("Details").setPreferredWidth(getWidth() - locationWidth);
                splitPane.setDividerLocation(0.75);
                splitPane.removeComponentListener(this);
            }
        });
        splitPane.setResizeWeight(1);
        add(splitPane, BorderLayout.CENTER);
        buildButton.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        tabbedPane.setSelectedIndex(1);
        includeTraceCheckbox.setEnabled(false);
        buildButton.setEnabled(false);
        generateSourceButton.setEnabled(false);
        messages.setText("");
        editorComponent.regenerateFragmentsIfPending();
        editorComponent.setEditingDisabledReason("Compilation is in progress, please wait for it to finish");
        String vivadoPath = settingsSupplier.getVivadoPath();
        String buildPath = settingsSupplier.getBuildPath() + projectName;
        new Thread(() -> {
            String error = null;
            try {
                Generator generator = new Generator(buildPath, compilationElements);
                generator.generate();
                ProcessBuilder builder = new ProcessBuilder("setsid", vivadoPath + "/bin/vivado", "-mode", "batch",
                        "-source", generator.outputDirectory + "/Compile.tcl").directory(
                        new File(generator.outputDirectory));
                builder.environment().put("ALL_SOURCE_FILES", Generator.getAllFiles(compilationElements));
                Process vivadoProcess = builder.start();
                long finalPid = vivadoProcess.pid();
                SwingUtilities.invokeLater(() -> {
                    runningVivadoProcess = finalPid;
                });
                BuildResultParser buildResultParser = new BuildResultParser(compilationElements, this);
                new Thread(new StreamErrorAdjuster(
                        new BufferedReader(new InputStreamReader(vivadoProcess.getInputStream())), line -> {
                    SwingUtilities.invokeLater(() -> {
                        buildResultParser.accept(line);
                        messages.append(line + "\n");
                    });
                })).start();
                new Thread(new StreamErrorAdjuster(
                        new BufferedReader(new InputStreamReader(vivadoProcess.getErrorStream())), line -> {
                    SwingUtilities.invokeLater(() -> {
                        messages.append(line + "\n");
                    });
                })).start();
                StreamErrorAdjuster.waitWhileCheckingForOOM(vivadoProcess);
            } catch (IOException | IllegalAccessException | InterruptedException exception) {
                error = exception.getMessage();
            }
            String finalError = error;
            SwingUtilities.invokeLater(() -> {
                runningVivadoProcess = null;
                messages.append(finalError);
                includeTraceCheckbox.setEnabled(true);
                buildButton.setEnabled(true);
                generateSourceButton.setEnabled(true);
                editorComponent.setEditingDisabledReason(null);
            });
        }).start();
    }

    public EditorComponent.Highlighting generateHighlighting(String[] sourceText) {
        try {
            compilationElements = new CompilationElements(includeTraceCheckbox.isSelected(), false);
            boolean compileEnabled;
            LinkedList<EditorComponent.Underline> underlines = new LinkedList<>();
            try {
                new FlowsParser("base.flows", LineSplitter.splitLines(
                        Files.readString(Path.of(ResourceAnchor.class.getResource("flows/base.flows").toURI()))),
                        false).parse(compilationElements);
                new FlowsParser(projectName, sourceText, false).parse(compilationElements);
                Compiler compiler = new Compiler(compilationElements);
                compiler.compile();
                compileEnabled = true;
                LinkedList<Object[]> newData = new LinkedList<>();
                for (CompilationError error : compiler.compilationErrorList.list) {
                    compileEnabled = false;
                    if (error.locationRange() != null) {
                        underlines.add(new EditorComponent.Underline(error.locationRange(), error.details()));
                    }
                    newData.add(new Object[]{error.locationRange(), error.details()});
                }
                errorsModel.setDataVector(newData.toArray(new Object[0][]), new Object[]{"Location", "Details"});
            } catch (ParseException exception) {
                underlines.add(new EditorComponent.Underline(exception.locationRange, exception.getMessage()));
                compileEnabled = false;
                errorsModel.setDataVector(new Object[][]{new Object[]{exception.locationRange, exception.getMessage()}},
                        new Object[]{"Location", "Details"});
            }
            buildButton.setEnabled(compileEnabled);

            EditorComponent.Fragment[][] fragments = new EditorComponent.Fragment[sourceText.length][];
            int fragmentLine = 0;
            LinkedList<EditorComponent.Fragment> fragmentList = new LinkedList<>();
            for (FlowsParser.Token token : new FlowsParser(projectName, sourceText, true).findHighlightedTokens()) {
                fragmentLine = addFragmentsUntil(fragmentLine, token.sourceLocation.line(), fragmentList, sourceText,
                        fragments, 0);
                SourceLocation end;
                int color;
                if (token instanceof FlowsParser.TokenKeyword cast) {
                    end = new SourceLocation(token.sourceLocation.line(),
                            token.sourceLocation.position() + cast.kind.name().length());
                    color = 1;
                } else if (token instanceof FlowsParser.TokenPunctuation cast) {
                    end = new SourceLocation(token.sourceLocation.line(),
                            token.sourceLocation.position() + cast.kind.length);
                    color = 2;
                } else if (token instanceof FlowsParser.TokenComment cast) {
                    end = cast.endLocation;
                    color = 3;
                } else {
                    throw new RuntimeException("Unknown token kind: " + token.getClass().getName());
                }
                fragmentList.add(new EditorComponent.Fragment(token.sourceLocation.position(), 0));
                fragmentLine = addFragmentsUntil(fragmentLine, end.line(), fragmentList, sourceText, fragments, color);
                fragmentList.add(new EditorComponent.Fragment(end.position(), color));
            }
            addFragmentsUntil(fragmentLine, sourceText.length, fragmentList, sourceText, fragments, 0);
            return new EditorComponent.Highlighting(fragments, underlines.toArray(new EditorComponent.Underline[0]));
        } catch (ParseException | IllegalAccessException | URISyntaxException | IOException e) {
            return null;
        }
    }

    private static int addFragmentsUntil(int current, int limit, LinkedList<EditorComponent.Fragment> fragmentList,
                                         String[] sourceText, EditorComponent.Fragment[][] fragments, int color) {
        while (current < limit) {
            fragmentList.add(new EditorComponent.Fragment(sourceText[current].length(), color));
            fragments[current] = fragmentList.toArray(EditorComponent.Fragment.EMPTY_ARRAY);
            fragmentList.clear();
            current++;
        }
        return current;
    }

    boolean isContentModified() {
        return editorComponent.isContentModified();
    }

    @Override
    public void showBuildResult(BuildResult buildResult, long elapsed) {
        SwingUtilities.invokeLater(() -> {
            if (buildResult instanceof BuildFailedTiming cast) {
                editorComponent.setSourceGraph(cast.failedPath);
                JOptionPane.showMessageDialog(ProjectTabComponent.this, String.format(
                        "The highlighted path takes about %d%% longer than one step allows." +
                                (cast.failedPath.length != 0 ?
                                        "\nPlease review the highlighted path and split it into multiple steps." : ""),
                        (int) (cast.slackFraction * 100)), "Build failed", JOptionPane.INFORMATION_MESSAGE);
            } else if (buildResult instanceof BuildSucceeded) {
                JOptionPane.showMessageDialog(ProjectTabComponent.this,
                        "Build successful in " + (elapsed / 1000) + " seconds", "Build succeeded",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }
}
