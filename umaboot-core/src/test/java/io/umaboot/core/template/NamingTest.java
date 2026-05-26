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
}
