package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameMatcherTest {

  private final NameMatcher nameMatcher = new NameMatcher();

  @Test
  void isSameName_returnsFalseForNullValues() {
    assertThat(nameMatcher.isSameName(null, null)).isFalse();
    assertThat(nameMatcher.isSameName("A", null)).isFalse();
    assertThat(nameMatcher.isSameName(null, "A")).isFalse();
  }

  @Test
  void isSameName_returnsFalseForDifferentNames() {
    assertThat(nameMatcher.isSameName("A", "B")).isFalse();
    assertThat(nameMatcher.isSameName("A A", "AA")).isFalse();
  }

  @Test
  void isSameName_returnsTrueForExactMatch() {
    assertThat(nameMatcher.isSameName("A", "A")).isTrue();
  }

  @Test
  void isSameName_ignoresExtraWhitespace() {
    assertThat(nameMatcher.isSameName("A", " A   ")).isTrue();
  }

  @Test
  void isSameName_isOrderIndependent() {
    assertThat(nameMatcher.isSameName("AA BB CC", "BB CC AA")).isTrue();
    assertThat(nameMatcher.isSameName("KASK MARI", "MARI KASK")).isTrue();
  }

  @Test
  void isSameName_ignoresPunctuation() {
    assertThat(nameMatcher.isSameName("AA/BB,CC", "BB+CC-AA")).isTrue();
    assertThat(nameMatcher.isSameName("MARI-LIIS KASK", "MARI LIIS KASK")).isTrue();
  }

  @Test
  void isSameName_isCaseInsensitive() {
    assertThat(nameMatcher.isSameName("aaa bbb", "Aaa BBB")).isTrue();
  }

  @Test
  void isSameName_normalizesDiacritics() {
    assertThat(nameMatcher.isSameName("Jüri Õun", "JURI OUN")).isTrue();
    assertThat(nameMatcher.isSameName("PÄRT ÕLEKÕRS", "Part Olekors")).isTrue();
  }

  @Test
  void isSameName_stripsFieDesignation() {
    assertThat(nameMatcher.isSameName("FIE PÄRT ÕLEKÕRS", "PÄRT ÕLEKÕRS")).isTrue();
    assertThat(nameMatcher.isSameName("PÄRT ÕLEKÕRS FIE", "PÄRT ÕLEKÕRS")).isTrue();
    assertThat(nameMatcher.isSameName("FIE MARI KASK", "KASK MARI")).isTrue();
    assertThat(nameMatcher.isSameName("fie Jüri Õun", "JURI OUN")).isTrue();
  }
}
