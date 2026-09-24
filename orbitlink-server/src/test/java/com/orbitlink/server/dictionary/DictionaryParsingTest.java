package com.orbitlink.server.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orbitlink.server.dictionary.yaml.DictionaryFile;
import com.orbitlink.server.dictionary.yaml.ParameterDefinition;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Parsing tests. No Spring context and no database, so these run in the fast
 * Surefire phase and need no Docker — the parser is pure logic over a file.
 */
class DictionaryParsingTest {

    private final DictionaryLoader loader = new DictionaryLoader(null);

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesTheShippedSampleDictionary() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/dictionary/sample-dictionary.yaml")) {
            assertThat(in).as("sample dictionary is on the classpath").isNotNull();

            DictionaryFile file = loader.parse(in);

            assertThat(file.version()).isEqualTo("1.0.0");
            assertThat(file.parameters()).hasSize(10);
            assertThat(file.parameters())
                    .extracting(ParameterDefinition::apid)
                    .containsOnly(100, 101);
        }
    }

    @Test
    void appliesCalibrationDefaultsWhenTheFileOmitsThem() throws IOException {
        DictionaryFile file = loader.parse(yaml("""
                version: "9.9.9"
                parameters:
                  - mnemonic: FLAG
                    name: Some flag
                    apid: 1
                    dataType: BOOLEAN
                    bitOffset: 0
                    bitLength: 1
                """));

        ParameterDefinition parameter = file.parameters().get(0);
        assertThat(parameter.calScaleOrDefault()).isEqualTo(1.0);
        assertThat(parameter.calOffsetOrDefault()).isEqualTo(0.0);
        assertThat(parameter.enumStatesOrEmpty()).isEmpty();
    }

    /**
     * A zero limit must survive as a real bound. If the DTO used primitives,
     * an omitted minValue and an explicit 0.0 would be indistinguishable.
     */
    @Test
    void distinguishesAnExplicitZeroLimitFromAnAbsentOne() throws IOException {
        DictionaryFile bounded = loader.parse(yaml("""
                version: "1"
                parameters:
                  - mnemonic: P
                    name: P
                    apid: 1
                    dataType: UNSIGNED_INT
                    bitOffset: 0
                    bitLength: 8
                    minValue: 0.0
                """));
        DictionaryFile unbounded = loader.parse(yaml("""
                version: "1"
                parameters:
                  - mnemonic: P
                    name: P
                    apid: 1
                    dataType: UNSIGNED_INT
                    bitOffset: 0
                    bitLength: 8
                """));

        assertThat(bounded.parameters().get(0).minValue()).isEqualTo(0.0);
        assertThat(unbounded.parameters().get(0).minValue()).isNull();
    }

    @Test
    void parsesEnumStates() throws IOException {
        DictionaryFile file = loader.parse(yaml("""
                version: "1"
                parameters:
                  - mnemonic: MODE
                    name: Mode
                    apid: 5
                    dataType: ENUM
                    bitOffset: 0
                    bitLength: 8
                    enumStates:
                      - rawValue: 0
                        label: NOMINAL
                      - rawValue: 2
                        label: SAFE_MODE
                """));

        assertThat(file.parameters().get(0).enumStatesOrEmpty())
                .extracting("rawValue", "label")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0L, "NOMINAL"),
                        org.assertj.core.groups.Tuple.tuple(2L, "SAFE_MODE"));
    }

    /** A misspelled key must fail loudly rather than be silently dropped. */
    @Test
    void rejectsUnknownFields() {
        assertThatThrownBy(() -> loader.parse(yaml("""
                version: "1"
                parameters:
                  - mnemonic: P
                    name: P
                    apid: 1
                    dataType: UNSIGNED_INT
                    bitOffset: 0
                    bitLength: 8
                    untis: V
                """)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("untis");
    }

    @Test
    void rejectsAFileWithNoVersion() {
        assertThatThrownBy(() -> loader.parse(yaml("""
                parameters:
                  - mnemonic: P
                    name: P
                    apid: 1
                    dataType: UNSIGNED_INT
                    bitOffset: 0
                    bitLength: 8
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejectsAFileWithNoParameters() {
        assertThatThrownBy(() -> loader.parse(yaml("version: \"1\"\nparameters: []\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one parameter");
    }
}
