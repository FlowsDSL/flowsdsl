// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.SourceLocation;
import dsl.flows.sde.SourceLocationRange;
import dsl.flows.sde.utility.LineSplitter;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.function.Function;

public class EditorComponent extends JComponent
        implements KeyListener, MouseListener, MouseMotionListener, FocusListener, ActionListener {
    public enum ReplaceKind {Edit, Typing, Undo, Redo}

    record Underline(SourceFilenameAndLocationRange range, String details) {
    }

    record Highlighting(Fragment[][] fragments, Underline[] underlines) {
    }

    record IntRange(int start, int end) {
    }

    record Fragment(int end, int color) {
        static public Fragment[] EMPTY_ARRAY = new Fragment[0];
    }

    public record ReplaceRangeOperation(String[] replacedText, SourceLocationRange locationRange) {
    }

    static Color[] PALETTE;
    static Color BACKGROUND_COLOR, SELECTION_COLOR;
    static final int CURSOR_WIDTH = 3;
    final String filename;
    final Function<String[], Highlighting> highlightingFunction;
    final Consumer<EditorComponent> modificationStatusChangeListener;
    String[] lines;
    Highlighting highlighting;
    FontRenderContext fontRenderContext;
    SourceLocation anchor = null, cursor = new SourceLocation(0, 0);
    Timer fragmentRegenerationTimer;
    long fragmentRegenerationMoment;
    String editingDisabledReason;
    LinkedList<ReplaceRangeOperation> undoList = new LinkedList<>();
    LinkedList<ReplaceRangeOperation> redoList = new LinkedList<>();
    ReplaceRangeOperation lastTypingOperation;
    boolean contentModified;

    EditorComponent(String filename, Function<String[], Highlighting> highlightingFunction,
                    Consumer<EditorComponent> modificationStatusChangeListener) {
        this.filename = filename;
        this.highlightingFunction = highlightingFunction;
        this.modificationStatusChangeListener = modificationStatusChangeListener;
        if (PALETTE == null) {
            PALETTE = new Color[]{new Color(0xc0c0c0), new Color(0x80c080), new Color(0xc08080), new Color(0x808080)};
            BACKGROUND_COLOR = Color.black;
            SELECTION_COLOR = new Color(0x404080);
        }
        try {
            this.lines = LineSplitter.splitLines(Files.readString(Path.of(filename)));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        this.highlighting = highlightingFunction.apply(lines);
        setFont(new Font("Noto Mono", Font.PLAIN, 12));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addFocusListener(this);
        fontRenderContext = getFontMetrics(getFont()).getFontRenderContext();
        updatePreferredSize();
    }

    @Override
    public void paint(Graphics g) {
        double letterWidth = getLetterWidth();
        Graphics2D g2d = (Graphics2D) g;
        FontMetrics fm = getFontMetrics(getFont());
        Rectangle clipBounds = g.getClipBounds();
        g.setColor(BACKGROUND_COLOR);
        g.fillRect(0, 0, getWidth(), getHeight());
        int height = fm.getHeight();
        int indexLimit = Math.max(0, Math.min(lines.length, (clipBounds.y + clipBounds.height + height - 1) / height));
        int indexStart = Math.max(0, Math.min(lines.length, clipBounds.y / height));
        int ascent = fm.getAscent();
        int y = height * indexStart;
        SourceLocationRange range = anchor != null ? getSelectedRange() : null;
        for (int index = indexStart; index < indexLimit; index++) {
            IntRange positionRange = getPositionRange(range, index);
            if (positionRange != null) {
                g.setColor(SELECTION_COLOR);
                g.fillRect((int) (positionRange.start * letterWidth), y,
                        (int) ((positionRange.end - positionRange.start) * letterWidth), height);
            }
            if (highlighting != null) {
                int fragmentStart = 0;
                for (Fragment fragment : highlighting.fragments[index]) {
                    String textFragment = lines[index].substring(fragmentStart, fragment.end);
                    g.setColor(PALETTE[fragment.color]);
                    g2d.drawString(textFragment, (float) (letterWidth * fragmentStart), y + ascent);
                    fragmentStart = fragment.end;
                }
            } else {
                g.setColor(PALETTE[0]);
                g2d.drawString(lines[index], 0, y + ascent);
            }
            y += height;
        }
        if (highlighting != null) {
            g.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1, new float[]{2, 2}, 0));
            for (Underline underline : highlighting.underlines) {
                for (int index = underline.range.start.line(); index <= underline.range.end.line(); index++) {
                    int underliningY = height * (index + 1);
                    IntRange positionRange = getPositionRange(underline.range, index);
                    g2d.drawLine((int) (letterWidth * positionRange.start), underliningY,
                            (int) (letterWidth * positionRange.end), underliningY);
                }
            }
        }
        if (hasFocus()) {
            g.setColor(Color.white);
            g.fillRect((int) (cursor.position() * letterWidth), cursor.line() * fm.getHeight(), CURSOR_WIDTH,
                    fm.getHeight());
        }
    }

    public void select(SourceLocationRange range) {
        if (range != null) {
            moveCursor(range.start, false);
            moveCursor(range.end, true);
        } else {
            anchor = null;
            repaint();
        }
    }

    public void setEditingDisabledReason(String editingDisabledReason) {
        this.editingDisabledReason = editingDisabledReason;
        repaint();
    }

    private IntRange getPositionRange(SourceLocationRange range, int line) {
        if (range == null || line < range.start.line() || line > range.end.line()) {
            return null;
        }
        return new IntRange(range.start.line() == line ? range.start.position() : 0,
                line < range.end.line() ? lines[line].length() : range.end.position());
    }

    private SourceLocationRange getSelectedRange() {
        SourceLocation selectionStart, selectionEnd;
        if (anchor.compareTo(cursor) > 0) {
            selectionStart = cursor;
            selectionEnd = anchor;
        } else {
            selectionStart = anchor;
            selectionEnd = cursor;
        }
        return new SourceLocationRange(selectionStart, selectionEnd);
    }

    public double getLetterWidth() {
        return getFont().getStringBounds("A", fontRenderContext).getWidth();
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    private SourceLocation getSourceLocation(int x, int y) {
        int line = Math.min(y / getFontMetrics(getFont()).getHeight(), lines.length - 1);
        return new SourceLocation(line, Math.min((int) (x / getLetterWidth()), lines[line].length()));
    }

    private void moveCursor(SourceLocation location, boolean shift) {
        if (shift) {
            if (anchor == null) {
                anchor = cursor;
            }
        } else {
            anchor = null;
        }
        cursor = location;
        lastTypingOperation = null;
        int fontHeight = getFontMetrics(getFont()).getHeight();
        double letterWidth = getLetterWidth();
        scrollRectToVisible(new Rectangle((int) (location.position() * letterWidth), location.line() * fontHeight,
                (int) Math.ceil(letterWidth), fontHeight));
        repaint();
    }

    static class CursorAdvancer {
        final String[] lines;
        final boolean flipIsLetter, word;
        boolean done = false;

        CursorAdvancer(String[] lines, boolean flipIsLetter, boolean word) {
            this.lines = lines;
            this.flipIsLetter = flipIsLetter;
            this.word = word;
        }

        boolean shouldContinue(SourceLocation location) {
            if (word) {
                boolean isLetterAtLocation = isLetter(location) ^ flipIsLetter;
                if (done && isLetterAtLocation) {
                    return false;
                }
                done |= !isLetterAtLocation;
            } else {
                if (done) {
                    return false;
                }
                done = true;
            }
            return true;
        }

        private boolean isLetter(SourceLocation location) {
            return location.position() != lines[location.line()].length() &&
                    Character.isAlphabetic(lines[location.line()].charAt(location.position()));
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            CursorAdvancer advancer = new CursorAdvancer(lines, false, e.isControlDown());
            SourceLocation location = cursor;
            while (advancer.shouldContinue(location)) {
                SourceLocation next = getRightKeyLocation(location);
                if (next == null) {
                    break;
                }
                location = next;
            }
            moveCursor(location, e.isShiftDown());
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
            CursorAdvancer advancer = new CursorAdvancer(lines, true, e.isControlDown());
            SourceLocation location = cursor;
            while (true) {
                SourceLocation next = getLeftKeyLocation(location);
                if (next == null) {
                    break;
                }
                if (!advancer.shouldContinue(next)) {
                    break;
                }
                location = next;
            }
            moveCursor(location, e.isShiftDown());
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_UP) {
            if (cursor.line() == 0) {
                return;
            }
            moveCursor(new SourceLocation(cursor.line() - 1,
                    Math.min(cursor.position(), lines[cursor.line() - 1].length())), e.isShiftDown());
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            if (cursor.line() == lines.length - 1) {
                return;
            }
            moveCursor(new SourceLocation(cursor.line() + 1,
                    Math.min(cursor.position(), lines[cursor.line() + 1].length())), e.isShiftDown());
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_HOME) {
            moveCursor(new SourceLocation(e.isControlDown() ? 0 : cursor.line(), 0), e.isShiftDown());
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_END) {
            int newRow = e.isControlDown() ? lines.length - 1 : cursor.line();
            moveCursor(new SourceLocation(newRow, lines[newRow].length()), e.isShiftDown());
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            if (anchor == null) {
                SourceLocation location = getLeftKeyLocation(cursor);
                if (location != null) {
                    verifyEditingEnabledAndReplaceRange(new SourceLocationRange(location, cursor), ReplaceKind.Edit,
                            "");
                }
            } else {
                verifyEditingEnabledAndReplaceRange(getSelectedRange(), ReplaceKind.Edit, "");
            }
        }
        if (!e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_DELETE) {
            if (anchor == null) {
                SourceLocation location = getRightKeyLocation(cursor);
                if (location != null) {
                    verifyEditingEnabledAndReplaceRange(new SourceLocationRange(cursor, location), ReplaceKind.Edit,
                            "");
                }
            } else {
                verifyEditingEnabledAndReplaceRange(getSelectedRange(), ReplaceKind.Edit, "");
            }
        }
        if (!e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
            verifyEditingEnabledAndReplaceRange(getSelectedRangeOrEmptyOneAtCursor(), ReplaceKind.Edit, "", "");
        }
        if (((e.isControlDown() && e.getKeyCode() == KeyEvent.VK_X) ||
                (e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_DELETE)) && anchor != null) {
            SourceLocationRange selectedRange = getSelectedRange();
            setClipboardContents(concatenateContentLines(getContents(selectedRange)));
            verifyEditingEnabledAndReplaceRange(selectedRange, ReplaceKind.Edit, "");
        }
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {
            select(new SourceLocationRange(new SourceLocation(0, 0),
                    new SourceLocation(lines.length - 1, lines[lines.length - 1].length())));
        }
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_S) {
            try {
                try (PrintStream out = new PrintStream(filename + ".tmp")) {
                    String separator = "";
                    for (String line : lines) {
                        out.print(separator);
                        out.print(line);
                        separator = "\n";
                    }
                }
                new File(filename + ".tmp").renameTo(new File(filename));
                setModificationStatus(false);
            } catch (FileNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
        if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
            if (cursor.line() != 0) {
                int newLine = Math.max(0,
                        cursor.line() - (int) getVisibleRect().getHeight() / (getFontMetrics(getFont()).getHeight()));
                moveCursor(new SourceLocation(newLine, Math.min(cursor.position(), lines[newLine].length())),
                        e.isShiftDown());
            }
            e.consume();
        }
        if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
            if (cursor.line() != lines.length - 1) {
                int newLine = Math.min(lines.length - 1,
                        cursor.line() + (int) getVisibleRect().getHeight() / (getFontMetrics(getFont()).getHeight()));
                moveCursor(new SourceLocation(newLine, Math.min(cursor.position(), lines[newLine].length())),
                        e.isShiftDown());
            }
            e.consume();
        }
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C && anchor != null) {
            setClipboardContents(concatenateContentLines(getContents(getSelectedRange())));
        }
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_E) {
            StringBuilder builder = new StringBuilder();
            builder.append("```\n");
            for (int index = 0; index < lines.length; index++) {
                builder.append(String.format("%3d", index + 1)).append("  ").append(lines[index]).append("\n");
            }
            setClipboardContents(builder.append("```\n").toString());
        }
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
            undoRedo(e.isShiftDown() ? ReplaceKind.Redo : ReplaceKind.Undo);
        }
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Y) {
            undoRedo(ReplaceKind.Redo);
        }
        if ((e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) ||
                (e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_INSERT)) {
            Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(this);
            try {
                verifyEditingEnabledAndReplaceRange(getSelectedRangeOrEmptyOneAtCursor(), ReplaceKind.Edit,
                        LineSplitter.splitLines(contents.getTransferData(DataFlavor.stringFlavor).toString()));
            } catch (UnsupportedFlavorException | IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        char ch = e.getKeyChar();
        if (ch >= ' ' && ch <= '~') {
            verifyEditingEnabledAndReplaceRange(getSelectedRangeOrEmptyOneAtCursor(), ReplaceKind.Typing,
                    Character.toString(ch));
        }
    }

    private void undoRedo(ReplaceKind replaceKind) {
        if (isEditingDisabled()) {
            return;
        }
        LinkedList<ReplaceRangeOperation> list = replaceKind == ReplaceKind.Redo ? redoList : undoList;
        if (!list.isEmpty()) {
            ReplaceRangeOperation operation = list.removeLast();
            replaceRange(operation.locationRange, replaceKind, operation.replacedText);
        }
    }

    private SourceLocationRange getSelectedRangeOrEmptyOneAtCursor() {
        return anchor != null ? getSelectedRange() : new SourceLocationRange(cursor, cursor);
    }

    private String[] getContents(SourceLocationRange range) {
        String[] result = new String[range.end.line() - range.start.line() + 1];
        for (int index = range.start.line(); index <= range.end.line(); index++) {
            IntRange lineRange = getPositionRange(range, index);
            result[index - range.start.line()] = lines[index].substring(lineRange.start, lineRange.end);
        }
        return result;
    }

    private String concatenateContentLines(String[] content) {
        StringBuilder contentsBuilder = new StringBuilder(content[0]);
        for (int index = 1; index < content.length; index++) {
            contentsBuilder.append('\n').append(content[index]);
        }
        return contentsBuilder.toString();
    }

    private void setClipboardContents(String contents) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(contents), null);
    }

    private SourceLocation getRightKeyLocation(SourceLocation location) {
        if (location.position() != lines[location.line()].length()) {
            return new SourceLocation(location.line(), location.position() + 1);
        }
        if (location.line() == lines.length - 1) {
            return null;
        }
        return new SourceLocation(location.line() + 1, 0);
    }

    private SourceLocation getLeftKeyLocation(SourceLocation location) {
        if (location.position() != 0) {
            return new SourceLocation(location.line(), location.position() - 1);
        }
        if (location.line() == 0) {
            return null;
        }
        return new SourceLocation(location.line() - 1, lines[location.line() - 1].length());
    }

    public void verifyEditingEnabledAndReplaceRange(SourceLocationRange range, ReplaceKind replaceKind,
                                                    String... replacement) {
        if (isEditingDisabled()) {
            return;
        }
        replaceRange(range, replaceKind, replacement);
    }

    private void replaceRange(SourceLocationRange range, ReplaceKind replaceKind, String[] replacement) {
        setModificationStatus(true);
        String[] replacedText = getContents(range);
        String[] newLineArray = new String[range.start.line() + (lines.length - range.end.line()) + replacement.length -
                1];
        System.arraycopy(lines, 0, newLineArray, 0, range.start.line());
        String endLineSuffix = lines[range.end.line()].substring(range.end.position());
        newLineArray[range.start.line()] =
                lines[range.start.line()].substring(0, range.start.position()) + replacement[0] +
                        (replacement.length == 1 ? endLineSuffix : "");
        if (replacement.length != 1) {
            System.arraycopy(replacement, 1, newLineArray, range.start.line() + 1, replacement.length - 2);
            newLineArray[range.start.line() + replacement.length - 1] =
                    replacement[replacement.length - 1] + endLineSuffix;
        }
        System.arraycopy(lines, range.end.line() + 1, newLineArray, range.start.line() + replacement.length,
                lines.length - range.end.line() - 1);
        lines = newLineArray;
        highlighting = null;
        cursor = new SourceLocation(range.start.line() + replacement.length - 1,
                replacement.length == 1 ? (range.start.position() + replacement[0].length()) :
                        replacement[replacement.length - 1].length());
        if (replaceKind == ReplaceKind.Typing && lastTypingOperation != null &&
                lastTypingOperation.locationRange.end.compareTo(range.start) == 0) {
            undoList.removeLast();
            undoList.add(new ReplaceRangeOperation(lastTypingOperation.replacedText,
                    new SourceLocationRange(lastTypingOperation.locationRange.start, cursor)));
        } else {
            (replaceKind == ReplaceKind.Undo ? redoList : undoList).addLast(
                    new ReplaceRangeOperation(replacedText, new SourceLocationRange(range.start, cursor)));
        }
        if (replaceKind == ReplaceKind.Edit || replaceKind == ReplaceKind.Typing) {
            redoList.clear();
            anchor = null;
        } else {
            anchor = range.start;
        }
        updatePreferredSize();
        revalidate();
        repaint();
        scheduleFragmentRegeneration();
        lastTypingOperation = replaceKind == ReplaceKind.Typing ? undoList.getLast() : null;
    }

    private boolean isEditingDisabled() {
        if (editingDisabledReason != null) {
            JOptionPane.showMessageDialog(this, editingDisabledReason, "Editing is disabled",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        }
        return false;
    }

    private void setModificationStatus(boolean contentModified) {
        this.contentModified = contentModified;
        modificationStatusChangeListener.accept(this);
    }

    private void updatePreferredSize() {
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, line.length());
        }
        setPreferredSize(new Dimension((int) ((maxWidth + 1) * getLetterWidth()),
                lines.length * getFontMetrics(getFont()).getHeight()));
    }

    public void regenerateFragmentsIfPending() {
        if (fragmentRegenerationTimer == null) {
            return;
        }
        regenerateFragments();
    }

    public void regenerateFragments() {
        if (fragmentRegenerationTimer != null) {
            fragmentRegenerationTimer.stop();
            fragmentRegenerationTimer = null;
        }
        highlighting = highlightingFunction.apply(lines);
        repaint();
    }

    private void scheduleFragmentRegeneration() {
        long now = System.currentTimeMillis();
        fragmentRegenerationMoment = now + 1000;
        if (fragmentRegenerationTimer == null) {
            fragmentRegenerationTimer = new Timer(1000, this);
            fragmentRegenerationTimer.setRepeats(false);
            fragmentRegenerationTimer.start();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (fragmentRegenerationTimer == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long delay = fragmentRegenerationMoment - now;
        if (delay <= 0) {
            regenerateFragments();
        } else {
            fragmentRegenerationTimer.setInitialDelay((int) delay);
            fragmentRegenerationTimer.restart();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
            SourceLocation location = getSourceLocation(e.getX(), e.getY());
            if (location.position() < lines[location.line()].length()) {
                int start = location.position(), end = location.position();
                String line = lines[location.line()];
                boolean isLetter = Character.isLetter(line.charAt(location.position()));
                while (start > 0 && Character.isLetter(line.charAt(start - 1)) == isLetter) {
                    start--;
                }
                while (end < line.length() && Character.isLetter(line.charAt(end)) == isLetter) {
                    end++;
                }
                moveCursor(new SourceLocation(location.line(), start), false);
                moveCursor(new SourceLocation(location.line(), end), true);
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocus();
        moveCursor(getSourceLocation(e.getX(), e.getY()), false);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        moveCursor(getSourceLocation(e.getX(), e.getY()), true);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        String tooltip = null;
        if (highlighting != null) {
            SourceLocation sourceLocation = getSourceLocation(e.getX(), e.getY());
            for (Underline underline : highlighting.underlines) {
                if (sourceLocation.compareTo(underline.range.start) >= 0 &&
                        sourceLocation.compareTo(underline.range.end) < 0) {
                    tooltip = underline.range + ": " + underline.details;
                    break;
                }
            }
        }
        setToolTipText(tooltip);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void focusGained(FocusEvent e) {
        repaint();
    }

    @Override
    public void focusLost(FocusEvent e) {
        repaint();
    }

    boolean isContentModified() {
        return contentModified;
    }
}
