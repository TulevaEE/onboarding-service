package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NameMatcherTest {

  private final NameMatcher nameMatcher = new NameMatcher();

  @Test
  @DisplayName("isSameName returns false for null values")
  void isSameName_nullValues() {
    assertThat(nameMatcher.isSameName(null, null)).isFalse();
    assertThat(nameMatcher.isSameName("A", null)).isFalse();
    assertThat(nameMatcher.isSameName(null, "A")).isFalse();
  }

  @Test
  @DisplayName("isSameName returns false for different names")
  void isSameName_differentNames() {
    assertThat(nameMatcher.isSameName("A", "B")).isFalse();
    assertThat(nameMatcher.isSameName("A A", "AA")).isFalse();
  }

  @Test
  @DisplayName("isSameName returns true for exact match")
  void isSameName_exactMatch() {
    assertThat(nameMatcher.isSameName("A", "A")).isTrue();
  }

  @Test
  @DisplayName("isSameName ignores extra whitespace")
  void isSameName_whitespace() {
    assertThat(nameMatcher.isSameName("A", " A   ")).isTrue();
  }

  @Test
  @DisplayName("isSameName is order-independent")
  void isSameName_orderIndependent() {
    assertThat(nameMatcher.isSameName("AA BB CC", "BB CC AA")).isTrue();
    assertThat(nameMatcher.isSameName("KASK MARI", "MARI KASK")).isTrue();
  }

  @Test
  @DisplayName("isSameName ignores punctuation")
  void isSameName_punctuation() {
    assertThat(nameMatcher.isSameName("AA/BB,CC", "BB+CC-AA")).isTrue();
    assertThat(nameMatcher.isSameName("MARI-LIIS KASK", "MARI LIIS KASK")).isTrue();
  }

  @Test
  @DisplayName("isSameName is case-insensitive")
  void isSameName_caseInsensitive() {
    assertThat(nameMatcher.isSameName("aaa bbb", "Aaa BBB")).isTrue();
  }

  @Test
  @DisplayName("isSameName normalizes diacritics")
  void isSameName_diacritics() {
    assertThat(nameMatcher.isSameName("Jüri Õun", "JURI OUN")).isTrue();
    assertThat(nameMatcher.isSameName("PÄRT ÕLEKÕRS", "Part Olekors")).isTrue();
  }
}
