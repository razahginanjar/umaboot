package io.umaboot.core.merge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectedRegionMergerTest {

    @Test
    void preservesUserRegion() {
        String existing = """
                package com.example;
                public class A {
                    // <Umaboot:protected name="customMethods">
                    public void hello() { System.out.println("user code"); }
                    // </Umaboot:protected>
                }
                """;
        String generated = """
                package com.example;
                public class A {
                    // <Umaboot:protected name="customMethods">
                    // </Umaboot:protected>
                }
                """;
        var merger = new ProtectedRegionMerger();
        ProtectedRegionMerger.MergeResult mr = merger.merge(existing, generated, true);

        assertThat(mr.substituted()).isEqualTo(1);
        assertThat(mr.conflict()).isFalse();
        assertThat(mr.content()).contains("System.out.println(\"user code\")");
    }

    @Test
    void noProtectedRegions_returnsGeneratedUnchanged() {
        String existing = "public class A {}\n";
        String generated = "public class A {}\n// new content\n";
        var merger = new ProtectedRegionMerger();
        ProtectedRegionMerger.MergeResult mr = merger.merge(existing, generated, true);
        assertThat(mr.content()).isEqualTo(generated);
        assertThat(mr.substituted()).isZero();
    }

    @Test
    void nonJavaSkipsParseValidation() {
        String existing = "before\n// <Umaboot:protected name=\"x\">\nuser-yaml: true\n// </Umaboot:protected>\nafter\n";
        String generated = "before\n// <Umaboot:protected name=\"x\">\n// </Umaboot:protected>\nafter\n";
        var merger = new ProtectedRegionMerger();
        ProtectedRegionMerger.MergeResult mr = merger.merge(existing, generated, false);
        assertThat(mr.content()).contains("user-yaml: true");
        assertThat(mr.conflict()).isFalse();
    }
}
