package io.umaboot.core.generator.jpa;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.generator.JavaTypeMapper;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.RelationshipModel;
import io.umaboot.core.model.RelationshipType;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.Naming;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds a FreeMarker model map for a single {@link TableModel}.
 *
 * <p>This is the bridge between the schema model and templates. Templates stay
 * dumb and just iterate over precomputed lists like {@code fields},
 * {@code relationships}, {@code imports}.</p>
 *
 * <p><b>Schema-aware features (Phase G):</b></p>
 * <ul>
 *   <li><b>Audit fields</b> — when {@code audit.enabled} and the table has any of the
 *       configured audit columns ({@code created_at, updated_at, created_by, updated_by}),
 *       those columns are filtered out of {@code fields} (they live on the {@code Auditable}
 *       superclass) and the model exposes:
 *       <ul>
 *         <li>{@code auditable=true}</li>
 *         <li>{@code hasCreatedAt, hasUpdatedAt, hasCreatedBy, hasUpdatedBy} booleans</li>
 *       </ul>
 *   </li>
 *   <li><b>Soft delete</b> — when {@code softDelete.enabled} and the table has a
 *       {@code deleted_at}/{@code is_deleted}/{@code deleted}/{@code isDeleted} column
 *       (or the explicit {@code softDelete.column}), the column is kept on the entity
 *       (so JPA can write to it) but the model exposes:
 *       <ul>
 *         <li>{@code softDelete=true}</li>
 *         <li>{@code softDeleteColumn} (raw column name)</li>
 *         <li>{@code softDeleteIsTimestamp} (true) or {@code softDeleteIsBoolean} (true)</li>
 *       </ul>
 *   </li>
 * </ul>
 */
public final class EntityView {

    private EntityView() {}

    /** Soft-delete column name candidates, checked in order when {@code softDelete.column} is unset. */
    private static final List<String> SOFT_DELETE_CANDIDATES =
            List.of("deleted_at", "is_deleted", "deleted", "deletedat", "isdeleted");

    public static Map<String, Object> build(TableModel table, GeneratorContext ctx) {
        Map<String, Object> m = new LinkedHashMap<>();

        // Per-table override block (className, columns map). May be empty.
        UmabootConfig.TableOverride tableOverride = ctx.tableOverride(table.name())
                .orElseGet(UmabootConfig.TableOverride::empty);

        String entityName = Naming.entityClass(
                table.name(),
                ctx.classNameStripPrefix(),
                tableOverride.className());
        m.put("table", Map.of(
                "name", table.name(),
                "schema", table.schema(),
                "comment", table.comment()
        ));
        m.put("entityName", entityName);
        m.put("entityVar", lowerFirst(entityName));
        m.put("basePackage", ctx.basePackage());
        m.put("javaVersion", ctx.javaVersion());
        m.put("javaMajor", ctx.javaMajor());
        m.put("javaSupportsStringIsBlank", ctx.javaSupportsStringIsBlank());
        m.put("javaSupportsListOf", ctx.javaSupportsListOf());
        m.put("javaSupportsListCopyOf", ctx.javaSupportsListCopyOf());
        m.put("javaSupportsStreamToList", ctx.javaSupportsStreamToList());
        m.put("useLombok", ctx.useLombok());
        m.put("openApiAnnotation", ctx.isOpenApiAnnotation());
        m.put("eeNamespace", ctx.eeNamespace());
        m.put("springBoot2", ctx.isSpringBoot2());
        m.put("springBoot3", ctx.isSpringBoot3());
        // Phase E — injection flags (used by ServiceImpl, Controller, ApplicationService templates).
        m.put("injectionStyle", ctx.injectionStyle());
        m.put("injectConstructor", ctx.isInjectionConstructor());
        m.put("injectLombok", ctx.isInjectionLombok());
        m.put("injectAutowired", ctx.isInjectionAutowired());
        // Phase F — validation + DTO + exception flags (used by Controller, RequestDTO, ResponseDTO).
        m.put("validationStyle", ctx.validationStyle());
        m.put("validationJakarta", ctx.isValidationJakarta());
        m.put("validationNone", ctx.isValidationNone());
        m.put("validationService", ctx.isValidationService());
        m.put("dtoStyle", ctx.dtoStyle());
        m.put("dtoClass", ctx.isDtoClass());
        m.put("dtoRecord", ctx.isDtoRecord());
        m.put("dtoShape", ctx.dtoShape());
        m.put("dtoSeparate", ctx.isDtoSeparate());
        m.put("dtoSingle", ctx.isDtoSingle());
        m.put("exceptionStyle", ctx.exceptionStyle());
        m.put("exceptionEnvelope", ctx.isExceptionEnvelope());
        m.put("exceptionProblemDetail", ctx.isExceptionProblemDetail());

        // ---------- Audit + soft-delete detection ----------
        UmabootConfig.AuditOptions auditOpts = ctx.audit();
        UmabootConfig.SoftDeleteOptions softDeleteOpts = ctx.softDelete();

        Set<String> auditColumnNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean hasCreatedAt = false, hasUpdatedAt = false, hasCreatedBy = false, hasUpdatedBy = false;
        if (auditOpts.enabled()) {
            for (ColumnModel c : table.columns()) {
                if (c.name().equalsIgnoreCase(auditOpts.createdAt())) {
                    auditColumnNames.add(c.name()); hasCreatedAt = true;
                } else if (c.name().equalsIgnoreCase(auditOpts.updatedAt())) {
                    auditColumnNames.add(c.name()); hasUpdatedAt = true;
                } else if (c.name().equalsIgnoreCase(auditOpts.createdBy())) {
                    auditColumnNames.add(c.name()); hasCreatedBy = true;
                } else if (c.name().equalsIgnoreCase(auditOpts.updatedBy())) {
                    auditColumnNames.add(c.name()); hasUpdatedBy = true;
                }
            }
        }
        boolean auditable = !auditColumnNames.isEmpty();

        ColumnModel softDeleteColumn = null;
        if (softDeleteOpts.enabled()) {
            String configured = softDeleteOpts.column();
            for (ColumnModel c : table.columns()) {
                if (configured != null) {
                    if (c.name().equalsIgnoreCase(configured)) {
                        softDeleteColumn = c;
                        break;
                    }
                } else {
                    String lower = c.name().toLowerCase(Locale.ROOT);
                    if (SOFT_DELETE_CANDIDATES.contains(lower)) {
                        softDeleteColumn = c;
                        break;
                    }
                }
            }
        }

        // Fields (only non-FK, non-audit columns; FK columns are represented by relationships)
        TreeSet<String> imports = new TreeSet<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        List<String> fkColumnNames = new ArrayList<>();
        for (RelationshipModel r : table.relationships()) {
            if (r.owning() && (r.type() instanceof RelationshipType.ManyToOne
                    || r.type() instanceof RelationshipType.OneToOne)) {
                fkColumnNames.addAll(r.fromColumns());
            }
        }

        String idType = "Long";
        String idField = "id";
        String idColumn = "id";

        for (ColumnModel c : table.columns()) {
            if (fkColumnNames.stream().anyMatch(f -> f.equalsIgnoreCase(c.name()))) {
                continue;
            }
            if (auditColumnNames.contains(c.name())) {
                continue; // hoisted into Auditable @MappedSuperclass
            }
            String columnOverrideType = java.util.Optional.ofNullable(tableOverride.columns().get(c.name()))
                    .map(UmabootConfig.ColumnOverride::javaType)
                    .filter(s -> !s.isBlank())
                    .orElse(null);
            String javaType = JavaTypeMapper.javaType(c, columnOverrideType);
            String simple = JavaTypeMapper.simpleName(javaType);
            String imp = JavaTypeMapper.importFor(javaType);
            if (!imp.isEmpty()) imports.add(imp);

            String fieldName = Naming.toCamelCase(c.name());

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("columnName", c.name());
            field.put("fieldName", fieldName);
            field.put("javaType", simple);
            field.put("nullable", c.nullable());
            field.put("size", c.size());
            field.put("primaryKey", c.primaryKey());
            field.put("autoIncrement", c.autoIncrement());
            field.put("comment", c.comment());
            fields.add(field);

            if (c.primaryKey() && table.hasSimplePrimaryKey()) {
                idType = simple;
                idField = fieldName;
                idColumn = c.name();
            }
        }

        m.put("fields", fields);
        m.put("idType", idType);
        m.put("idField", idField);
        m.put("idColumn", idColumn);
        m.put("hasSimplePk", table.hasSimplePrimaryKey());
        m.put("unknownIdLiteral", unknownIdLiteral(idType));

        // Phase J — effective per-table pagination. Cursor pagination requires a
        // simple PK; tables without one silently fall back to offset.
        boolean wantCursor = "cursor".equalsIgnoreCase(ctx.paginationStyle());
        boolean usingCursor = wantCursor && table.hasSimplePrimaryKey();
        m.put("paginationStyle", usingCursor ? "cursor" : "offset");
        m.put("paginationCursor", usingCursor);
        m.put("paginationOffset", !usingCursor);

        // Audit flags (always present, false when no audit columns or audit disabled)
        m.put("auditable", auditable);
        m.put("hasCreatedAt", hasCreatedAt);
        m.put("hasUpdatedAt", hasUpdatedAt);
        m.put("hasCreatedBy", hasCreatedBy);
        m.put("hasUpdatedBy", hasUpdatedBy);
        m.put("hasAuditUser", hasCreatedBy || hasUpdatedBy);

        // Soft-delete flags
        boolean softDelete = softDeleteColumn != null;
        m.put("softDelete", softDelete);
        m.put("softDeleteColumn", softDelete ? softDeleteColumn.name() : "");
        boolean softDeleteIsTimestamp = false;
        boolean softDeleteIsBoolean = false;
        if (softDelete) {
            String javaType = JavaTypeMapper.simpleName(JavaTypeMapper.javaType(softDeleteColumn));
            softDeleteIsBoolean = "Boolean".equals(javaType) || "boolean".equals(javaType);
            softDeleteIsTimestamp = !softDeleteIsBoolean; // anything else (Instant/LocalDateTime/Timestamp)
        }
        m.put("softDeleteIsTimestamp", softDeleteIsTimestamp);
        m.put("softDeleteIsBoolean", softDeleteIsBoolean);

        // Relationships
        List<Map<String, Object>> rels = new ArrayList<>();
        for (RelationshipModel r : table.relationships()) {
            String relatedEntity = Naming.entityClass(
                    r.toTable(),
                    ctx.classNameStripPrefix(),
                    ctx.tableOverride(r.toTable())
                            .map(UmabootConfig.TableOverride::className)
                            .filter(s -> !s.isBlank())
                            .orElse(null));
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("type", r.type().getClass().getSimpleName()); // e.g. "ManyToOne"
            rm.put("targetEntity", relatedEntity);
            rm.put("targetVar", lowerFirst(relatedEntity));
            rm.put("fieldName", Naming.toCamelCase(r.fieldName()));
            rm.put("owning", r.owning());
            rm.put("selfReference", r.selfReference());
            rm.put("fromColumns", r.fromColumns());
            rm.put("toColumns", r.toColumns());
            if (r.type() instanceof RelationshipType.ManyToMany mm) {
                rm.put("junctionTable", mm.junctionTable());
            }
            // Inverse field name on parent for mappedBy
            if (!r.owning() && (r.type() instanceof RelationshipType.OneToMany
                    || r.type() instanceof RelationshipType.OneToOne)) {
                rm.put("mappedBy", Naming.toCamelCase(r.toTable().toLowerCase()));
            }
            rels.add(rm);
        }
        m.put("relationships", rels);
        m.put("imports", imports);

        return m;
    }

    private static String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Returns a Java source-level literal for an "obviously not present" id,
     * used by the generated 404 smoke test. Matches the {@code idType}.
     */
    private static String unknownIdLiteral(String idType) {
        return switch (idType) {
            case "Long", "long" -> "999999999L";
            case "Integer", "int" -> "999999999";
            case "String" -> "\"umaboot-unknown-id\"";
            case "UUID" -> "java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000000\")";
            default -> "999999999L";
        };
    }

    /** True if any non-junction table in the schema has at least one audit column. */
    public static boolean anyTableHasAudit(io.umaboot.core.model.SchemaModel schema, GeneratorContext ctx) {
        if (!ctx.audit().enabled()) return false;
        for (TableModel t : schema.tables()) {
            if (t.junction()) continue;
            for (ColumnModel c : t.columns()) {
                if (c.name().equalsIgnoreCase(ctx.audit().createdAt())
                        || c.name().equalsIgnoreCase(ctx.audit().updatedAt())
                        || c.name().equalsIgnoreCase(ctx.audit().createdBy())
                        || c.name().equalsIgnoreCase(ctx.audit().updatedBy())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if any non-junction table has a {@code created_by}/{@code updated_by} column. */
    public static boolean anyTableHasAuditUser(io.umaboot.core.model.SchemaModel schema, GeneratorContext ctx) {
        if (!ctx.audit().enabled()) return false;
        for (TableModel t : schema.tables()) {
            if (t.junction()) continue;
            for (ColumnModel c : t.columns()) {
                if (c.name().equalsIgnoreCase(ctx.audit().createdBy())
                        || c.name().equalsIgnoreCase(ctx.audit().updatedBy())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if any of the audit columns ending in {@code _at} appear in any table. */
    public static boolean anyTableHasCreatedAt(io.umaboot.core.model.SchemaModel schema, GeneratorContext ctx) {
        if (!ctx.audit().enabled()) return false;
        for (TableModel t : schema.tables()) {
            if (t.junction()) continue;
            for (ColumnModel c : t.columns()) {
                if (c.name().equalsIgnoreCase(ctx.audit().createdAt())) return true;
            }
        }
        return false;
    }

    public static boolean anyTableHasUpdatedAt(io.umaboot.core.model.SchemaModel schema, GeneratorContext ctx) {
        if (!ctx.audit().enabled()) return false;
        for (TableModel t : schema.tables()) {
            if (t.junction()) continue;
            for (ColumnModel c : t.columns()) {
                if (c.name().equalsIgnoreCase(ctx.audit().updatedAt())) return true;
            }
        }
        return false;
    }

    public static boolean anyTableHasCreatedBy(io.umaboot.core.model.SchemaModel schema, GeneratorContext ctx) {
        if (!ctx.audit().enabled()) return false;
        for (TableModel t : schema.tables()) {
            if (t.junction()) continue;
            for (ColumnModel c : t.columns()) {
                if (c.name().equalsIgnoreCase(ctx.audit().createdBy())) return true;
            }
        }
        return false;
    }

    public static boolean anyTableHasUpdatedBy(io.umaboot.core.model.SchemaModel schema, GeneratorContext ctx) {
        if (!ctx.audit().enabled()) return false;
        for (TableModel t : schema.tables()) {
            if (t.junction()) continue;
            for (ColumnModel c : t.columns()) {
                if (c.name().equalsIgnoreCase(ctx.audit().updatedBy())) return true;
            }
        }
        return false;
    }
}
