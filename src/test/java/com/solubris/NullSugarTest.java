package com.solubris;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NullSugarTest {
    @Nested
    class Coalesce {
        @Test
        void canReturnFirstValue() {
            String result = NullSugar.coalesce("first", "second", "third");

            assertThat(result).isEqualTo("first");
        }

        @Test
        void canReturnSecondValue() {
            String result = NullSugar.coalesce(null, "second", "third");

            assertThat(result).isEqualTo("second");
        }

        @Test
        void returnsNullIfAllNull() {
            String result = NullSugar.coalesce(null, null);

            assertThat(result).isNull();
        }
    }
}