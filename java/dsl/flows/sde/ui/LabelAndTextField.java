// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import javax.swing.*;

public class LabelAndTextField {
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
