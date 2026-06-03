package io.umaboot.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.umaboot.intellij.settings.UiText;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Project-local log storage for Umaboot actions.
 *
 * <p>Summary is intentionally short and status-oriented. Detail keeps the
 * operation inputs, parser warnings, generated paths, and exception stack
 * traces that are useful when diagnosing generated-project failures.</p>
 */
public final class UmabootLog {

    private static final Key<UmabootLog> KEY = Key.create("io.umaboot.intellij.log");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String TOOL_WINDOW_ID = "Umaboot";
    private static final String SUMMARY_TAB = "Summary Log";
    private static final String DETAIL_TAB = "Detail Log";

    private final Project project;
    private final StringBuilder summary = new StringBuilder();
    private final StringBuilder detail = new StringBuilder();
    private JBTextArea summaryArea;
    private JBTextArea detailArea;

    private UmabootLog(Project project) {
        this.project = project;
    }

    public static UmabootLog get(Project project) {
        UmabootLog existing = project.getUserData(KEY);
        if (existing != null) return existing;
        UmabootLog created = new UmabootLog(project);
        project.putUserData(KEY, created);
        return created;
    }

    public void started(String scope, String detailLine) {
        appendSummary(scope + ": started");
        appendDetail("[" + scope + "] started");
        if (detailLine != null && !detailLine.isBlank()) {
            appendDetail("[" + scope + "] " + detailLine);
        }
    }

    public void finished(String scope, String message) {
        appendSummary(scope + ": " + message);
        appendDetail("[" + scope + "] " + message);
    }

    public void event(String scope, String message) {
        appendSummary(scope + ": " + message);
        appendDetail("[" + scope + "] " + message);
    }

    public void detail(String scope, String message) {
        appendDetail("[" + scope + "] " + message);
    }

    public void parserWarnings(String scope, List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return;
        appendSummary(scope + ": " + warnings.size() + " parser warning(s)");
        appendDetail("[" + scope + "] parser warnings:");
        for (String warning : warnings) {
            appendDetail("  - " + warning);
        }
    }

    public void failed(String scope, Throwable throwable) {
        appendSummary(scope + ": failed - " + rootMessage(throwable));
        appendDetail("[" + scope + "] failed");
        appendDetail(stackTrace(throwable));
    }

    public void showSummary() {
        show(SUMMARY_TAB);
    }

    public void showDetail() {
        show(DETAIL_TAB);
    }

    private void show(String tabName) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
            if (toolWindow == null) {
                Messages.showInfoMessage(project, snapshot(tabName), tabName);
                return;
            }
            Content content = ensureContent(toolWindow, tabName);
            toolWindow.getContentManager().setSelectedContent(content);
            toolWindow.show(null);
        });
    }

    private Content ensureContent(ToolWindow toolWindow, String tabName) {
        for (Content content : toolWindow.getContentManager().getContents()) {
            if (tabName.equals(content.getDisplayName())) return content;
        }
        JTextArea area = SUMMARY_TAB.equals(tabName) ? summaryArea() : detailArea();
        Content content = ContentFactory.getInstance().createContent(
                buildLogPanel(area, tabName),
                tabName,
                false);
        toolWindow.getContentManager().addContent(content);
        return content;
    }

    private JBPanel<JBPanel<?>> buildLogPanel(JTextArea area, String tabName) {
        JBPanel<JBPanel<?>> root = new JBPanel<>(new BorderLayout());
        root.add(new JBScrollPane(area), BorderLayout.CENTER);

        UiText.Language language = UiText.load(project);
        JBPanel<JBPanel<?>> bar = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(0xC0C0C0)));

        JButton copy = new JButton(UiText.text(language, "Copy"));
        copy.addActionListener(e -> CopyPasteManager.getInstance()
                .setContents(new StringSelection(snapshot(tabName))));
        JButton clear = new JButton(UiText.text(language, "Clear"));
        clear.addActionListener(e -> clear(tabName));

        bar.add(copy);
        bar.add(clear);
        root.add(bar, BorderLayout.SOUTH);
        return root;
    }

    private synchronized void clear(String tabName) {
        if (SUMMARY_TAB.equals(tabName)) {
            summary.setLength(0);
            setText(summaryArea, "");
        } else {
            detail.setLength(0);
            setText(detailArea, "");
        }
    }

    private synchronized String snapshot(String tabName) {
        String value = SUMMARY_TAB.equals(tabName) ? summary.toString() : detail.toString();
        return value.isBlank() ? "No log entries yet." : value;
    }

    private synchronized void appendSummary(String message) {
        String line = timestamp() + " " + message + System.lineSeparator();
        summary.append(line);
        appendText(summaryArea, line);
    }

    private synchronized void appendDetail(String message) {
        String line = timestamp() + " " + message + System.lineSeparator();
        detail.append(line);
        appendText(detailArea, line);
    }

    private JBTextArea summaryArea() {
        if (summaryArea == null) {
            summaryArea = createArea(summary.toString());
        }
        return summaryArea;
    }

    private JBTextArea detailArea() {
        if (detailArea == null) {
            detailArea = createArea(detail.toString());
        }
        return detailArea;
    }

    private static JBTextArea createArea(String text) {
        JBTextArea area = new JBTextArea(text);
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return area;
    }

    private static void appendText(JTextArea area, String line) {
        if (area == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            area.append(line);
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    private static void setText(JTextArea area, String text) {
        if (area == null) return;
        ApplicationManager.getApplication().invokeLater(() -> area.setText(text));
    }

    static String rootMessage(Throwable throwable) {
        if (throwable == null) return "unknown error";
        Throwable cursor = throwable;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return message == null || message.isBlank()
                ? cursor.getClass().getSimpleName()
                : message;
    }

    private static String stackTrace(Throwable throwable) {
        if (throwable == null) return "No exception details.";
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString().trim();
    }

    private static String timestamp() {
        return LocalDateTime.now().format(CLOCK);
    }
}
