package br.org.gam.api.shared.specification;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@FunctionalTest
@DisplayName("Functional - Unicode search whitespace")
class UnicodeWhitespaceTest {

    private static final int[] WHITE_SPACE_CODE_POINTS = {
            0x0009, 0x000A, 0x000B, 0x000C, 0x000D,
            0x0020, 0x0085, 0x00A0, 0x1680,
            0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005,
            0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
            0x2028, 0x2029, 0x202F, 0x205F, 0x3000
    };

    @ParameterizedTest(name = "{0}")
    @MethodSource("whiteSpaceCodePoints")
    @DisplayName("REQ-SEARCH-002/005/007 - every Unicode White_Space code point -> blank, trimmed, and collapsed")
    void everyUnicodeWhiteSpaceCodePointShouldHaveSharedSearchSemantics(
            String scenario,
            String whitespace
    ) {
        assertThat(UnicodeWhitespace.isBlank(whitespace)).as(scenario).isTrue();
        assertThat(UnicodeWhitespace.trim(whitespace + "value" + whitespace))
                .as(scenario)
                .isEqualTo("value");
        assertThat(UnicodeWhitespace.collapse("left" + whitespace + whitespace + "right"))
                .as(scenario)
                .isEqualTo("left right");
    }

    @Test
    @DisplayName("REQ-SEARCH-002/005/007 - empty and mixed Unicode whitespace -> empty or one separator")
    void emptyAndMixedWhitespaceShouldRespectLoopBoundaries() {
        String allWhitespace = IntStream.of(WHITE_SPACE_CODE_POINTS)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        assertThat(UnicodeWhitespace.isBlank("")).isTrue();
        assertThat(UnicodeWhitespace.trim("")).isEmpty();
        assertThat(UnicodeWhitespace.collapse("")).isEmpty();
        assertThat(UnicodeWhitespace.isBlank(allWhitespace)).isTrue();
        assertThat(UnicodeWhitespace.trim(allWhitespace)).isEmpty();
        assertThat(UnicodeWhitespace.collapse("left" + allWhitespace + "right"))
                .isEqualTo("left right");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonWhiteSpaceContent")
    @DisplayName("REQ-SEARCH-005/007 - format, content, and supplementary code points -> preserved")
    void nonWhiteSpaceCodePointsShouldNeverBeTrimmedOrCollapsed(String scenario, String content) {
        String boundaryValue = content + "value" + content;

        assertThat(UnicodeWhitespace.isBlank(content)).as(scenario).isFalse();
        assertThat(UnicodeWhitespace.trim(boundaryValue)).as(scenario).isEqualTo(boundaryValue);
        assertThat(UnicodeWhitespace.collapse("left" + content + "right"))
                .as(scenario)
                .isEqualTo("left" + content + "right");
    }

    @Test
    @DisplayName("REQ-SEARCH-005/007 - supplementary content between whitespace boundaries -> preserved atomically")
    void supplementaryCodePointShouldBePreservedAcrossTrimAndCollapse() {
        String supplementary = new String(Character.toChars(0x1F642));
        String boundaries = "\u00A0\u202F";

        assertThat(UnicodeWhitespace.trim(boundaries + supplementary + boundaries))
                .isEqualTo(supplementary);
        assertThat(UnicodeWhitespace.collapse(supplementary + boundaries + supplementary))
                .isEqualTo(supplementary + " " + supplementary);
    }

    private static Stream<Arguments> whiteSpaceCodePoints() {
        return IntStream.of(WHITE_SPACE_CODE_POINTS)
                .mapToObj(codePoint -> Arguments.of(
                        "U+" + String.format("%04X", codePoint),
                        new String(Character.toChars(codePoint))
                ));
    }

    private static Stream<Arguments> nonWhiteSpaceContent() {
        return Stream.of(
                Arguments.of("BACKSPACE U+0008", "\u0008"),
                Arguments.of("SHIFT OUT U+000E", "\u000E"),
                Arguments.of("UNIT SEPARATOR U+001F", "\u001F"),
                Arguments.of("NEXT-LINE neighbor U+0084", "\u0084"),
                Arguments.of("NEXT-LINE neighbor U+0086", "\u0086"),
                Arguments.of("MONGOLIAN VOWEL SEPARATOR U+180E", "\u180E"),
                Arguments.of("ZERO WIDTH SPACE U+200B", "\u200B"),
                Arguments.of("LEFT-TO-RIGHT EMBEDDING U+202A", "\u202A"),
                Arguments.of("WORD JOINER U+2060", "\u2060"),
                Arguments.of("ZERO WIDTH NO-BREAK SPACE U+FEFF", "\uFEFF"),
                Arguments.of("IDEOGRAPHIC COMMA U+3001", "\u3001"),
                Arguments.of("supplementary emoji U+1F642", new String(Character.toChars(0x1F642)))
        );
    }
}
