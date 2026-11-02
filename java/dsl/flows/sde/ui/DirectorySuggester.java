// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public record DirectorySuggester(JTextField textField, JWindow listWindow, DefaultListModel<ListEntry> listModel,
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
                    if (value != null && value.valid() && textField.getSelectionEnd() == textField.getSelectionStart()) {
                        String text = textField.getText();
                        textField.setText(
                                (text.startsWith("/") ? "" : "/") + text.substring(0, text.lastIndexOf('/') + 1) +
                                        value.name() + "/");
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
