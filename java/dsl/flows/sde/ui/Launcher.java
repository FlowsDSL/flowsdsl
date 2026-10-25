// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import dsl.flows.sde.*;
import dsl.flows.sde.compiler.Compiler;
import dsl.flows.sde.generator.Generator;
import dsl.flows.sde.models.CompilationElements;
import dsl.flows.sde.resources.ResourceAnchor;
import dsl.flows.sde.utility.LineSplitter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public class Launcher {
    record ListEntry(String name, boolean valid) {
        @Override
        public String toString() {
            return name;
        }
    }

    static class ValidityIndicator extends JComponent {
        boolean active;

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (active) {
                g.setColor(Color.RED);
                g.fillOval(0, 0, getWidth(), getHeight());
            }
        }
    }

    record DirectorySuggester(JTextField textField, JWindow listWindow, DefaultListModel<ListEntry> listModel,
                              JList<ListEntry> list, Runnable textChangedListener) {
        void addListeners() {
            textField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    updateSuggestions();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    updateSuggestions();
                }
            });
            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateSuggestions();
                    textChangedListener.run();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateSuggestions();
                    textChangedListener.run();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateSuggestions();
                    textChangedListener.run();
                }
            });
            textField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        ListEntry value = list.getSelectedValue();
                        if (value != null && value.valid &&
                                textField.getSelectionEnd() == textField.getSelectionStart()) {
                            String text = textField.getText();
                            textField.setText(
                                    (text.startsWith("/") ? "" : "/") + text.substring(0, text.lastIndexOf('/') + 1) +
                                            value.name + "/");
                        }
                    }
                    if (e.getKeyCode() == KeyEvent.VK_UP) {
                        list.setSelectedIndex(list.getSelectedIndex() - 1);
                        list.scrollRectToVisible(list.getCellBounds(list.getSelectedIndex(), list.getSelectedIndex()));
                    }
                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        list.setSelectedIndex(list.getSelectedIndex() + 1);
                        list.scrollRectToVisible(list.getCellBounds(list.getSelectedIndex(), list.getSelectedIndex()));
                    }
                }
            });
        }

        void updateSuggestions() {
            String text = textField.getText();
            if (textField.isFocusOwner()) {
                int index = text.lastIndexOf('/');
                File[] files = new File(index <= 0 ? "/" : text.substring(0, index)).listFiles(File::isDirectory);
                Point textFieldLocationOnScreen = textField.getLocationOnScreen();
                listWindow.setLocation(
                        textFieldLocationOnScreen.x + textField.getFontMetrics(textField.getFont()).stringWidth(text) -
                                textField.getScrollOffset(), textFieldLocationOnScreen.y + textField.getHeight());
                listModel.removeAllElements();
                if (files != null) {
                    Arrays.sort(files, Comparator.comparing(File::getName));
                    String prefix = text.substring(index + 1);
                    for (File file : files) {
                        if (file.getName().startsWith(prefix)) {
                            listModel.addElement(new ListEntry(file.getName(), true));
                        }
                    }
                    if (files.length == 0) {
                        listModel.addElement(new ListEntry("No directories found", false));
                    }
                } else {
                    listModel.addElement(new ListEntry("Directory not found", false));
                }
                listWindow.setVisible(true);
            } else {
                listWindow.setVisible(false);
            }
        }
    }

    static class LabelAndTextField {
        final ValidityIndicator validityIndicator;
        final JLabel label;
        final JTextField textField;

        LabelAndTextField(String text, String value, JPanel startPanel) {
            validityIndicator = new ValidityIndicator();
            label = new JLabel(text + " ");
            textField = new JTextField(value);
            startPanel.add(validityIndicator);
            startPanel.add(label);
            startPanel.add(textField);
        }

        void position(int y, int width, int textHeight, int labelWidth) {
            validityIndicator.setBounds(textHeight / 4, y + textHeight / 4, textHeight / 2, textHeight / 2);
            label.setBounds(textHeight, y, labelWidth, textHeight);
            textField.setBounds(textHeight + labelWidth, y, width - textHeight - labelWidth, textHeight);
        }
    }

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
        Map<String, String> settingsMap = new TreeMap<>();
        String settingsFileName = System.getenv("HOME") + "/.flowsSettings";
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
                JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
            CompilationElements architecture = new CompilationElements(includeTracing, false);
            new FlowsParser("base.flows", LineSplitter.splitLines(
                    Files.readString(Path.of(ResourceAnchor.class.getResource("flows/base.flows").toURI()))),
                    false).parse(architecture);
            new FlowsParser(projectName, LineSplitter.splitLines(Files.readString(Path.of(projectsPath, projectName))),
                    false).parse(architecture);
            Compiler compiler = new Compiler(architecture);
            compiler.compile();
            if (!compiler.compilationErrorList.list.isEmpty()) {
                for (CompilationError error : compiler.compilationErrorList.list) {
                    System.err.println(error);
                }
                System.exit(1);
                return;
            }
            Generator generator = new Generator(buildPath + projectName, architecture);
            generator.generate();
            if (skipBitfile) {
                System.err.println("Compilation successful, output files written to " + generator.outputDirectory);
                System.exit(0);
            }
            ProcessBuilder builder = new ProcessBuilder("setsid", vivadoPath + "/bin/vivado", "-mode", "batch",
                    "-source", generator.outputDirectory + "/Compile.tcl").directory(
                    new File(generator.outputDirectory));
            builder.environment().put("ALL_SOURCE_FILES", Generator.getAllFiles(architecture));
            Process vivadoProcess = builder.start();
            new Thread(
                    new StreamErrorAdjuster(new BufferedReader(new InputStreamReader(vivadoProcess.getInputStream())),
                            System.out::println)).start();
            new Thread(
                    new StreamErrorAdjuster(new BufferedReader(new InputStreamReader(vivadoProcess.getErrorStream())),
                            System.out::println)).start();
            StreamErrorAdjuster.waitWhileCheckingForOOM(vivadoProcess);
            System.exit(vivadoProcess.exitValue());
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Flows");
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            String frameX = settingsMap.get("frameX");
            String frameY = settingsMap.get("frameY");
            if (frameX != null && frameY != null) {
                frame.setLocation(Integer.parseInt(frameX), Integer.parseInt(frameY));
            }
            String frameWidth = settingsMap.get("frameWidth");
            String frameHeight = settingsMap.get("frameHeight");
            if (frameWidth != null && frameHeight != null) {
                frame.setSize(Integer.parseInt(frameWidth), Integer.parseInt(frameHeight));
            } else {
                frame.setSize(800, 600);
            }
            JPanel startPanel = new JPanel();
            startPanel.setLayout(null);
            LabelAndTextField vivadoDirectory = new LabelAndTextField("Path to Vivado:", settingsMap.get("vivadoPath"),
                    startPanel);
            LabelAndTextField projectsDirectory = new LabelAndTextField("Path to projects:",
                    settingsMap.get("projectsPath"), startPanel);
            LabelAndTextField buildDirectory = new LabelAndTextField("Path to build directory:",
                    settingsMap.get("buildPath"), startPanel);
            JTabbedPane tab = new JTabbedPane();
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    StringBuilder consequences = new StringBuilder();
                    for (int tabIndex = 1; tabIndex < tab.getTabCount(); tabIndex++) {
                        ProjectTabComponent projectTab = (ProjectTabComponent) tab.getComponentAt(tabIndex);
                        if (projectTab.isContentModified()) {
                            consequences.append("Unsaved changes in ").append(tab.getTitleAt(tabIndex))
                                    .append(" will be lost.\n");
                        }
                        if (projectTab.runningVivadoProcess != null) {
                            consequences.append("Running build for project ").append(tab.getTitleAt(tabIndex))
                                    .append(" will be stopped.\n");
                        }
                    }
                    if (!consequences.isEmpty()) {
                        if (JOptionPane.showOptionDialog(frame, consequences.toString(), "Please confirm",
                                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                                new String[]{"Close", "Cancel"}, "Cancel") != 0) {
                            return;
                        }
                        StringBuilder sids = new StringBuilder();
                        for (int tabIndex = 1; tabIndex < tab.getTabCount(); tabIndex++) {
                            ProjectTabComponent projectTab = (ProjectTabComponent) tab.getComponentAt(tabIndex);
                            if (projectTab.runningVivadoProcess != null) {
                                if (!sids.isEmpty()) {
                                    sids.append(',');
                                }
                                sids.append(projectTab.runningVivadoProcess);
                            }
                        }
                        if (!sids.isEmpty()) {
                            try {
                                new ProcessBuilder("pkill", "-KILL", "-s", sids.toString()).start();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                    try (PrintStream ps = new PrintStream(settingsFileName)) {
                        ps.println("frameX=" + frame.getX());
                        ps.println("frameY=" + frame.getY());
                        ps.println("frameWidth=" + frame.getWidth());
                        ps.println("frameHeight=" + frame.getHeight());
                        ps.println("vivadoPath=" + vivadoDirectory.textField.getText());
                        ps.println("projectsPath=" + projectsDirectory.textField.getText());
                        ps.println("buildPath=" + buildDirectory.textField.getText());
                        StringBuilder openProjects = new StringBuilder("openProjects=");
                        String separator = "";
                        for (int tabIndex = 1; tabIndex < tab.getTabCount(); tabIndex++) {
                            openProjects.append(separator).append(tabIndex == tab.getSelectedIndex() ? "*" : "")
                                    .append(tab.getTitleAt(tabIndex));
                            separator = ",";
                        }
                        ps.println(openProjects);
                    } catch (FileNotFoundException exception) {
                        JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    System.exit(0);
                }
            });
            JLabel guide = new JLabel();
            startPanel.add(guide);
            JButton openButton = new JButton("Open project");
            startPanel.add(openButton);
            SettingsSupplier settingsSupplier = new SettingsSupplier() {
                @Override
                public String getBuildPath() {
                    return buildDirectory.textField.getText();
                }

                @Override
                public String getVivadoPath() {
                    return vivadoDirectory.textField.getText();
                }

                @Override
                public String getProjectPath() {
                    return projectsDirectory.textField.getText();
                }
            };
            openButton.addActionListener(_ -> {
                String projectDirectoryText = projectsDirectory.textField.getText();
                File[] files = new File(projectDirectoryText).listFiles(x -> x.getName().endsWith(".flows"));
                if (files == null || files.length == 0) {
                    JOptionPane.showMessageDialog(null,
                            "No project files (with name ending in .flows) found in directory " + projectDirectoryText,
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                JDialog dialog = new JDialog(frame, "Select the project", true);
                Container contentPane = dialog.getContentPane();
                contentPane.setLayout(new BorderLayout());
                DefaultListModel<String> model = new DefaultListModel<>();
                JList<String> projectList = new JList<>(model);
                for (File file : files) {
                    model.addElement(file.getName());
                }
                contentPane.add(new JScrollPane(projectList), BorderLayout.CENTER);
                JPanel buttonPanel = new JPanel();
                buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
                JButton okButton = new JButton("OK");
                String[] result = new String[1];
                ActionListener actionListener = _ -> {
                    result[0] = projectList.getSelectedValue();
                    dialog.setVisible(false);
                };
                projectList.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2) {
                            actionListener.actionPerformed(null);
                        }
                    }
                });
                okButton.addActionListener(actionListener);
                buttonPanel.add(okButton);
                contentPane.add(buttonPanel, BorderLayout.PAGE_END);
                dialog.setSize(400, 300);
                dialog.setLocation(frame.getX() + (frame.getWidth() - dialog.getWidth()) / 2,
                        frame.getY() + (frame.getHeight() - dialog.getHeight()) / 2);
                dialog.setVisible(true);
                if (result[0] != null) {
                    int tabCount = tab.getTabCount();
                    for (int index = 1; index < tabCount; index++) {
                        if (tab.getTitleAt(index).equals(result[0])) {
                            tab.setSelectedIndex(index);
                            return;
                        }
                    }
                    ProjectTabComponent newTab = openProjectTab(settingsSupplier, result[0], tab, tabCount);
                    if (newTab != null) {
                        tab.setSelectedComponent(newTab);
                    }
                }
            });
            startPanel.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    int width = startPanel.getWidth();
                    int height = startPanel.getHeight();
                    int labelWidth = Math.max(vivadoDirectory.label.getPreferredSize().width,
                            Math.max(buildDirectory.label.getPreferredSize().width,
                                    projectsDirectory.label.getPreferredSize().width));
                    int textHeight = vivadoDirectory.textField.getPreferredSize().height;
                    vivadoDirectory.position(10, width - 10, textHeight, labelWidth);
                    buildDirectory.position(textHeight + 10, width - 10, textHeight, labelWidth);
                    projectsDirectory.position(textHeight * 2 + 10, width - 10, textHeight, labelWidth);
                    openButton.setBounds(10, textHeight * 3 + 20, openButton.getPreferredSize().width,
                            openButton.getPreferredSize().height);
                    guide.setBounds(10, height - textHeight - 10, width, textHeight);
                }
            });
            Runnable textChangedListener = () -> {
                String guideText;
                vivadoDirectory.validityIndicator.active = !new File(
                        vivadoDirectory.textField.getText() + "/bin/vivado").canExecute();
                vivadoDirectory.validityIndicator.repaint();
                boolean buildDirectoryIndicatorActive = false, projectsDirectoryIndicatorActive = false;
                if (vivadoDirectory.validityIndicator.active) {
                    guideText = " Please enter path to Vivado (start typing to see list of directories at the path typed so far)";
                } else {
                    String buildDirectoryText = buildDirectory.textField.getText();
                    buildDirectoryIndicatorActive =
                            buildDirectoryText.equals("/") || !new File(buildDirectoryText).exists();
                    if (buildDirectoryIndicatorActive) {
                        guideText = " Please enter path to directory where generated Verilog files and temporary Vivado project files should be kept";
                    } else {
                        String projectsDirectoryText = projectsDirectory.textField.getText();
                        File[] projectsDirectoryFiles = new File(projectsDirectoryText).listFiles(
                                file -> file.getName().endsWith(".flows"));
                        projectsDirectoryIndicatorActive =
                                projectsDirectoryText.equals("/") || projectsDirectoryFiles == null;
                        guideText =
                                projectsDirectoryIndicatorActive ? " Please enter the projects directory to start" : "";
                    }
                }
                buildDirectory.validityIndicator.active = buildDirectoryIndicatorActive;
                buildDirectory.validityIndicator.repaint();
                projectsDirectory.validityIndicator.active = projectsDirectoryIndicatorActive;
                projectsDirectory.validityIndicator.repaint();
                guide.setText(guideText);
                openButton.setEnabled(!projectsDirectoryIndicatorActive);
            };
            JWindow listWindow = new JWindow();
            DefaultListModel<ListEntry> listModel = new DefaultListModel<>();
            JList<ListEntry> list = new JList<>(listModel);
            list.setFocusable(false);
            listWindow.getContentPane().add(new JScrollPane(list));
            listWindow.setSize(new Dimension(400, 200));
            new DirectorySuggester(vivadoDirectory.textField, listWindow, listModel, list,
                    textChangedListener).addListeners();
            new DirectorySuggester(buildDirectory.textField, listWindow, listModel, list,
                    textChangedListener).addListeners();
            new DirectorySuggester(projectsDirectory.textField, listWindow, listModel, list,
                    textChangedListener).addListeners();
            tab.addTab("Start", startPanel);
            String openProjectsList = settingsMap.get("openProjects");
            if (openProjectsList != null && openProjectsList.length() > 0) {
                int selectedIndex = 0;
                for (String project : openProjectsList.split(",")) {
                    boolean isSelectedTab = project.charAt(0) == '*';
                    if (isSelectedTab) {
                        project = project.substring(1);
                    }
                    if (openProjectTab(settingsSupplier, project, tab, tab.getTabCount()) != null) {
                        if (isSelectedTab) {
                            selectedIndex = tab.getTabCount() - 1;
                        }
                    }
                }
                tab.setSelectedIndex(selectedIndex);
            }
            textChangedListener.run();
            frame.getContentPane().add(tab);
            frame.setVisible(true);
        });
    }

    private static ProjectTabComponent openProjectTab(SettingsSupplier settingsSupplier, String name, JTabbedPane tab,
                                                      int tabCount) {
        if (!new File(settingsSupplier.getProjectPath() + name).exists()) {
            return null;
        }
        ProjectTabComponent component = new ProjectTabComponent(settingsSupplier, name, editorComponent -> {
            for (int index = 1; index < tab.getTabCount(); index++) {
                if (((ProjectTabComponent) tab.getComponentAt(index)).editorComponent == editorComponent) {
                    ((JLabel) tab.getTabComponentAt(index)).setText(getText(name, editorComponent.isContentModified()));
                }
            }
        });
        tab.addTab(name, component);
        JLabel tabLabel = new JLabel(getText(name, false));
        tab.setTabComponentAt(tabCount, tabLabel);
        int tabLabelPosition = tabLabel.getText().length() - 1;
        tabLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getX() >= tabLabel.getFontMetrics(tabLabel.getFont())
                        .stringWidth(tabLabel.getText().substring(0, tabLabelPosition))) {
                    tab.remove(component);
                    e.consume();
                } else {
                    if (tab.getSelectedComponent() == component) {
                        tab.requestFocus();
                    } else {
                        tab.setSelectedComponent(component);
                    }
                }
            }
        });
        return component;
    }

    private static String getText(String name, boolean status) {
        return (status ? "* " : "") + name + " \u274c";
    }
}
