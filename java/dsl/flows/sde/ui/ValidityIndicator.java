// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import javax.swing.*;
import java.awt.*;

public class ValidityIndicator extends JComponent {
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
