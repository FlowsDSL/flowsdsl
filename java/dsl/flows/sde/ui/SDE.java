// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

public class SDE {
    public static void start(String settingsFileName, Map<String, String> settingsMap) {
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
            LabelAndTextField vivadoDirectory = new LabelAndTextField("Path to Vivado:",
                    settingsMap.get("vivadoPath"), startPanel);
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

    public static void showErrorMessage(String message) {
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
