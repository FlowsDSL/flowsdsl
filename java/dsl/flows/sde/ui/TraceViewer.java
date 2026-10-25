// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.ui;

import dsl.flows.sde.utility.Deserializer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Consumer;

public class TraceViewer {
    static abstract class ColumnOrGroup {
        final int level;
        final String[] name;
        final String columnTitle;

        ColumnOrGroup(int level, String[] name) {
            this(level, name, buildColumnTitle(name));
        }

        ColumnOrGroup(int level, String[] name, String columnTitle) {
            this.level = level;
            this.name = name;
            this.columnTitle = columnTitle;
        }

        static String buildColumnTitle(String[] name) {
            final String columnTitle;
            StringBuilder builder = new StringBuilder("<html>");
            for (int nameIndex = name.length; nameIndex < 5; nameIndex++) {
                builder.append("<br/>");
            }
            for (int nameIndex = 0; nameIndex < name.length; nameIndex++) {
                if (nameIndex != 0) {
                    builder.append("<br/>");
                }
                builder.append(name[nameIndex]);
            }
            columnTitle = builder.append("</html>").toString();
            return columnTitle;
        }

        @Override
        public String toString() {
            return columnTitle;
        }
    }

    static abstract class Column extends ColumnOrGroup {
        Column(int level, String[] name) {
            super(level, name);
        }

        Column(int level, String[] name, String columnTitle) {
            super(level, name, columnTitle);
        }
    }

    static class TimeColumn extends Column {
        TimeColumn() {
            super(1, new String[]{"Time"}, "<html><br/><br/><br/><br/>Time</html>");
        }
    }

    static class ErrorColumn extends Column {
        ErrorColumn() {
            super(1, new String[]{"Error"}, "<html><br/><br/><br/><br/>Error</html>");
        }
    }

    static abstract class FieldColumn extends Column {
        final int offset;
        final OptionCondition[] optionConditions;

        FieldColumn(int level, String[] name, int offset, OptionCondition[] optionConditions) {
            super(level, name);
            this.offset = offset;
            this.optionConditions = optionConditions;
        }
    }

    static class BitArrayColumn extends FieldColumn {
        final int size;

        BitArrayColumn(int level, String[] name, int offset, int size, OptionCondition[] optionConditions) {
            super(level, name, offset, optionConditions);
            this.size = size;
        }
    }

    static class ProgramCounterColumn extends FieldColumn {
        ProgramCounterColumn(int level, String[] name, int offset) {
            super(level, name, offset, new OptionCondition[0]);
        }
    }

    static class OptionColumn extends FieldColumn {
        final int size;
        final OneOfType type;

        OptionColumn(int level, String[] name, int offset, int size, OneOfType type,
                     OptionCondition[] optionConditions) {
            super(level, name, offset, optionConditions);
            this.size = size;
            this.type = type;
        }
    }

    static class OptionCondition {
        final OptionColumn column;
        final int value;

        OptionCondition(OptionColumn column, int value) {
            this.column = column;
            this.value = value;
        }
    }

    static class Group extends ColumnOrGroup {
        Group(int level, String[] name) {
            super(level, name, buildColumnTitle(name));
        }
    }

    static abstract class Type {
        final String name;

        Type(String name) {
            this.name = name;
        }
    }

    static class BitType extends Type {
        BitType() {
            super("Bit");
        }
    }

    static class StructType extends Type {
        record Field(String name, int type, int arraySize) {
        }

        final Field[] fields;

        StructType(String name, Field[] fields) {
            super(name);
            this.fields = fields;
        }
    }

    static class OneOfType extends Type {
        final int[] options;

        OneOfType(String name, int[] options) {
            super(name);
            this.options = options;
        }
    }

    static class CycleCounterReferencePoint {
        long value;
    }

    record CycleCounterWrapper(long value, CycleCounterReferencePoint referencePoint) {
        public String toString() {
            long diff = value - referencePoint.value;
            if (diff == 0) {
                return "";
            }
            if (diff < 0) {
                return String.format("- %,d", -diff);
            }
            return String.format("+ %,d", diff);
        }
    }

    record CellWrapper(byte[] value, int size) {
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            int index;
            if ((size & 7) != 0 && (size & 7) <= 4) {
                index = 1;
                result.append(String.format("%x", value[0]));
            } else {
                index = 0;
            }
            while (index < value.length) {
                result.append(String.format("%02x", value[index] & 0xff));
                index++;
            }
            return result.toString();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CellWrapper castOther && Arrays.equals(value, castOther.value);
        }
    }

    static class FieldSelectorComponent extends JComponent {
        final ColumnOrGroup[] fields;
        final Consumer<boolean[]> listener;
        boolean[] selected;

        FieldSelectorComponent(ColumnOrGroup[] fields, Consumer<boolean[]> listener) {
            this.fields = fields;
            selected = new boolean[fields.length];
            this.listener = listener;
            Arrays.fill(selected, true);
            listener.accept(selected);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int index = e.getY() / getFontMetrics(getFont()).getHeight();
                    int level = fields[index].level;
                    boolean newValue = !selected[index];
                    selected[index++] = newValue;
                    while (index < selected.length && fields[index].level > level) {
                        selected[index++] = newValue;
                    }
                    repaint();
                    listener.accept(selected);
                }
            });
        }

        @Override
        public void paint(Graphics graphics) {
            FontMetrics fontMetrics = graphics.getFontMetrics();
            int height = fontMetrics.getHeight();
            int ascent = fontMetrics.getAscent();
            for (int index = 0; index < fields.length; index++) {
                String[] name = fields[index].name;
                int x = height * fields[index].level;
                int y = index * height;
                graphics.drawRect(x, y + 2, height - 4, height - 4);
                if (selected[index]) {
                    graphics.drawString("\u2713", x + (height - 2 - fontMetrics.stringWidth("\u2713")) / 2, y + ascent);
                }
                graphics.drawString(name[name.length - 1], x + height + 10, y + ascent);
            }
        }

        public Dimension getPreferredSize() {
            return new Dimension(300, fields.length * getFontMetrics(getFont()).getHeight());
        }
    }

    static byte[] extractValue(int[] raw, int offset, int size, int chunkSize) {
        byte[] result = new byte[(size + 7) >> 3];
        for (int i = 0; i < result.length; i++) {
            int byteLength = (i == 0 && ((size & 7) != 0) ? (size & 7) : 8);
            int index = offset / chunkSize;
            int start = chunkSize - (offset % chunkSize) - byteLength;
            if (start >= 0) {
                result[i] = (byte) ((raw[index] >> start) & ((1 << byteLength) - 1));
            } else {
                result[i] = (byte) (((raw[index] << (-start)) | (raw[index + 1] >> (chunkSize + start))) &
                        ((1 << byteLength) - 1));
            }
            offset += byteLength;
        }
        return result;
    }

    static int collectColumnsAndGroups(ArrayList<ColumnOrGroup> columnsAndGroups, Type[] types, int typeIndex,
                                       int arraySize, int level, int offset,
                                       ArrayList<OptionCondition> optionConditionPath, ArrayList<String> namePath,
                                       TreeMap<String, ArrayList<String>> pcValuesByInstance) {
        Type type = types[typeIndex];
        if (type instanceof StructType castStructType && castStructType.fields.length == 1) {
            StructType.Field field = castStructType.fields[0];
            if (field.name.equals("Value")) {
                typeIndex = field.type;
                type = types[typeIndex];
                arraySize = field.arraySize;
            }
        }
        if (type instanceof BitType) {
            columnsAndGroups.add(new BitArrayColumn(level, namePath.toArray(new String[0]), offset, arraySize,
                    optionConditionPath.toArray(new OptionCondition[0])));
            return offset + arraySize;
        }
        if (!namePath.isEmpty()) {
            columnsAndGroups.add(new Group(level, namePath.toArray(new String[0])));
        }
        int index = arraySize;
        while (index != 0) {
            index--;
            String arrayIndexString = arraySize != 1 ? "[" + index + "]" : "";
            if (type instanceof OneOfType castOneOfType) {
                int optionBits = 32 - Integer.numberOfLeadingZeros(castOneOfType.options.length - 1);
                namePath.add(arrayIndexString + "Option");
                OptionColumn column = new OptionColumn(level + 1, namePath.toArray(new String[0]), offset, optionBits,
                        castOneOfType, optionConditionPath.toArray(new OptionCondition[0]));
                columnsAndGroups.add(column);
                namePath.removeLast();
                int result = 0;
                for (int option : castOneOfType.options) {
                    optionConditionPath.add(new OptionCondition(column, option));
                    namePath.add(arrayIndexString + types[option].name);
                    result = Math.max(result,
                            collectColumnsAndGroups(columnsAndGroups, types, option, 1, level + 1, offset + optionBits,
                                    optionConditionPath, namePath, pcValuesByInstance));
                    optionConditionPath.removeLast();
                    namePath.removeLast();
                }
                offset = result;
            } else if (type instanceof StructType castStructType) {
                for (StructType.Field field : castStructType.fields) {
                    if (pcValuesByInstance != null && level == 2 && namePath.get(1).equals("State") &&
                            field.name.charAt(0) == 'A') {
                        int lineEnd = 2;
                        while (field.name.charAt(lineEnd) != '_') {
                            lineEnd++;
                        }
                        int positionEnd = lineEnd + 2;
                        while (positionEnd < field.name.length() && Character.isDigit(field.name.charAt(positionEnd))) {
                            positionEnd++;
                        }
                        int finalOffset = offset;
                        pcValuesByInstance.computeIfAbsent(namePath.get(0), x -> {
                            namePath.add("ProgramCounter");
                            columnsAndGroups.add(
                                    new ProgramCounterColumn(level + 1, namePath.toArray(new String[0]), finalOffset));
                            namePath.removeLast();
                            return new ArrayList<>();
                        }).add(field.name.substring(1, lineEnd) + ":" + field.name.substring(lineEnd + 1, positionEnd));
                        offset++;
                        continue;
                    }
                    namePath.add(field.name + arrayIndexString);
                    offset = collectColumnsAndGroups(columnsAndGroups, types, field.type,
                            field.arraySize != -1 ? field.arraySize : 1, level + 1, offset, optionConditionPath,
                            namePath, pcValuesByInstance);
                    namePath.removeLast();
                }
            } else {
                throw new RuntimeException("Invalid type");
            }
        }
        return offset;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java TraceViewer <path to trace directory>");
            System.exit(1);
        }
        String pathToTrace = args[0];
        Deserializer deserializer = new Deserializer(pathToTrace + "/Layout", false);
        int channelWidth = (int) deserializer.readInteger();
        int totalChannels = (int) deserializer.readInteger();
        int tracingChannels = (int) deserializer.readInteger();
        int typeCount = (int) deserializer.readInteger();
        Type[] types = new Type[typeCount];
        for (int typeIndex = 0; typeIndex < typeCount; typeIndex++) {
            String typeName = deserializer.readString();
            long typeType = deserializer.readInteger();
            Type type;
            if (typeType == 0) {
                type = new BitType();
            } else if (typeType == 1) {
                int optionCount = (int) deserializer.readInteger();
                int[] options = new int[optionCount];
                for (int optionIndex = 0; optionIndex < optionCount; optionIndex++) {
                    options[optionIndex] = (int) deserializer.readInteger();
                }
                type = new OneOfType(typeName, options);
            } else if (typeType == 2) {
                int fieldCount = (int) deserializer.readInteger();
                StructType.Field[] fields = new StructType.Field[fieldCount];
                for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                    String name = deserializer.readString();
                    int fieldType = (int) deserializer.readInteger();
                    int arraySize = (int) deserializer.readInteger();
                    fields[fieldIndex] = new StructType.Field(name, fieldType, arraySize);
                }
                type = new StructType(typeName, fields);
            } else {
                throw new RuntimeException("Invalid type type: " + typeType);
            }
            types[typeIndex] = type;
        }
        ArrayList<ColumnOrGroup> columnOrGroups = new ArrayList<>();
        columnOrGroups.add(new TimeColumn());
        columnOrGroups.add(new ErrorColumn());
        TreeMap<String, ArrayList<String>> pcValuesByInstance = new TreeMap<>();
        collectColumnsAndGroups(columnOrGroups, types, 0, 1, 0, channelWidth * (totalChannels + 2 - tracingChannels),
                new ArrayList<>(), new ArrayList<>(), pcValuesByInstance);
        int fromCoreMessagesCount = (int) deserializer.readInteger();
        for (int index = 0; index < fromCoreMessagesCount; index++) {
            int typeIndex = (int) deserializer.readInteger();
            int fromCoreMessageEndChannel = (int) deserializer.readInteger();
            ArrayList<String> path = new ArrayList<>();
            path.add(types[typeIndex].name);
            collectColumnsAndGroups(columnOrGroups, types, typeIndex, 1, 1,
                    channelWidth * (totalChannels + 2 - fromCoreMessageEndChannel), new ArrayList<>(), path, null);
        }
        ColumnOrGroup[] fieldArray = columnOrGroups.toArray(new ColumnOrGroup[0]);
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Trace");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(3000, 2000);
            JDialog dialog = new JDialog(frame, "Select columns", true);
            dialog.setSize(400, 1000);
            DefaultTableModel model = new DefaultTableModel(new Object[0][], fieldArray) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable table = new JTable(model);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            table.getTableHeader().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON3) {
                        dialog.setVisible(true);
                    }
                }
            });
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            CycleCounterReferencePoint cycleCounterReferencePoint = new CycleCounterReferencePoint();
            table.getSelectionModel().addListSelectionListener(e -> {
                int[] rows = table.getSelectionModel().getSelectedIndices();
                if (rows.length == 0) {
                    cycleCounterReferencePoint.value = 0;
                } else {
                    for (int index = 0; index < model.getColumnCount(); index++) {
                        Object value = model.getValueAt(rows[0], index);
                        if (value instanceof CycleCounterWrapper castWrapper) {
                            cycleCounterReferencePoint.value = castWrapper.value;
                            break;
                        }
                    }
                }
                model.fireTableRowsUpdated(0, model.getRowCount() - 1);
            });
            FieldSelectorComponent fieldSelector = new FieldSelectorComponent(fieldArray, selected -> {
                ArrayList<ColumnOrGroup> columnList = new ArrayList<>();
                for (int index = 0; index < selected.length; index++) {
                    ColumnOrGroup fog = fieldArray[index];
                    if (fog instanceof Column && selected[index]) {
                        columnList.add(fog);
                    }
                }
                int mask = (1 << channelWidth) - 1;
                try (FileChannel channel = FileChannel.open(Path.of(pathToTrace + "/Data"), StandardOpenOption.READ)) {
                    MappedByteBuffer bb = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    ArrayList<Object[]> data = new ArrayList<>();
                    Object[] values = new Object[columnList.size()];
                    boolean[] updated = new boolean[totalChannels + 2];
                    int[] rawState = new int[totalChannels + 2];
                    rawState[1] = (1 << channelWidth) - 1;
                    long cycleCount = -1;
                    while (bb.remaining() > 0) {
                        int word = bb.getInt();
                        int value = word & mask;
                        int chunk = word >> channelWidth;
                        if (chunk == totalChannels + 1) {
                            if (cycleCount == -1) {
                                cycleCount = 0;
                            } else {
                                cycleCount += value + 1;
                            }
                            boolean addRow = false;
                            LinkedList<Object> row = new LinkedList<>();
                            for (int index = 0; index < columnList.size(); index++) {
                                ColumnOrGroup cog = columnList.get(index);
                                if (cog instanceof TimeColumn) {
                                    row.add(new CycleCounterWrapper(cycleCount, cycleCounterReferencePoint));
                                }
                                if (cog instanceof ErrorColumn) {
                                    if (rawState[1] != ((1 << channelWidth) - 1)) {
                                        row.add(rawState[1]);
                                        addRow = true;
                                    } else {
                                        row.add(null);
                                    }
                                }
                                if (cog instanceof FieldColumn castField) {
                                    Object newValue;
                                    if (castField instanceof ProgramCounterColumn) {
                                        ArrayList<String> pcValues = pcValuesByInstance.get(castField.name[0]);
                                        int offset = castField.offset;
                                        newValue = "<invalid>";
                                        for (int ipIndex = 0; ipIndex < pcValues.size(); ipIndex++) {
                                            if ((rawState[offset / channelWidth] &
                                                    (1 << (channelWidth - 1 - (offset % channelWidth)))) != 0) {
                                                newValue = pcValues.get(ipIndex);
                                                break;
                                            }
                                            offset++;
                                        }
                                    } else {
                                        boolean matchedAllOptionConditions = true;
                                        for (OptionCondition optionCondition : castField.optionConditions) {
                                            if (optionCondition.column.type.options[extractValue(rawState,
                                                    optionCondition.column.offset, optionCondition.column.size,
                                                    channelWidth)[0]] != optionCondition.value) {
                                                matchedAllOptionConditions = false;
                                                break;
                                            }
                                        }
                                        if (castField instanceof BitArrayColumn castBitArray) {
                                            newValue = matchedAllOptionConditions ? new CellWrapper(
                                                    extractValue(rawState, castBitArray.offset, castBitArray.size,
                                                            channelWidth), castBitArray.size) : null;
                                        } else if (castField instanceof OptionColumn castOption) {
                                            newValue = matchedAllOptionConditions ?
                                                    types[castOption.type.options[extractValue(rawState,
                                                            castOption.offset, castOption.size,
                                                            channelWidth)[0]]].name : null;
                                        } else {
                                            newValue = null;
                                        }
                                    }
                                    int startChannel = castField.offset / channelWidth;
                                    if (startChannel < (totalChannels + 2 - tracingChannels) ? updated[startChannel] :
                                            !Objects.equals(values[index], newValue)) {
                                        addRow = true;
                                        row.add(newValue);
                                        values[index] = newValue;
                                    } else {
                                        row.add(null);
                                    }
                                }
                            }
                            if (addRow) {
                                data.add(row.toArray(new Object[0]));
                            }
                            Arrays.fill(updated, false);
                        } else {
                            updated[totalChannels + 1 - chunk] = true;
                            rawState[totalChannels + 1 - chunk] = value;
                        }
                    }
                    model.setDataVector(data.toArray(new Object[0][]), columnList.toArray(new ColumnOrGroup[0]));
                    FontMetrics fm = table.getFontMetrics(table.getFont());
                    for (int columnIndex = 0; columnIndex < model.getColumnCount(); columnIndex++) {
                        int width = 0;
                        for (int rowIndex = 0; rowIndex < model.getRowCount(); rowIndex++) {
                            Object value = model.getValueAt(rowIndex, columnIndex);
                            if (value != null) {
                                width = Math.max(width, fm.stringWidth(value.toString()));
                            }
                        }
                        TableColumn column = table.getColumnModel().getColumn(columnIndex);
                        column.setIdentifier(columnList.get(columnIndex));
                        column.setPreferredWidth(Math.min(width, 200) + 10);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            Container contentPane = dialog.getContentPane();
            contentPane.setLayout(new BorderLayout());
            contentPane.add(new JScrollPane(fieldSelector), BorderLayout.CENTER);
            table.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_H && e.isControlDown()) {
                        LinkedList<Column> fieldColumns = new LinkedList<>();
                        for (int index = 0; index < table.getColumnCount(); index++) {
                            fieldColumns.add((Column) table.getColumnModel().getColumn(index).getIdentifier());
                        }
                        int depth = 0;
                        for (ColumnOrGroup cog : fieldColumns) {
                            depth = Math.max(depth, cog.name.length);
                        }
                        StringBuilder result = new StringBuilder("<style>td { vertical-align: top }</style>\n<table>");
                        for (int level = 0; level < depth; level++) {
                            result.append("<tr>");
                            String name = null;
                            int width = 0, height = 0;
                            for (ColumnOrGroup cog : fieldColumns) {
                                if (cog instanceof Column castColumn) {
                                    if (level < castColumn.name.length) {
                                        String fieldName = castColumn.name[level];
                                        if (level == 2 && castColumn.name[1].equals("State")) {
                                            if (fieldName.charAt(0) == 'S') {
                                                fieldName = fieldName.substring(1);
                                            } else if (fieldName.charAt(0) == 'M') {
                                                int index = 1;
                                                while (!Character.isLetter(fieldName.charAt(index))) {
                                                    index++;
                                                }
                                                fieldName = fieldName.substring(index);
                                            }
                                        }
                                        StringBuilder nameWithBreaks = new StringBuilder();
                                        for (int index = 0; index < fieldName.length(); index++) {
                                            char ch = fieldName.charAt(index);
                                            if (index != 0 && Character.isUpperCase(ch) &&
                                                    (index + 1 != fieldName.length() &&
                                                            Character.isLowerCase(fieldName.charAt(index + 1)))) {
                                                nameWithBreaks.append("<br/>");
                                            }
                                            nameWithBreaks.append(ch);
                                        }
                                        fieldName = nameWithBreaks.toString();
                                        if (width != 0 && !fieldName.equals(name)) {
                                            result.append("<td rowspan=").append(height).append(" colspan=")
                                                    .append(width).append(">").append(name).append("</td>");
                                            width = 0;
                                        }
                                        if (width == 0) {
                                            name = fieldName;
                                            height = level + 1 == castColumn.name.length ? depth - level : 1;
                                        }
                                        width++;
                                    } else {
                                        if (width != 0) {
                                            result.append("<td rowspan=").append(height).append(" colspan=")
                                                    .append(width).append(">").append(name).append("</td>");
                                            width = 0;
                                        }
                                    }
                                }
                            }
                            if (width != 0) {
                                result.append("<td rowspan=").append(height).append(" colspan=").append(width)
                                        .append(">").append(name).append("</td>");
                            }
                            result.append("</tr>\n");
                        }
                        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
                            result.append("<tr>");
                            for (int columnIndex = 0; columnIndex < table.getColumnCount(); columnIndex++) {
                                result.append("<td>");
                                Object value = table.getValueAt(rowIndex, columnIndex);
                                if (value != null) {
                                    result.append("<code>").append(value).append("</code>");
                                }
                                result.append("</td>");
                            }
                            result.append("</tr>\n");
                        }
                        result.append("</table>");
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(result.toString()), null);
                        e.consume();
                    }
                }
            });
            frame.getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
            frame.setVisible(true);
        });
    }
}
