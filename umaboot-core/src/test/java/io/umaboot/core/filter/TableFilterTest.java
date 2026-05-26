package io.umaboot.core.filter;

import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.RelationshipModel;
import io.umaboot.core.model.RelationshipType;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableFilterTest {

    @Test
    void emptyFilters_acceptEverything() {
        TableFilter f = TableFilter.allowAll();
        assertThat(f.accepts("customers")).isTrue();
        assertThat(f.accepts("audit_log")).isTrue();
    }

    @Test
    void include_onlyMatchingPass() {
        TableFilter f = new TableFilter(List.of("customer*", "orders"), List.of());
        assertThat(f.accepts("customers")).isTrue();
        assertThat(f.accepts("customer_addresses")).isTrue();
        assertThat(f.accepts("orders")).isTrue();
        assertThat(f.accepts("products")).isFalse();
    }

    @Test
    void exclude_dropsMatching() {
        TableFilter f = new TableFilter(List.of(), List.of("audit_*", "tmp_*"));
        assertThat(f.accepts("audit_log")).isFalse();
        assertThat(f.accepts("tmp_export")).isFalse();
        assertThat(f.accepts("customers")).isTrue();
    }

    @Test
    void caseInsensitive() {
        TableFilter f = new TableFilter(List.of("CUSTOMER*"), List.of());
        assertThat(f.accepts("customers")).isTrue();
        assertThat(f.accepts("CUSTOMERS")).isTrue();
    }

    @Test
    void questionMarkMatchesSingleChar() {
        TableFilter f = new TableFilter(List.of("user?s"), List.of());
        assertThat(f.accepts("userxs")).isTrue();   // ?=x
        assertThat(f.accepts("userss")).isTrue();   // ?=s (still matches)
        assertThat(f.accepts("users")).isFalse();   // too short — pattern needs 6 chars
        assertThat(f.accepts("userabs")).isFalse(); // too long — pattern needs exactly 6
    }

    @Test
    void apply_dropsTablesAndDanglingRelationships() {
        TableModel customers = simpleTable("customers");
        TableModel addresses = simpleTable("addresses");
        // customers -> addresses (1:N) — addresses references customer_id
        RelationshipModel toCustomer = new RelationshipModel(
                "addresses", "customers", new RelationshipType.ManyToOne(),
                List.of("customer_id"), List.of("id"), "customer", true, false);
        addresses = new TableModel(addresses.name(), addresses.schema(), addresses.comment(),
                addresses.columns(), addresses.primaryKey(), List.of(),
                List.of(toCustomer), false);
        // inverse on customers
        RelationshipModel toAddresses = new RelationshipModel(
                "customers", "addresses", new RelationshipType.OneToMany(),
                List.of("id"), List.of("customer_id"), "addresses", false, false);
        customers = new TableModel(customers.name(), customers.schema(), customers.comment(),
                customers.columns(), customers.primaryKey(), List.of(),
                List.of(toAddresses), false);

        SchemaModel schema = new SchemaModel("public", List.of(customers, addresses));
        SchemaModel result = new TableFilter(List.of(), List.of("addresses")).apply(schema);

        assertThat(result.tables()).hasSize(1).extracting("name").containsExactly("customers");
        // The OneToMany relationship to addresses must be pruned.
        assertThat(result.findTable("customers").relationships()).isEmpty();
    }

    @Test
    void apply_dropsJunctionWhenEndpointFiltered() {
        TableModel products = simpleTable("products");
        TableModel tags = simpleTable("tags");
        TableModel productTags = junction("product_tags", "products", "tags");

        SchemaModel schema = new SchemaModel("public", List.of(products, tags, productTags));
        SchemaModel result = new TableFilter(List.of(), List.of("tags")).apply(schema);

        assertThat(result.tables()).extracting("name")
                .containsExactly("products")
                .doesNotContain("tags", "product_tags");
    }

    private static ColumnModel pk(String name) {
        return new ColumnModel(name, Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
    }

    private static TableModel simpleTable(String name) {
        return new TableModel(name, "public", "",
                List.of(pk("id")), List.of("id"),
                List.of(), List.of(), false);
    }

    private static TableModel junction(String name, String left, String right) {
        RelationshipModel toLeft = new RelationshipModel(
                name, left, new RelationshipType.ManyToOne(),
                List.of(left + "_id"), List.of("id"), left, true, false);
        RelationshipModel toRight = new RelationshipModel(
                name, right, new RelationshipType.ManyToOne(),
                List.of(right + "_id"), List.of("id"), right, true, false);
        return new TableModel(name, "public", "",
                List.of(pk(left + "_id"), pk(right + "_id")),
                List.of(left + "_id", right + "_id"),
                List.of(),
                List.of(toLeft, toRight),
                true);
    }
}
