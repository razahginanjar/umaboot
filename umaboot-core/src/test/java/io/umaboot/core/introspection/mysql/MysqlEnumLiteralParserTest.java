package io.umaboot.core.introspection.mysql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlEnumLiteralParserTest {

    @Test
    void parsesSimpleEnumLiteral() {
        assertThat(MysqlIntrospector.parseEnumLiteral("enum('A','B','C')"))
                .containsExactly("A", "B", "C");
    }

    @Test
    void handlesEscapedQuoteInValue() {
        assertThat(MysqlIntrospector.parseEnumLiteral("enum('it''s','ok')"))
                .containsExactly("it's", "ok");
    }

    @Test
    void handlesValueWithComma() {
        assertThat(MysqlIntrospector.parseEnumLiteral("enum('a,b','c')"))
                .containsExactly("a,b", "c");
    }

    @Test
    void returnsEmptyForMalformed() {
        assertThat(MysqlIntrospector.parseEnumLiteral("not-an-enum")).isEmpty();
    }
}
