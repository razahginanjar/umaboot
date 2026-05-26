package io.umaboot.intellij.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootYamlIO;
import io.umaboot.core.introspection.Introspector;
import io.umaboot.core.introspection.JdbcDrivers;
import io.umaboot.core.introspection.mysql.MysqlIntrospector;
import io.umaboot.core.introspection.postgres.PostgresIntrospector;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.intellij.UmabootConfigLocator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Per-project Settings panel for Umaboot.
 *
 * <p>Sections:</p>
 * <ol>
 *   <li>Connection — JDBC URL, username, password, schema, plus a "Test Connection" button.</li>
 *   <li>Tables — "Refresh Tables" button populates a checkbox list from the live database.
 *       Selected tables drive {@code generation.tables.include}; everything else is
 *       implicitly excluded by being absent.</li>
 *   <li>Generation — architecture, persistence, MyBatis style, MapStruct toggle, basePackage,
 *       projectName, projectGroup, Spring Boot / Java versions, Lombok, OpenAPI, output mode,
 *       output dir.</li>
 * </ol>
 *
 * <p>Apply serializes the panel state back to {@code umaboot.yaml} at the project root.</p>
 */
public final class UmabootSettingsPanel {

    private final Project project;
    private final JBPanel<JBPanel<?>> root;

    // Connection — v0.8 redesign:
    //   * databaseTypeCombo : "postgresql" | "mysql"
    //   * mode radios       : Host (build URL from parts) / URL (paste raw)
    //   * Host card         : hostField + paramsField
    //   * URL card          : urlField
    //   * Always-visible    : databaseField, schemaField, username, password
    private final ComboBox<String> databaseTypeCombo =
            new ComboBox<>(new String[]{"postgresql", "mysql"});
    private final javax.swing.JRadioButton hostModeRadio = new javax.swing.JRadioButton("Host");
    private final javax.swing.JRadioButton urlModeRadio  = new javax.swing.JRadioButton("URL");
    private final JBTextField hostField     = new JBTextField();
    private final JBTextField paramsField   = new JBTextField();
    private final JBTextField urlField      = new JBTextField();
    private final JBTextField databaseField = new JBTextField();
    private final JBTextField schemaField   = new JBTextField();
    private final JBTextField usernameField = new JBTextField();
    private final JBPasswordField passwordField = new JBPasswordField();
    private final JBPanel<JBPanel<?>> connectionCardContainer =
            new JBPanel<>(new java.awt.CardLayout());
    private final JButton testConnectionButton = new JButton("Test Connection");
    private final JBLabel connectionStatusLabel = new JBLabel(" ");

    // Tables
    private final JButton refreshTablesButton = new JButton("Refresh Tables");
    private final CheckBoxList<String> tableList = new CheckBoxList<>();
    private final JBLabel tablesStatusLabel = new JBLabel(" ");
    private final JBTextField classNameStripPrefixField = new JBTextField();

    // Generation
    private final ComboBox<String> architectureCombo = new ComboBox<>(new String[]{"mvc", "hexagonal", "ddd"});
    private final ComboBox<String> persistenceCombo = new ComboBox<>(new String[]{"jpa", "mybatis", "jooq"});
    private final ComboBox<String> mybatisStyleCombo = new ComboBox<>(new String[]{"xml", "annotation"});
    private final JBCheckBox useMapStructCheckbox = new JBCheckBox("Use MapStruct (JPA only)");
    private final JBTextField basePackageField = new JBTextField();
    private final JBTextField projectNameField = new JBTextField();
    private final JBTextField projectGroupField = new JBTextField();
    private final ComboBox<String> springBootVersionCombo = makeEditableCombo();
    private final ComboBox<String> javaVersionCombo = makeEditableCombo();
    private final JBCheckBox useLombokCheckbox = new JBCheckBox("Use Lombok");
    private final ComboBox<String> openApiStyleCombo = new ComboBox<>(new String[]{"yaml", "annotation", "none"});
    private final ComboBox<String> injectionStyleCombo = new ComboBox<>(new String[]{"constructor", "lombok", "autowired"});
    private final ComboBox<String> validationStyleCombo = new ComboBox<>(new String[]{"jakarta", "none", "service"});
    private final ComboBox<String> dtoStyleCombo = new ComboBox<>(new String[]{"class", "record"});
    private final ComboBox<String> dtoShapeCombo = new ComboBox<>(new String[]{"separate", "single"});
    private final ComboBox<String> exceptionStyleCombo = new ComboBox<>(new String[]{"problemdetail", "envelope"});
    private final JBCheckBox auditEnabledCheckbox = new JBCheckBox("Detect audit fields (created_at / updated_at / created_by / updated_by)");
    private final JBCheckBox softDeleteEnabledCheckbox = new JBCheckBox("Detect soft delete (deleted_at / is_deleted)");
    private final JBCheckBox dockerEnabledCheckbox = new JBCheckBox("Emit Dockerfile + docker-compose.yml");
    private final ComboBox<String> ciStyleCombo = new ComboBox<>(new String[]{"none", "github", "gitlab"});
    private final ComboBox<String> loggingStyleCombo = new ComboBox<>(new String[]{"plain", "json"});
    private final JBCheckBox correlationIdCheckbox = new JBCheckBox("Add correlation-id filter (X-Correlation-Id -> MDC)");
    private final JBCheckBox testsEnabledCheckbox = new JBCheckBox("Generate integration tests (@SpringBootTest + Testcontainers)");
    private final ComboBox<String> paginationStyleCombo = new ComboBox<>(new String[]{"offset", "cursor"});
    private final ComboBox<String> securityStyleCombo = new ComboBox<>(new String[]{"none", "basic", "jwt"});
    private final ComboBox<String> outputModeCombo = new ComboBox<>(new String[]{"standalone", "overlay"});
    private final ComboBox<String> applicationConfigFormatCombo =
            new ComboBox<>(new String[]{"yaml", "properties"});
    private final JBCheckBox useProjectDirectoryCheckbox =
            new JBCheckBox("Use project directory (where umaboot.yaml lives)");
    private final JBTextField outputDirField = new JBTextField();

    private UmabootConfig loaded;
    private boolean dirty = false;

    public UmabootSettingsPanel(Project project) {
        this.project = project;
        this.root = build();
        wireListeners();
        load();
        populateVersionsAsync();
    }

    public JComponent getRoot() {
        return new JBScrollPane(root);
    }

    // ------------------------------------------------------------ layout

    private JBPanel<JBPanel<?>> build() {
        JBPanel<JBPanel<?>> p = new JBPanel<>(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = JBUI.insets(4);

        p.add(buildConnectionGroup(), c); c.gridy++;
        p.add(buildTablesGroup(), c); c.gridy++;
        p.add(buildGenerationGroup(), c); c.gridy++;

        // Filler
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        p.add(new JBPanel<>(), c);
        return p;
    }

    private JBPanel<JBPanel<?>> buildConnectionGroup() {
        JBPanel<JBPanel<?>> g = formPanel("Connection");
        int r = 0;

        addRow(g, r++, "Database type:", databaseTypeCombo);

        // Mode toggle row
        javax.swing.ButtonGroup modeGroup = new javax.swing.ButtonGroup();
        modeGroup.add(hostModeRadio);
        modeGroup.add(urlModeRadio);
        hostModeRadio.setSelected(true);
        JBPanel<JBPanel<?>> modeRow = new JBPanel<>(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        modeRow.add(hostModeRadio);
        modeRow.add(urlModeRadio);
        addRow(g, r++, "Connection mode:", modeRow);

        // Card container — swaps between Host card and URL card.
        // Database lives INSIDE the host card so URL mode is self-contained
        // (the URL is the only source of connection info in URL mode).
        JBPanel<JBPanel<?>> hostCard = new JBPanel<>(new GridBagLayout());
        addRow(hostCard, 0, "Host:",       hostField);
        addRow(hostCard, 1, "Database:",   databaseField);
        addRow(hostCard, 2, "Parameters:", paramsField);
        JBPanel<JBPanel<?>> urlCard = new JBPanel<>(new GridBagLayout());
        addRow(urlCard, 0, "JDBC URL:", urlField);
        connectionCardContainer.add(hostCard, "host");
        connectionCardContainer.add(urlCard,  "url");

        GridBagConstraints cardC = new GridBagConstraints();
        cardC.gridx = 0; cardC.gridy = r++; cardC.gridwidth = 2;
        cardC.fill = GridBagConstraints.HORIZONTAL; cardC.weightx = 1.0;
        cardC.insets = JBUI.insets(2, 4);
        g.add(connectionCardContainer, cardC);

        // Shared fields (visible in both modes) — note: Database is NOT here anymore.
        addRow(g, r++, "Schema:",   schemaField);
        addRow(g, r++, "Username:", usernameField);
        addRow(g, r++, "Password:", passwordField);

        // Test Connection button + status label
        JBPanel<JBPanel<?>> buttons = new JBPanel<>(new BorderLayout());
        buttons.add(testConnectionButton, BorderLayout.WEST);
        buttons.add(connectionStatusLabel, BorderLayout.CENTER);
        connectionStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = r; c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        c.insets = JBUI.insets(2, 4);
        g.add(buttons, c);
        return g;
    }

    private JBPanel<JBPanel<?>> buildTablesGroup() {
        JBPanel<JBPanel<?>> g = formPanel("Tables to generate");

        JBPanel<JBPanel<?>> top = new JBPanel<>(new BorderLayout());
        top.add(refreshTablesButton, BorderLayout.WEST);
        top.add(tablesStatusLabel, BorderLayout.CENTER);
        tablesStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        JBScrollPane scroll = new JBScrollPane(tableList);
        scroll.setPreferredSize(new Dimension(0, 180));

        // Class-name strip prefix row — applies the prefix-strip rule to every
        // generated entity / DTO / repository class name, BEFORE singularize +
        // PascalCase. Tables that don't start with the prefix are left alone.
        JBPanel<JBPanel<?>> stripRow = new JBPanel<>(new BorderLayout());
        stripRow.add(new JBLabel("Strip prefix from class names:"), BorderLayout.WEST);
        classNameStripPrefixField.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        stripRow.add(classNameStripPrefixField, BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        c.insets = JBUI.insets(2, 4);
        g.add(top, c);
        c.gridy = 1;
        g.add(stripRow, c);
        c.gridy = 2; c.fill = GridBagConstraints.BOTH; c.weighty = 1.0;
        g.add(scroll, c);
        return g;
    }

    private JBPanel<JBPanel<?>> buildGenerationGroup() {
        JBPanel<JBPanel<?>> g = formPanel("Generation");
        int r = 0;
        addRow(g, r++, "Architecture:", architectureCombo);
        addRow(g, r++, "Persistence:", persistenceCombo);
        addRow(g, r++, "MyBatis style:", mybatisStyleCombo);
        addRow(g, r++, "", useMapStructCheckbox);
        addRow(g, r++, "Base package:", basePackageField);
        addRow(g, r++, "Project name:", projectNameField);
        addRow(g, r++, "Project group:", projectGroupField);
        addRow(g, r++, "Spring Boot version:", springBootVersionCombo);
        addRow(g, r++, "Java version:", javaVersionCombo);
        addRow(g, r++, "", useLombokCheckbox);
        addRow(g, r++, "Injection style:", injectionStyleCombo);
        addRow(g, r++, "Validation style:", validationStyleCombo);
        addRow(g, r++, "DTO style:", dtoStyleCombo);
        addRow(g, r++, "DTO shape:", dtoShapeCombo);
        addRow(g, r++, "Exception style:", exceptionStyleCombo);
        addRow(g, r++, "", auditEnabledCheckbox);
        addRow(g, r++, "", softDeleteEnabledCheckbox);
        addRow(g, r++, "", dockerEnabledCheckbox);
        addRow(g, r++, "CI:", ciStyleCombo);
        addRow(g, r++, "Logging:", loggingStyleCombo);
        addRow(g, r++, "", correlationIdCheckbox);
        addRow(g, r++, "", testsEnabledCheckbox);
        addRow(g, r++, "Pagination:", paginationStyleCombo);
        addRow(g, r++, "Security:", securityStyleCombo);
        addRow(g, r++, "OpenAPI style:", openApiStyleCombo);
        addRow(g, r++, "App config format:", applicationConfigFormatCombo);
        addRow(g, r++, "Output mode:", outputModeCombo);
        addRow(g, r++, "", useProjectDirectoryCheckbox);
        addRow(g, r++, "Output dir:", outputDirField);
        return g;
    }

    // ------------------------------------------------------------ helpers

    private static JBPanel<JBPanel<?>> formPanel(String title) {
        JBPanel<JBPanel<?>> p = new JBPanel<>(new GridBagLayout());
        p.setBorder(IdeBorderFactory.createTitledBorder(title, true));
        return p;
    }

    private static void addRow(JBPanel<JBPanel<?>> p, int row, String label, JComponent field) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row; lc.fill = GridBagConstraints.HORIZONTAL;
        lc.insets = new Insets(2, 4, 2, 8);
        p.add(new JBLabel(label), lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row; fc.weightx = 1.0;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.insets = new Insets(2, 0, 2, 4);
        p.add(field, fc);
    }

    // ------------------------------------------------------------ listeners

    private void wireListeners() {
        testConnectionButton.addActionListener(e -> testConnection());
        refreshTablesButton.addActionListener(e -> refreshTables());

        // Mark dirty on any change
        java.awt.event.ActionListener mark = e -> dirty = true;
        architectureCombo.addActionListener(mark);
        persistenceCombo.addActionListener(mark);
        mybatisStyleCombo.addActionListener(mark);
        outputModeCombo.addActionListener(mark);
        applicationConfigFormatCombo.addActionListener(mark);
        // "Use project directory" checkbox — when checked, outputDir is forced
        // to "." in YAML (which OutputDirResolver maps to the directory of
        // umaboot.yaml). Disables the text field so the user can't accidentally
        // type a value that would be ignored.
        useProjectDirectoryCheckbox.addActionListener(e -> {
            outputDirField.setEnabled(!useProjectDirectoryCheckbox.isSelected());
            dirty = true;
        });
        useMapStructCheckbox.addActionListener(mark);
        useLombokCheckbox.addActionListener(mark);
        openApiStyleCombo.addActionListener(mark);
        injectionStyleCombo.addActionListener(mark);
        validationStyleCombo.addActionListener(mark);
        dtoStyleCombo.addActionListener(mark);
        dtoShapeCombo.addActionListener(mark);
        exceptionStyleCombo.addActionListener(mark);
        auditEnabledCheckbox.addActionListener(mark);
        softDeleteEnabledCheckbox.addActionListener(mark);
        dockerEnabledCheckbox.addActionListener(mark);
        ciStyleCombo.addActionListener(mark);
        loggingStyleCombo.addActionListener(mark);
        correlationIdCheckbox.addActionListener(mark);
        testsEnabledCheckbox.addActionListener(mark);
        paginationStyleCombo.addActionListener(mark);
        securityStyleCombo.addActionListener(mark);
        springBootVersionCombo.addActionListener(mark);
        javaVersionCombo.addActionListener(mark);
        databaseTypeCombo.addActionListener(mark);

        // Connection mode radios — swap the card AND mark dirty.
        hostModeRadio.addActionListener(e -> {
            if (hostModeRadio.isSelected()) {
                ((java.awt.CardLayout) connectionCardContainer.getLayout())
                        .show(connectionCardContainer, "host");
                dirty = true;
            }
        });
        urlModeRadio.addActionListener(e -> {
            if (urlModeRadio.isSelected()) {
                ((java.awt.CardLayout) connectionCardContainer.getLayout())
                        .show(connectionCardContainer, "url");
                dirty = true;
            }
        });

        // Cross-disable: "lombok" injection requires useLombok checked.
        useLombokCheckbox.addActionListener(e -> {
            if (!useLombokCheckbox.isSelected() && "lombok".equals(injectionStyleCombo.getSelectedItem())) {
                injectionStyleCombo.setSelectedItem("constructor");
            }
        });
        for (JBTextField f : List.of(urlField, usernameField, schemaField,
                hostField, paramsField, databaseField,
                basePackageField, projectNameField, projectGroupField,
                outputDirField, classNameStripPrefixField)) {
            f.getDocument().addDocumentListener(new SimpleDocListener(() -> dirty = true));
        }
        passwordField.getDocument().addDocumentListener(new SimpleDocListener(() -> dirty = true));
    }

    // ------------------------------------------------------------ Test Connection

    private void testConnection() {
        connectionStatusLabel.setForeground(Color.GRAY);
        connectionStatusLabel.setText("Testing...");
        testConnectionButton.setEnabled(false);

        // Test Connection works even when database is empty (so the user can
        // verify host/credentials before they've decided on a DB). We compose
        // the URL inline rather than going through the Connection record's
        // strict constructor, which would refuse host mode without a database.
        final String url;
        try {
            url = composeUrlForTest();
        } catch (RuntimeException ex) {
            connectionStatusLabel.setText("Invalid form: " + ex.getMessage());
            connectionStatusLabel.setForeground(Color.RED);
            testConnectionButton.setEnabled(true);
            return;
        }
        final String user = usernameField.getText().trim();
        final String pass = new String(passwordField.getPassword());

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String result;
            Color color;
            JdbcDrivers.registerAll();
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                if (conn.isValid(3)) {
                    result = "Connected: " + safeMeta(conn);
                    color = new Color(0x2E7D32); // green
                } else {
                    result = "Connection invalid";
                    color = Color.RED;
                }
            } catch (Throwable t) {
                result = "Failed: " + t.getMessage();
                color = Color.RED;
            }
            final String msg = result;
            final Color c = color;
            SwingUtilities.invokeLater(() -> {
                String finalMsg = msg;
                Color  finalColor = c;
                // If the probe succeeded but the form is still missing fields that
                // Apply / Refresh Tables need, surface that as an amber warning so
                // the user notices BEFORE clicking the next button. The probe
                // itself stays lenient (host/credentials only).
                if (c.equals(new Color(0x2E7D32))) {
                    String warning = warningForMissingApplyFields();
                    if (warning != null) {
                        finalMsg = msg + " — " + warning;
                        finalColor = new Color(0xC78A00); // amber
                    }
                }
                connectionStatusLabel.setText(finalMsg);
                connectionStatusLabel.setForeground(finalColor);
                testConnectionButton.setEnabled(true);
            });
        });
    }

    /**
     * Returns an amber-colored warning message when Test Connection succeeds but
     * the form is still missing fields that Apply / Refresh Tables would need
     * (see {@link UmabootConfig.Connection#introspectionTarget()}). Returns
     * {@code null} when nothing is missing.
     *
     * <p>This computes the same target {@code introspectionTarget()} would, but
     * leniently — without throwing — so we can surface a hint instead of an
     * error. In URL mode we explicitly read the database from the URL itself
     * rather than the (now hidden) database field, matching how the Connection
     * record's canonical-form constructor resolves it.</p>
     */
    private String warningForMissingApplyFields() {
        String type = (String) databaseTypeCombo.getSelectedItem();
        if (type == null) type = "postgresql";

        // Reproduce the canonical constructor's database-resolution leniently.
        String database = hostModeRadio.isSelected()
                ? databaseField.getText().trim()
                : UmabootConfig.Connection.parseDatabaseFromUrl(urlField.getText().trim());
        String schema = schemaField.getText().trim();
        String target = "mysql".equals(type)
                ? (database.isBlank() ? schema : database)
                : schema;

        if (!target.isBlank()) return null;
        String label = "mysql".equals(type) ? "Database" : "Schema";
        return label + " is empty — fill in before Apply / Refresh Tables";
    }

    /** Compose the URL for a Test Connection probe — lenient (database optional). */
    private String composeUrlForTest() {
        if (urlModeRadio.isSelected()) {
            return urlField.getText().trim();
        }
        String type = (String) databaseTypeCombo.getSelectedItem();
        if (type == null || type.isBlank()) type = "postgresql";
        String host     = hostField.getText().trim();
        String params   = paramsField.getText().trim();
        String database = databaseField.getText().trim();
        if (params.startsWith("?")) {
            throw new IllegalArgumentException(
                    "Parameters field must not start with '?' — the program adds it automatically");
        }
        String base = "jdbc:" + type + "://" + host;
        if (!database.isBlank()) base += "/" + database;
        if (!params.isBlank())   base += "?" + params;
        return base;
    }

    /**
     * Build a {@link UmabootConfig.Connection} from the current form values.
     * Triggers the canonical-form validation (leading '?' in params, missing
     * host/database in host mode, empty url in url mode).
     *
     * <p><b>URL-mode contract:</b> the (now hidden) database field is ignored —
     * the canonical constructor parses the database out of the URL. This avoids
     * the stale-state bug where a database typed in host mode would silently
     * survive a swap to URL mode and contradict the URL.</p>
     */
    private UmabootConfig.Connection currentFormConnection() {
        String mode = hostModeRadio.isSelected() ? "host" : "url";
        String type = (String) databaseTypeCombo.getSelectedItem();
        String database = "url".equals(mode)
                ? ""                                       // URL mode: derive from URL only
                : databaseField.getText().trim();          // host mode: field is authoritative
        return new UmabootConfig.Connection(
                mode,
                type,
                hostField.getText().trim(),
                paramsField.getText().trim(),
                urlField.getText().trim(),
                database,
                schemaField.getText().trim(),
                usernameField.getText().trim(),
                new String(passwordField.getPassword()),
                null);
    }

    private static String safeMeta(Connection conn) {
        try {
            return conn.getMetaData().getDatabaseProductName() + " "
                    + conn.getMetaData().getDatabaseProductVersion();
        } catch (Throwable ignored) {
            return "OK";
        }
    }

    // ------------------------------------------------------------ Refresh Tables

    private void refreshTables() {
        tablesStatusLabel.setForeground(Color.GRAY);
        tablesStatusLabel.setText("Reading schema...");
        refreshTablesButton.setEnabled(false);

        // Refresh Tables needs the introspect target (db for mysql, schema for
        // postgres), so we go through the strict Connection record. Validation
        // errors (missing host/database, leading '?' in params) surface here.
        final UmabootConfig.Connection formConn;
        try {
            formConn = currentFormConnection();
        } catch (RuntimeException ex) {
            tablesStatusLabel.setText("Invalid form: " + ex.getMessage());
            tablesStatusLabel.setForeground(Color.RED);
            refreshTablesButton.setEnabled(true);
            return;
        }

        // Level 1 — fail fast if the introspection target is empty. Without this,
        // we'd open a JDBC connection just to silently get 0 tables back.
        if (formConn.introspectionTarget().isBlank()) {
            String missing = "mysql".equals(formConn.type()) ? "Database" : "Schema";
            tablesStatusLabel.setText("Please fill in " + missing + " before refreshing tables");
            tablesStatusLabel.setForeground(Color.RED);
            refreshTablesButton.setEnabled(true);
            return;
        }

        final String url  = formConn.url();
        final String user = formConn.username();
        final String pass = formConn.password();
        final String introspectionTarget = formConn.introspectionTarget();
        final boolean isMysql = "mysql".equals(formConn.type());

        // Capture currently-selected tables so we preserve them after refresh
        final List<String> currentlySelected = new ArrayList<>();
        for (int i = 0; i < tableList.getItemsCount(); i++) {
            String name = tableList.getItemAt(i);
            if (name != null && tableList.isItemSelected(i)) currentlySelected.add(name);
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String status;
            Color color;
            List<String> tables = List.of();
            JdbcDrivers.registerAll();
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                Introspector introspector = isMysql
                        ? new MysqlIntrospector(conn) : new PostgresIntrospector(conn);
                SchemaModel sm = introspector.introspect(introspectionTarget);
                tables = sm.tables().stream()
                        .filter(t -> !t.junction())  // hide junctions; user picks logical entities
                        .map(t -> t.name())
                        .sorted()
                        .toList();
                status = tables.size() + " tables found";
                color = new Color(0x2E7D32);
            } catch (Throwable t) {
                status = "Failed: " + t.getMessage();
                color = Color.RED;
            }
            final List<String> tablesFinal = tables;
            final String statusFinal = status;
            final Color colorFinal = color;
            SwingUtilities.invokeLater(() -> {
                tableList.clear();
                for (String name : tablesFinal) {
                    boolean wasSelected = currentlySelected.contains(name)
                            || (loaded != null && loaded.generation().tables().include().contains(name));
                    // If no include filter is set yet, include everything by default
                    boolean defaultInclude = loaded == null
                            || loaded.generation().tables().include().isEmpty();
                    tableList.addItem(name, name, defaultInclude || wasSelected);
                }
                tablesStatusLabel.setText(statusFinal);
                tablesStatusLabel.setForeground(colorFinal);
                refreshTablesButton.setEnabled(true);
                dirty = true;
            });
        });
    }

    // ------------------------------------------------------------ load / save

    public void load() {
        Path file = configFile();
        if (Files.exists(file)) {
            try {
                loaded = UmabootYamlIO.load(file);
            } catch (Exception ex) {
                // Bad YAML shouldn't prevent the panel from opening — load defaults
                // and surface the error in the connection-status label so the user
                // can see what's wrong without staring at an empty form.
                loaded = blankConfig();
                connectionStatusLabel.setText("Failed to read " + file.getFileName() + ": " + ex.getMessage());
                connectionStatusLabel.setForeground(Color.RED);
            }
        } else {
            loaded = blankConfig();
        }
        applyToFields(loaded);
        dirty = false;
    }

    public void save() {
        UmabootConfig config = readFromFields();
        UmabootYamlIO.save(configFile(), config);
        loaded = config;
        dirty = false;
    }

    public boolean isModified() {
        return dirty;
    }

    private void applyToFields(UmabootConfig c) {
        // Connection — new shape (mode/type/host/params/url/database/schema)
        var conn = c.connection();
        databaseTypeCombo.setSelectedItem(conn.type());
        hostField.setText(conn.host());
        paramsField.setText(conn.params());
        urlField.setText(conn.url());
        databaseField.setText(conn.database());
        schemaField.setText(conn.schema());
        usernameField.setText(conn.username());
        passwordField.setText(conn.password());
        // Select mode + show the corresponding card
        if ("url".equals(conn.mode())) {
            urlModeRadio.setSelected(true);
            ((java.awt.CardLayout) connectionCardContainer.getLayout())
                    .show(connectionCardContainer, "url");
        } else {
            hostModeRadio.setSelected(true);
            ((java.awt.CardLayout) connectionCardContainer.getLayout())
                    .show(connectionCardContainer, "host");
        }

        architectureCombo.setSelectedItem(c.generation().architecture());
        persistenceCombo.setSelectedItem(c.generation().persistence());
        mybatisStyleCombo.setSelectedItem(c.generation().mybatis().style());
        useMapStructCheckbox.setSelected(c.generation().jpa().useMapStruct());
        basePackageField.setText(c.generation().basePackage());
        projectNameField.setText(c.generation().projectName());
        projectGroupField.setText(c.generation().projectGroup());
        springBootVersionCombo.setSelectedItem(c.generation().springBootVersion());
        javaVersionCombo.setSelectedItem(c.generation().javaVersion());
        useLombokCheckbox.setSelected(c.generation().useLombok());
        openApiStyleCombo.setSelectedItem(c.generation().openapi().style());
        injectionStyleCombo.setSelectedItem(c.generation().injection().style());
        validationStyleCombo.setSelectedItem(c.generation().validation().style());
        dtoStyleCombo.setSelectedItem(c.generation().dto().style());
        dtoShapeCombo.setSelectedItem(c.generation().dto().shape());
        exceptionStyleCombo.setSelectedItem(c.generation().exception().style());
        auditEnabledCheckbox.setSelected(c.generation().audit().enabled());
        softDeleteEnabledCheckbox.setSelected(c.generation().softDelete().enabled());
        dockerEnabledCheckbox.setSelected(c.generation().docker().enabled());
        ciStyleCombo.setSelectedItem(c.generation().ci().style());
        loggingStyleCombo.setSelectedItem(c.generation().logging().style());
        correlationIdCheckbox.setSelected(c.generation().logging().correlationId());
        testsEnabledCheckbox.setSelected(c.generation().tests().enabled());
        paginationStyleCombo.setSelectedItem(c.generation().pagination().style());
        securityStyleCombo.setSelectedItem(c.generation().security().style());
        outputModeCombo.setSelectedItem(c.generation().output().mode());
        applicationConfigFormatCombo.setSelectedItem(c.generation().applicationConfig().format());
        // "Use project directory" shortcut: outputDir == "." means "the directory
        // of umaboot.yaml" (per OutputDirResolver). Reflect that in the checkbox.
        String od = c.generation().outputDir();
        boolean isProjectDir = od != null && od.trim().equals(".");
        useProjectDirectoryCheckbox.setSelected(isProjectDir);
        outputDirField.setEnabled(!isProjectDir);
        outputDirField.setText(isProjectDir ? "" : (od == null ? "" : od));

        // Tables come from a live refresh; pre-populate from include list so the
        // user sees their saved selections even without clicking Refresh first.
        classNameStripPrefixField.setText(c.generation().tables().classNameStripPrefix());
        tableList.clear();
        for (String name : c.generation().tables().include()) {
            tableList.addItem(name, name, true);
        }
        if (c.generation().tables().include().isEmpty()) {
            tablesStatusLabel.setText("Click Refresh Tables to load from the database");
            tablesStatusLabel.setForeground(Color.GRAY);
        } else {
            tablesStatusLabel.setText(c.generation().tables().include().size() + " tables in include list");
            tablesStatusLabel.setForeground(Color.GRAY);
        }
    }

    private UmabootConfig readFromFields() {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < tableList.getItemsCount(); i++) {
            if (tableList.isItemSelected(i)) {
                String n = tableList.getItemAt(i);
                if (n != null) selected.add(n);
            }
        }

        var connection = currentFormConnection();

        var jpa = new UmabootConfig.JpaOptions(useMapStructCheckbox.isSelected());
        var mybatis = new UmabootConfig.MyBatisOptions(
                Optional.ofNullable((String) mybatisStyleCombo.getSelectedItem()).orElse("xml"));
        var tables = new UmabootConfig.TableFilterOptions(
                selected,
                List.of(),
                classNameStripPrefixField.getText().trim());
        // Preserve DDD options from the loaded config (no UI for them yet)
        var ddd = loaded != null ? loaded.generation().ddd() : UmabootConfig.DddOptions.defaults();
        var output = new UmabootConfig.OutputOptions(
                Optional.ofNullable((String) outputModeCombo.getSelectedItem()).orElse("standalone"));

        var applicationConfig = new UmabootConfig.ApplicationConfigOptions(
                Optional.ofNullable((String) applicationConfigFormatCombo.getSelectedItem()).orElse("yaml"));

        var openapi = new UmabootConfig.OpenApiOptions(
                Optional.ofNullable((String) openApiStyleCombo.getSelectedItem()).orElse("yaml"));

        var injection = new UmabootConfig.InjectionOptions(
                Optional.ofNullable((String) injectionStyleCombo.getSelectedItem()).orElse("constructor"));

        var validation = new UmabootConfig.ValidationOptions(
                Optional.ofNullable((String) validationStyleCombo.getSelectedItem()).orElse("jakarta"));

        var dto = new UmabootConfig.DtoOptions(
                Optional.ofNullable((String) dtoStyleCombo.getSelectedItem()).orElse("class"),
                Optional.ofNullable((String) dtoShapeCombo.getSelectedItem()).orElse("separate"));

        var exception = new UmabootConfig.ExceptionOptions(
                Optional.ofNullable((String) exceptionStyleCombo.getSelectedItem()).orElse("problemdetail"));

        // Audit + softDelete: preserve any custom column names from the loaded config;
        // the panel UI just exposes the enabled flag.
        var loadedAudit = loaded != null ? loaded.generation().audit() : UmabootConfig.AuditOptions.defaults();
        var audit = new UmabootConfig.AuditOptions(
                auditEnabledCheckbox.isSelected(),
                loadedAudit.createdAt(),
                loadedAudit.updatedAt(),
                loadedAudit.createdBy(),
                loadedAudit.updatedBy());
        var loadedSoftDelete = loaded != null ? loaded.generation().softDelete() : UmabootConfig.SoftDeleteOptions.defaults();
        var softDelete = new UmabootConfig.SoftDeleteOptions(
                softDeleteEnabledCheckbox.isSelected(),
                loadedSoftDelete.column());

        // Phase H — Docker / CI / logging
        var loadedDocker = loaded != null ? loaded.generation().docker() : UmabootConfig.DockerOptions.defaults();
        var docker = new UmabootConfig.DockerOptions(
                dockerEnabledCheckbox.isSelected(),
                loadedDocker.baseImage(),
                loadedDocker.port());
        var ci = new UmabootConfig.CiOptions(
                Optional.ofNullable((String) ciStyleCombo.getSelectedItem()).orElse("none"));
        var logging = new UmabootConfig.LoggingOptions(
                Optional.ofNullable((String) loggingStyleCombo.getSelectedItem()).orElse("plain"),
                correlationIdCheckbox.isSelected());
        var tests = new UmabootConfig.TestOptions(testsEnabledCheckbox.isSelected());
        var pagination = new UmabootConfig.PaginationOptions(
                Optional.ofNullable((String) paginationStyleCombo.getSelectedItem()).orElse("offset"));

        // Security: panel chooses the style; users + jwt subconfig are preserved
        // from the loaded YAML (or seeded with sensible defaults for first-time
        // jwt picks so config-load validation passes).
        var loadedSecurity = loaded != null ? loaded.generation().security() : UmabootConfig.SecurityOptions.defaults();
        String securityStyle = Optional.ofNullable((String) securityStyleCombo.getSelectedItem()).orElse("none");
        java.util.List<UmabootConfig.UserCredentials> securityUsers = loadedSecurity.users();
        if (securityUsers.isEmpty() && !"none".equals(securityStyle)) {
            // Seed a default admin user when enabling security for the first time.
            securityUsers = java.util.List.of(new UmabootConfig.UserCredentials(
                    "admin", "admin", java.util.List.of("ADMIN", "USER")));
        }
        UmabootConfig.JwtOptions loadedJwt = loadedSecurity.jwt();
        String jwtSecret = loadedJwt.secret();
        if ("jwt".equals(securityStyle) && (jwtSecret == null || jwtSecret.isEmpty())) {
            // Seed a placeholder secret so config-load doesn't reject the panel save.
            // The generated application.yml warns the user to externalize before deploying.
            jwtSecret = "change-me-this-needs-at-least-32-characters-of-entropy";
        }
        var jwt = new UmabootConfig.JwtOptions(
                jwtSecret, loadedJwt.expirationMinutes(), loadedJwt.header(), loadedJwt.prefix());
        var security = new UmabootConfig.SecurityOptions(securityStyle, securityUsers, jwt);

        var generation = new UmabootConfig.Generation(
                Optional.ofNullable((String) architectureCombo.getSelectedItem()).orElse("mvc"),
                Optional.ofNullable((String) persistenceCombo.getSelectedItem()).orElse("jpa"),
                basePackageField.getText().trim(),
                projectNameField.getText().trim(),
                projectGroupField.getText().trim(),
                comboText(springBootVersionCombo),
                comboText(javaVersionCombo),
                useLombokCheckbox.isSelected(),
                openapi,
                injection,
                validation,
                dto,
                exception,
                audit,
                softDelete,
                docker,
                ci,
                logging,
                tests,
                pagination,
                security,
                useProjectDirectoryCheckbox.isSelected()
                        ? "."
                        : (outputDirField.getText().trim().isEmpty()
                                ? null
                                : outputDirField.getText().trim()),
                jpa, mybatis, tables, ddd, output, applicationConfig);

        return new UmabootConfig(connection, generation);
    }

    private Path configFile() {
        if (project.getBasePath() == null) return Path.of(UmabootConfigLocator.PRIMARY);
        return UmabootConfigLocator.findConfigFile(Path.of(project.getBasePath()));
    }

    private static UmabootConfig blankConfig() {
        var connection = new UmabootConfig.Connection(
                /* mode     */ "host",
                /* type     */ "postgresql",
                /* host     */ "localhost:5432",
                /* params   */ "",
                /* url      */ null,
                /* database */ "your_db",
                /* schema   */ "public",
                /* username */ "postgres",
                /* password */ "postgres",
                /* driver   */ null);
        var generation = new UmabootConfig.Generation(
                "mvc", "jpa",
                "com.example.app", "your-app", "com.example",
                "3.3.5", "17", true,
                UmabootConfig.OpenApiOptions.defaults(),
                UmabootConfig.InjectionOptions.defaults(),
                UmabootConfig.ValidationOptions.defaults(),
                UmabootConfig.DtoOptions.defaults(),
                UmabootConfig.ExceptionOptions.defaults(),
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(),
                UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(),
                UmabootConfig.PaginationOptions.defaults(),
                UmabootConfig.SecurityOptions.defaults(),
                null,
                new UmabootConfig.JpaOptions(false),
                new UmabootConfig.MyBatisOptions("xml"),
                UmabootConfig.TableFilterOptions.allowAll(),
                UmabootConfig.DddOptions.defaults(),
                UmabootConfig.OutputOptions.defaults(),
                UmabootConfig.ApplicationConfigOptions.defaults());
        return new UmabootConfig(connection, generation);
    }

    /** Editable combo so the user can pick from suggestions OR type their own value. */
    private static ComboBox<String> makeEditableCombo() {
        ComboBox<String> combo = new ComboBox<>();
        combo.setEditable(true);
        return combo;
    }

    /** Read the editor field of an editable ComboBox (since selectedItem may be null after typing). */
    private static String comboText(ComboBox<String> combo) {
        Object item = combo.getEditor().getItem();
        if (item != null) return item.toString().trim();
        Object selected = combo.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    /** Cached service instance — populated once on first async call. */
    private volatile io.umaboot.core.version.VersionMetadataService versionService;

    /** Whether the listeners that re-filter the combos are currently active. */
    private boolean filteringInstalled = false;

    /**
     * The two ItemListeners installed on the version combos. Held as fields so we
     * can detach them temporarily when programmatically replacing one combo's
     * model from inside the other combo's listener — preventing the bidirectional
     * filter from re-entering itself and blowing the EDT stack.
     */
    private java.awt.event.ItemListener javaListener;
    private java.awt.event.ItemListener springBootListener;

    /**
     * Populates Spring Boot + Java version dropdowns asynchronously and wires
     * bidirectional filtering between them.
     *
     * <ul>
     *   <li>When the Java version changes, the Spring Boot combo is repopulated
     *       with the major appropriate for that Java (8/11 → 2.7.x; 17/21 → 3.x).</li>
     *   <li>When the Spring Boot version changes, the Java combo is repopulated
     *       with the Java versions that major supports.</li>
     * </ul>
     *
     * <p>Both combos remain editable so users can type a version not in the
     * suggested list. The filtering listeners disable themselves while
     * programmatically updating models so we don't recurse.</p>
     */
    private void populateVersionsAsync() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            io.umaboot.core.version.VersionMetadataService svc =
                    new io.umaboot.core.version.VersionMetadataService();
            this.versionService = svc;
            // Pre-warm the live fetch on the pooled thread so the first refilter is fast.
            svc.getSpringBootVersions();
            SwingUtilities.invokeLater(() -> {
                refilterSpringBootForJava();
                refilterJavaForSpringBoot();
                installFilterListeners();
                // Reset dirty since async population isn't a user edit.
                dirty = false;
            });
        });
    }

    /** Reloads the Spring Boot combo to match the current Java version selection. */
    private void refilterSpringBootForJava() {
        if (versionService == null) return;
        String currentJava = comboText(javaVersionCombo);
        String currentBoot = comboText(springBootVersionCombo);
        java.util.List<String> springBoot = versionService.getSpringBootVersionsFor(currentJava);
        // Detach the SB listener so the programmatic setSelectedItem inside
        // replaceComboModel doesn't fire refilterJavaForSpringBoot and recurse.
        if (springBootListener != null) {
            springBootVersionCombo.removeItemListener(springBootListener);
        }
        try {
            replaceComboModel(springBootVersionCombo, springBoot, currentBoot);
        } finally {
            if (springBootListener != null) {
                springBootVersionCombo.addItemListener(springBootListener);
            }
        }
    }

    /** Reloads the Java combo to match the current Spring Boot version selection. */
    private void refilterJavaForSpringBoot() {
        if (versionService == null) return;
        String currentBoot = comboText(springBootVersionCombo);
        String currentJava = comboText(javaVersionCombo);
        java.util.List<String> java = versionService.getJavaVersionsFor(currentBoot);
        // Detach the Java listener so the programmatic setSelectedItem inside
        // replaceComboModel doesn't fire refilterSpringBootForJava and recurse.
        if (javaListener != null) {
            javaVersionCombo.removeItemListener(javaListener);
        }
        try {
            replaceComboModel(javaVersionCombo, java, currentJava);
        } finally {
            if (javaListener != null) {
                javaVersionCombo.addItemListener(javaListener);
            }
        }
    }

    /** Replaces a combo's model while preserving the current value (even if user-typed). */
    private static void replaceComboModel(ComboBox<String> combo, java.util.List<String> values, String preserve) {
        combo.setModel(new javax.swing.DefaultComboBoxModel<>(values.toArray(new String[0])));
        if (preserve != null && !preserve.isEmpty()) {
            combo.setSelectedItem(preserve);
        }
    }

    private void installFilterListeners() {
        if (filteringInstalled) return;
        filteringInstalled = true;
        javaListener = e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                refilterSpringBootForJava();
                refilterSb2DependentCombos();
            }
        };
        springBootListener = e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                refilterJavaForSpringBoot();
                refilterSb2DependentCombos();
            }
        };
        javaVersionCombo.addItemListener(javaListener);
        springBootVersionCombo.addItemListener(springBootListener);
        // Initial pass — current Java/SB selections might already be SB2.
        refilterSb2DependentCombos();
    }

    /**
     * Spring Boot 2.x can't use {@code dto.style: record} (Java 14+ feature) or
     * {@code exception.style: problemdetail} (Spring 6+ feature). When SB2 is
     * active, narrow those combos to the legal options and force-pick the only
     * remaining value.
     */
    private void refilterSb2DependentCombos() {
        boolean sb2 = isSpringBoot2Selected();
        if (sb2) {
            replaceComboModel(dtoStyleCombo, java.util.List.of("class"), "class");
            replaceComboModel(exceptionStyleCombo, java.util.List.of("envelope"), "envelope");
        } else {
            String currentDto = comboText(dtoStyleCombo);
            replaceComboModel(dtoStyleCombo, java.util.List.of("class", "record"),
                    currentDto.isEmpty() ? "class" : currentDto);
            String currentEx = comboText(exceptionStyleCombo);
            replaceComboModel(exceptionStyleCombo, java.util.List.of("problemdetail", "envelope"),
                    currentEx.isEmpty() ? "problemdetail" : currentEx);
        }
    }

    private boolean isSpringBoot2Selected() {
        String v = comboText(springBootVersionCombo);
        return v.startsWith("2.");
    }

    /** Tiny adapter so we can wire any text-field document change to a Runnable. */
    private static final class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable onChange;
        SimpleDocListener(Runnable onChange) { this.onChange = onChange; }
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
    }
}
