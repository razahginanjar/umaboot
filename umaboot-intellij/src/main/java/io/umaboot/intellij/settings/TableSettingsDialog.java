package io.umaboot.intellij.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.JavaTypeMapper;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.TableModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-table customization dialog: pick an explicit className override and a
 * curated Java type for each column. Opens on double-click of a row in the
 * UmabootSettingsPanel's table list.
 *
 * <p>The dialog is stateless from a persistence standpoint — its result
 * ({@link #result()}) is a pure {@link UmabootConfig.TableOverride} record
 * that the caller is responsible for merging into the panel's overrides map
 * (and, on Apply, into {@code umaboot.yaml}).</p>
 */
public final class TableSettingsDialog extends DialogWrapper {

    private final String tableName;
    private final TableModel table;

    private final JBTextField classNameField = new JBTextField();
    private final ColumnTypeTableModel columnsModel;

    /** Empty-string marker used in the Java-type combo to mean "no override". */
    private static final String NO_OVERRIDE = "";

    public TableSettingsDialog(@Nullable Project project,
                               String tableName,
                               TableModel table,
                               UmabootConfig.TableOverride existing) {
        super(project, true /* modal */);
        this.tableName = tableName;
        this.table = table;
        this.classNameField.setText(existing == null ? "" : existing.className());

        Map<String, String> overridesByColumn = new LinkedHashMap<>();
        if (existing != null) {
            for (var entry : existing.columns().entrySet()) {
                overridesByColumn.put(entry.getKey(), entry.getValue().javaType());
            }
        }
        this.columnsModel = new ColumnTypeTableModel(table, overridesByColumn);

        setTitle("Customize table — " + tableName);
        setOKButtonText("Save");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JBPanel<JBPanel<?>> root = new JBPanel<>(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.gridx = 0; c.gridy = 0;
        c.insets = JBUI.insets(4);

        // Header row: tableName + classNameField
        JBPanel<JBPanel<?>> header = new JBPanel<>(new GridBagLayout());
        GridBagConstraints hc = new GridBagConstraints();
        hc.fill = GridBagConstraints.HORIZONTAL;
        hc.insets = new java.awt.Insets(2, 4, 2, 8);
        hc.gridx = 0; hc.gridy = 0;
        header.add(new JBLabel("Table:"), hc);
        hc.gridx = 1; hc.weightx = 1.0;
        header.add(new JBLabel(tableName), hc);

        hc.gridx = 0; hc.gridy = 1; hc.weightx = 0;
        header.add(new JBLabel("Class name (override):"), hc);
        hc.gridx = 1; hc.weightx = 1.0;
        classNameField.setToolTipText(
                "Leave empty to use the default (singularize + PascalCase, with classNameStripPrefix applied).");
        header.add(classNameField, hc);

        root.add(header, c);
        c.gridy = 1;

        // Columns table
        JBTable columnsTable = new JBTable(columnsModel);
        columnsTable.setRowHeight(24);
        columnsTable.setFillsViewportHeight(true);
        columnsTable.getColumnModel().getColumn(0).setPreferredWidth(160); // name
        columnsTable.getColumnModel().getColumn(1).setPreferredWidth(140); // db type
        columnsTable.getColumnModel().getColumn(2).setPreferredWidth(220); // java type

        // Java-type combo cell editor — shows the curated list with a
        // "(default)" option (empty string) at the top.
        JComboBox<String> typeCombo = new JComboBox<>();
        typeCombo.addItem(NO_OVERRIDE);
        for (String t : JavaTypeMapper.CURATED_OVERRIDE_TYPES) typeCombo.addItem(t);
        // Show shorter display labels but keep the canonical value as the model item.
        typeCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    javax.swing.JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                String v = value == null ? "" : value.toString();
                String display = v.isEmpty() ? "(default)" : displayName(v);
                return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
            }
        });

        TableColumn javaTypeCol = columnsTable.getColumnModel().getColumn(2);
        javaTypeCol.setCellEditor(new DefaultCellEditor(typeCombo));

        JScrollPane scroll = new JScrollPane(columnsTable);
        scroll.setPreferredSize(new Dimension(640, 320));

        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1.0;
        root.add(scroll, c);

        c.gridy = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weighty = 0;
        JBLabel hint = new JBLabel(
                "Tip — '(default)' uses the JDBC-type mapping. Pick a Java type to override per column.");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        root.add(hint, c);

        return root;
    }

    /**
     * Build the result the caller persists. Returns an empty override (which
     * the caller drops from the overrides map) when nothing meaningful was set.
     */
    public UmabootConfig.TableOverride result() {
        String className = classNameField.getText().trim();
        Map<String, UmabootConfig.ColumnOverride> columns = new LinkedHashMap<>();
        for (int i = 0; i < table.columns().size(); i++) {
            String columnName = table.columns().get(i).name();
            String type = columnsModel.overrideTypeAt(i);
            if (type != null && !type.isEmpty()) {
                columns.put(columnName, new UmabootConfig.ColumnOverride(type));
            }
        }
        return new UmabootConfig.TableOverride(className, columns);
    }

    /** Strip package qualifiers for display, leaving primitives and short names alone. */
    private static String displayName(String fqn) {
        if (fqn == null || fqn.isEmpty()) return "";
        if (fqn.endsWith("[]")) return fqn;        // byte[]
        if (fqn.contains("<")) {
            int lt = fqn.indexOf('<');
            String head = fqn.substring(0, lt);
            String tail = fqn.substring(lt);
            int dot = head.lastIndexOf('.');
            return (dot < 0 ? head : head.substring(dot + 1)) + tail;
        }
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    /**
     * Table model: column 0 = name (read-only), column 1 = DB type (read-only),
     * column 2 = Java-type override (editable, sentinel-empty = "use default").
     */
    private static final class ColumnTypeTableModel extends AbstractTableModel {
        private final TableModel table;
        private final String[] overrides; // indexed by column position; "" = no override
        private static final String[] HEADERS = {"Column", "DB type", "Java type"};

        ColumnTypeTableModel(TableModel table, Map<String, String> overridesByColumn) {
            this.table = table;
            this.overrides = new String[table.columns().size()];
            for (int i = 0; i < table.columns().size(); i++) {
                overrides[i] = overridesByColumn.getOrDefault(table.columns().get(i).name(), "");
            }
        }

        String overrideTypeAt(int row) {
            return overrides[row];
        }

        @Override public int getRowCount() { return table.columns().size(); }
        @Override public int getColumnCount() { return HEADERS.length; }
        @Override public String getColumnName(int c) { return HEADERS[c]; }
        @Override public boolean isCellEditable(int r, int c) { return c == 2; }

        @Override
        public Object getValueAt(int row, int col) {
            ColumnModel cm = table.columns().get(row);
            return switch (col) {
                case 0 -> cm.name();
                case 1 -> cm.sqlType();
                case 2 -> overrides[row];
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 2) {
                overrides[row] = value == null ? "" : value.toString();
                fireTableCellUpdated(row, col);
            }
        }
    }

    // Utility — Swing-style alias to keep imports tidy
}
