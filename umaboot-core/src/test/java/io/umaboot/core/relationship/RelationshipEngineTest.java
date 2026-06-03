package io.umaboot.core.relationship;

import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.RelationshipModel;
import io.umaboot.core.model.RelationshipType;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationshipEngineTest {

    @Test
    void detectsManyToManyAndJunction() {
        ColumnModel productId = pkColumn("product_id", Types.BIGINT);
        ColumnModel tagId = pkColumn("tag_id", Types.BIGINT);

        TableModel products = simpleTable("products");
        TableModel tags = simpleTable("tags");

        RelationshipModel toProducts = new RelationshipModel(
                "product_tags", "products", new RelationshipType.ManyToOne(),
                List.of("product_id"), List.of("id"), "product", true, false);
        RelationshipModel toTags = new RelationshipModel(
                "product_tags", "tags", new RelationshipType.ManyToOne(),
                List.of("tag_id"), List.of("id"), "tag", true, false);

        TableModel productTags = new TableModel(
                "product_tags", "public", "",
                List.of(productId, tagId),
                List.of("product_id", "tag_id"),
                List.of(),
                List.of(toProducts, toTags),
                false);

        SchemaModel result = new RelationshipEngine().analyze(
                new SchemaModel("public", List.of(products, tags, productTags)));

        TableModel productsOut = result.findTable("products");
        TableModel tagsOut = result.findTable("tags");
        TableModel junctionOut = result.findTable("product_tags");

        assertThat(junctionOut.junction()).isTrue();
        assertThat(productsOut.relationships())
                .anyMatch(r -> r.type() instanceof RelationshipType.ManyToMany
                        && r.toTable().equals("tags"));
        assertThat(tagsOut.relationships())
                .anyMatch(r -> r.type() instanceof RelationshipType.ManyToMany
                        && r.toTable().equals("products"));
    }

    @Test
    void addsInverseOneToManyForManyToOne() {
        TableModel customers = simpleTable("customers");
        RelationshipModel toCustomer = new RelationshipModel(
                "orders", "customers", new RelationshipType.ManyToOne(),
                List.of("customer_id"), List.of("id"), "customer", true, false);
        TableModel orders = new TableModel(
                "orders", "public", "",
                List.of(pkColumn("id", Types.BIGINT),
                        new ColumnModel("customer_id", Types.BIGINT, "int8", 19, 0,
                                false, false, false, null, "", List.of())),
                List.of("id"), List.of(), List.of(toCustomer), false);

        SchemaModel result = new RelationshipEngine()
                .analyze(new SchemaModel("public", List.of(customers, orders)));

        TableModel customersOut = result.findTable("customers");
        assertThat(customersOut.relationships())
                .anyMatch(r -> r.type() instanceof RelationshipType.OneToMany
                        && r.toTable().equals("orders")
                        && !r.owning());
    }

    @Test
    void danglingRelationshipTargetFailsWithClearMessage() {
        RelationshipModel toMissingTags = new RelationshipModel(
                "product_tags", "tags", new RelationshipType.ManyToOne(),
                List.of("tag_id"), List.of("id"), "tag", true, false);
        TableModel productTags = new TableModel(
                "product_tags", "public", "",
                List.of(pkColumn("product_id", Types.BIGINT), pkColumn("tag_id", Types.BIGINT)),
                List.of("product_id", "tag_id"),
                List.of(),
                List.of(new RelationshipModel(
                        "product_tags", "products", new RelationshipType.ManyToOne(),
                        List.of("product_id"), List.of("id"), "product", true, false),
                        toMissingTags),
                false);

        assertThatThrownBy(() -> new RelationshipEngine().analyze(
                new SchemaModel("public", List.of(simpleTable("products"), productTags))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Relationship target table 'tags'")
                .hasMessageContaining("product_tags");
    }

    private static ColumnModel pkColumn(String name, int jdbcType) {
        return new ColumnModel(name, jdbcType, "int8", 19, 0,
                false, true, true, null, "", List.of());
    }

    private static TableModel simpleTable(String name) {
        return new TableModel(name, "public", "",
                List.of(pkColumn("id", Types.BIGINT)),
                List.of("id"),
                List.of(),
                List.of(),
                false);
    }
}
