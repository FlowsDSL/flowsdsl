// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import javax.swing.*;
import java.awt.*;

public class LineNumberComponent extends JComponent {
    private static Color COLOR;
    private final EditorComponent editor;

    LineNumberComponent(EditorComponent editor) {
        this.editor = editor;
        setFont(editor.getFont());
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (COLOR == null) {
            COLOR = new Color(0xc0c0c0);
        }
        g.setColor(Color.black);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(COLOR);
        g.drawLine(getWidth() - 5, 0, getWidth() - 5, getHeight() - 1);
        FontMetrics fm = getFontMetrics(getFont());
        int height = fm.getHeight();
        Rectangle clip = g.getClipBounds();
        int indexStart = Math.max(0, clip.y / height);
        int indexLimit = (clip.y + clip.height + height - 1) / height;
        int ascent = fm.getAscent();
        for (int i = indexStart; i < indexLimit && i < editor.lines.length; i++) {
            g.drawString(Integer.toString(i + 1), 4, i * height + ascent);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        int digits = Integer.toString(Math.max(1, editor.lines.length)).length();
        return new Dimension(18 + digits * (int) editor.getLetterWidth(), editor.getPreferredSize().height);
    }
}
