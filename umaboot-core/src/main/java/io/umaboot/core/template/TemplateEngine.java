package io.umaboot.core.template;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Thin wrapper around FreeMarker {@link Configuration}.
 *
 * <p>Templates are loaded first from a user-provided directory (if supplied) and
 * fall back to classpath defaults at {@code /templates/}. This matches the
 * "classpath defaults; user-provided directory overrides per template ID"
 * contract from the spec.</p>
 */
public final class TemplateEngine {

    private final Configuration cfg;

    public TemplateEngine(Path userTemplateDir) {
        this.cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
        cfg.setNumberFormat("computer"); // 1234 not 1,234
        cfg.setBooleanFormat("c"); // true / false (not "yes"/"no")
        cfg.setWhitespaceStripping(true);
        // Disable the legacy `#{...}` numeric-interpolation syntax so MyBatis
        // SQL parameter syntax `#{id}` survives template rendering as plain text.
        cfg.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);

        try {
            TemplateLoader classpathLoader = new ClassTemplateLoader(getClass(), "/templates");
            if (userTemplateDir != null && Files.isDirectory(userTemplateDir)) {
                FileTemplateLoader fileLoader = new FileTemplateLoader(userTemplateDir.toFile());
                cfg.setTemplateLoader(new MultiTemplateLoader(
                        new TemplateLoader[]{fileLoader, classpathLoader}));
            } else {
                cfg.setTemplateLoader(classpathLoader);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to configure FreeMarker template loader", e);
        }
    }

    /** Render the template at {@code templatePath} with {@code model}. */
    public String render(String templatePath, Map<String, Object> model) {
        Objects.requireNonNull(templatePath, "templatePath");
        Objects.requireNonNull(model, "model");
        try {
            Template tpl = cfg.getTemplate(templatePath);
            StringWriter out = new StringWriter();
            tpl.process(model, out);
            return out.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load template: " + templatePath, e);
        } catch (freemarker.template.TemplateException e) {
            throw new IllegalStateException(
                    "Failed to render template " + templatePath + ": " + e.getMessage(), e);
        }
    }
}
