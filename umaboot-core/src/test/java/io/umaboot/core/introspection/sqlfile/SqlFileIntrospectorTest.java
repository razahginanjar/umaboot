package io.umaboot.core.introspection.sqlfile;

import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.fixtures.FixtureLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.SQLException;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-parser tests for {@link SqlFileIntrospector}. No live database, no
 * Testcontainers — feeds the per-condition fixtures from
 * {@code umaboot-test-fixtures} directly to the parser and asserts that the
 * resulting {@link SchemaModel} carries the right tables, columns, types,
 * primary keys, foreign keys, enums and comments.
 *
 * <p>The dialect-equivalence proof (parse-via-file == introspect-via-JDBC) is
 * the sibling {@code SqlFileVsJdbcRoundTripTest}, which spins up Testcontainers.</p>
 */
@Execution(ExecutionMode.CONCURRENT)
class SqlFileIntrospectorTest {

    // ============================================================ Postgres

    @Test
    void postgres_basicCrud_columnsAndPk() throws SQLException {
        SchemaModel schema = parse(FixtureLoader.POSTGRES_BASIC_CRUD, "postgresql");

        TableModel users = schema.findTable("users");
        assertThat(users).isNotNull();
        assertThat(users.columns()).extracting(ColumnModel::name)
                .containsExactly("id", "email", "full_name", "age", "is_active",
                        "signup_date", "created_at");
        assertThat(users.primaryKey()).containsExactly("id");

        ColumnModel id = users.findColumn("id");
        assertThat(id).isNotNull();
        // BIGSERIAL → autoIncrement true, JDBC BIGINT
        assertThat(id.autoIncrement()).isTrue();
        assertThat(id.jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(id.primaryKey()).isTrue();

        ColumnModel email = users.findColumn("email");
        assertThat(email).isNotNull();
        assertThat(email.jdbcType()).isEqualTo(Types.VARCHAR);
        assertThat(email.size()).isEqualTo(255);
        assertThat(email.nullable()).isFalse();
    }

    @Test
    void postgres_relationships_inlineAndOutOfLineFks() throws SQLException {
        SchemaModel schema = parse(FixtureLoader.POSTGRES_RELATIONSHIPS, "postgresql");

        // self-reference
        TableModel customers = schema.findTable("customers");
        assertThat(customers).isNotNull();
        assertThat(customers.relationships()).anyMatch(r ->
                r.toTable().equalsIgnoreCase("customers")
                        && r.fromColumns().contains("parent_id"));

        // 1:N customer -> orders
        TableModel orders = schema.findTable("orders");
        assertThat(orders).isNotNull();
        assertThat(orders.relationships()).anyMatch(r ->
                r.toTable().equalsIgnoreCase("customers")
                        && r.fromColumns().contains("customer_id"));

        // M:N junction
        TableModel productTags = schema.findTable("product_tags");
        assertThat(productTags).isNotNull();
        assertThat(productTags.primaryKey())
                .containsExactlyInAnyOrder("product_id", "tag_id");
    }

    @Test
    void postgres_enums_resolvedFromCreateType() throws SQLException {
        SchemaModel schema = parse(FixtureLoader.POSTGRES_ENUM_TYPES, "postgresql");

        TableModel shipments = schema.findTable("shipments");
        assertThat(shipments).isNotNull();
        ColumnModel status = shipments.findColumn("status");
        assertThat(status).isNotNull();
        assertThat(status.isEnum()).isTrue();
        assertThat(status.enumValues())
                .containsExactly("PENDING", "IN_TRANSIT", "DELIVERED", "RETURNED");

        ColumnModel carrier = shipments.findColumn("carrier");
        assertThat(carrier.enumValues())
                .containsExactly("UPS", "FEDEX", "DHL", "USPS");
    }

    @Test
    void postgres_comments_appliedFromCommentOn() throws SQLException {
        SchemaModel schema = parse(FixtureLoader.POSTGRES_COMMENTS, "postgresql");

        TableModel books = schema.findTable("books");
        assertThat(books).isNotNull();
        assertThat(books.comment()).isEqualTo("Books available in the catalog");

        ColumnModel title = books.findColumn("title");
        assertThat(title.comment()).isEqualTo("Public title displayed on listings");

        ColumnModel id = books.findColumn("id");
        assertThat(id.comment()).isEqualTo("Surrogate primary key");
    }

    @Test
    void postgres_compositePk() throws SQLException {
        SchemaModel schema = parse(FixtureLoader.POSTGRES_COMPOSITE_PK, "postgresql");
        // Pick whichever table the fixture happens to expose with a composite PK.
        boolean foundComposite = schema.tables().stream()
                .anyMatch(t -> t.primaryKey().size() >= 2);
        assertThat(foundComposite).isTrue();
    }

    // ============================================================ MySQL

    @Test
    void mysql_basicCrud() throws SQLException {
        SchemaModel schema = parse(FixtureLoader.MYSQL_BASIC_CRUD, "mysql");

        TableModel users = schema.findTable("users");
        assertThat(users).isNotNull();
        assertThat(users.primaryKey()).containsExactly("id");

        ColumnModel id = users.findColumn("id");
        assertThat(id.autoIncrement()).isTrue();   // AUTO_INCREMENT in MySQL
        assertThat(id.jdbcType()).isEqualTo(Types.BIGINT);
    }

    @Test
    void mysql_inlineEnum() throws SQLException {
        SchemaModel schema = parse(FixtureLoader.MYSQL_ENUM_TYPES, "mysql");

        TableModel shipments = schema.findTable("shipments");
        assertThat(shipments).isNotNull();
        ColumnModel status = shipments.findColumn("status");
        assertThat(status.isEnum()).isTrue();
        assertThat(status.enumValues())
                .containsExactly("PENDING", "IN_TRANSIT", "DELIVERED", "RETURNED");
    }

    @Test
    void mysql_inlineColumnComment() throws SQLException {
        SchemaModel schema = parse(FixtureLoader.MYSQL_COMMENTS, "mysql");

        TableModel books = schema.findTable("books");
        assertThat(books).isNotNull();
        ColumnModel title = books.findColumn("title");
        assertThat(title.comment()).isEqualTo("Public title displayed on listings");
    }

    @Test
    void mysql_relationships_explicitForeignKeyClause() throws SQLException {
        SchemaModel schema = parse(FixtureLoader.MYSQL_RELATIONSHIPS, "mysql");

        TableModel orders = schema.findTable("orders");
        assertThat(orders).isNotNull();
        assertThat(orders.relationships()).anyMatch(r ->
                r.toTable().equalsIgnoreCase("customers")
                        && r.fromColumns().contains("customer_id"));
    }

    // ============================================================ unsupported syntax tolerance

    @Test
    void unsupportedSyntax_skippedWithWarning_doesNotFail() throws SQLException {
        // Mixes a CREATE TABLE we DO understand with a CREATE TRIGGER we do NOT.
        // We expect the table to come through and the trigger to be silently skipped.
        String sql = """
                CREATE TABLE plain (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(50) NOT NULL
                );
                
                CREATE TRIGGER never_supported
                BEFORE INSERT ON plain
                FOR EACH ROW
                BEGIN
                    SET NEW.name = UPPER(NEW.name);
                END;
                """;

        SchemaModel schema = new SqlFileIntrospector(sql, "mysql").introspect("public");
        assertThat(schema.tables()).hasSize(1);
        assertThat(schema.findTable("plain")).isNotNull();
    }

    @Test
    void emptyFile_returnsEmptySchema() throws SQLException {
        SchemaModel schema = new SqlFileIntrospector("", "postgresql").introspect("public");
        assertThat(schema.tables()).isEmpty();
        assertThat(schema.schemaName()).isEqualTo("public");
    }

    // ============================================================ helpers

    private static SchemaModel parse(String fixturePath, String dialect) throws SQLException {
        String sql = FixtureLoader.load(fixturePath);
        return new SqlFileIntrospector(sql, dialect).introspect("public");
    }
}
