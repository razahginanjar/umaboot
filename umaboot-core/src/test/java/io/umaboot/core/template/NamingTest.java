package io.umaboot.core.template;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamingTest {

    @Test
    void snakeCaseToPascalCase() {
        assertThat(Naming.toPascalCase("customer_orders")).isEqualTo("CustomerOrders");
        assertThat(Naming.toPascalCase("user")).isEqualTo("User");
        assertThat(Naming.toPascalCase("order_items")).isEqualTo("OrderItems");
    }

    @Test
    void snakeCaseToCamelCase() {
        assertThat(Naming.toCamelCase("first_name")).isEqualTo("firstName");
        assertThat(Naming.toCamelCase("created_at")).isEqualTo("createdAt");
        assertThat(Naming.toCamelCase("id")).isEqualTo("id");
    }

    @Test
    void singularize() {
        assertThat(Naming.singularize("customers")).isEqualTo("customer");
        assertThat(Naming.singularize("addresses")).isEqualTo("address");
        assertThat(Naming.singularize("countries")).isEqualTo("country");
        assertThat(Naming.singularize("status")).isEqualTo("status"); // -ss is not plural
    }

    @Test
    void entityClassDerivation() {
        assertThat(Naming.entityClass("customers")).isEqualTo("Customer");
        assertThat(Naming.entityClass("order_items")).isEqualTo("OrderItem");
        assertThat(Naming.entityClass("addresses")).isEqualTo("Address");
    }

    // ---------------------------------------------------------------- prefix strip

    @Test
    void entityClass_withMatchingPrefix_stripsBeforePascalCase() {
        assertThat(Naming.entityClass("app_users", "app_")).isEqualTo("User");
        assertThat(Naming.entityClass("app_order_items", "app_")).isEqualTo("OrderItem");
        assertThat(Naming.entityClass("crm_customers", "crm_")).isEqualTo("Customer");
    }

    @Test
    void entityClass_withNonMatchingPrefix_leavesNameAlone() {
        // The "tables that don't match the prefix should not be cut" rule
        assertThat(Naming.entityClass("legacy_users", "app_")).isEqualTo("LegacyUser");
        assertThat(Naming.entityClass("flyway_schema_history", "app_")).isEqualTo("FlywaySchemaHistory");
    }

    @Test
    void entityClass_withNullOrEmptyPrefix_isSameAsNoPrefix() {
        assertThat(Naming.entityClass("app_users", null)).isEqualTo("AppUser");
        assertThat(Naming.entityClass("app_users", "")).isEqualTo("AppUser");
    }

    @Test
    void entityClass_prefixThatEqualsTableName_yieldsEmptyAfterStrip() {
        // Edge case — table is literally just the prefix. Don't crash; produce
        // something sensible (empty string round-trips through Pascal-case as "").
        assertThat(Naming.entityClass("app_", "app_")).isEqualTo("");
    }
}
