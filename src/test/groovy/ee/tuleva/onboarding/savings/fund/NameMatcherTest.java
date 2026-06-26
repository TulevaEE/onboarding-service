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

  @Test
  void isSameName_matchesAbbreviatedAndSpelledOutOsauhing() {
    assertThat(nameMatcher.isSameName("ALPHA OÜ", "OSAÜHING ALPHA")).isTrue();
    assertThat(nameMatcher.isSameName("ALPHA OÜ", "ALPHA OSAÜHING")).isTrue();
    assertThat(nameMatcher.isSameName("OÜ ALPHA", "OSAÜHING ALPHA")).isTrue();
    assertThat(nameMatcher.isSameName("OÜ ALPHA", "ALPHA OÜ")).isTrue();
  }

  @Test
  void isSameName_matchesLegalFormRegardlessOfCaseDiacriticsAndPunctuation() {
    assertThat(nameMatcher.isSameName("alpha oü", "Osaühing Alpha")).isTrue();
    assertThat(nameMatcher.isSameName("Alpha, OÜ", "OSAUHING ALPHA")).isTrue();
    assertThat(nameMatcher.isSameName("Osauhing Alpha", "ALPHA OÜ")).isTrue();
  }

  @Test
  void isSameName_matchesOsauhingWhenAbbreviationDiacriticDroppedToOu() {
    assertThat(nameMatcher.isSameName("ALPHA OU", "ALPHA OÜ")).isTrue();
    assertThat(nameMatcher.isSameName("ALPHA OU", "OSAÜHING ALPHA")).isTrue();
    assertThat(nameMatcher.isSameName("Osauhing Alpha", "Alpha OU")).isTrue();
  }

  @Test
  void isSameName_doesNotMatchOsauhingToADifferentLegalForm() {
    assertThat(nameMatcher.isSameName("Alpha OÜ", "Alpha AS")).isFalse();
    assertThat(nameMatcher.isSameName("Osaühing Alpha", "Aktsiaselts Alpha")).isFalse();
  }

  @Test
  void isSameName_doesNotMatchWhenLegalFormPresentOnlyOnOneSide() {
    assertThat(nameMatcher.isSameName("Alpha OÜ", "Alpha")).isFalse();
    assertThat(nameMatcher.isSameName("Osaühing Alpha", "Alpha")).isFalse();
  }

  @Test
  void isSameName_doesNotTreatDifferentCompaniesWithSameFormAsEqual() {
    assertThat(nameMatcher.isSameName("Alpha OÜ", "Beta OÜ")).isFalse();
    assertThat(nameMatcher.isSameName("Osaühing Alpha", "Osaühing Beta")).isFalse();
  }

  @Test
  void isSameName_doesNotMatchWhenNameIsOnlyALegalForm() {
    assertThat(nameMatcher.isSameName("OÜ", "Osaühing")).isFalse();
    assertThat(nameMatcher.isSameName("OÜ", "OU")).isFalse();
    assertThat(nameMatcher.isSameName("Osaühing", "Osaühing")).isFalse();
    assertThat(nameMatcher.isSameName("OÜ", "Alpha OÜ")).isFalse();
  }

  @Test
  void isSameName_doesNotMatchWhenNameIsOnlyFie() {
    assertThat(nameMatcher.isSameName("FIE", "FIE")).isFalse();
    assertThat(nameMatcher.isSameName("FIE", "Alpha OÜ")).isFalse();
  }

  @Test
  void isSameName_doesNotMatchBlankOrPunctuationOnlyNames() {
    assertThat(nameMatcher.isSameName("", "")).isFalse();
    assertThat(nameMatcher.isSameName("   ", "Alpha OÜ")).isFalse();
    assertThat(nameMatcher.isSameName(".,-", "Alpha OÜ")).isFalse();
  }

  @Test
  void isSameName_isIndependentOfWordOrderEvenForMultiWordNames() {
    assertThat(nameMatcher.isSameName("Alpha Beta OÜ", "Beta Alpha OÜ")).isTrue();
    assertThat(nameMatcher.isSameName("Anna Maria Tamm", "Tamm Maria Anna")).isTrue();
  }

  @Test
  void isSameName_requiresSameTokenMultiset() {
    assertThat(nameMatcher.isSameName("Alpha Alpha OÜ", "Alpha OÜ")).isFalse();
    assertThat(nameMatcher.isSameName("Alpha Beta OÜ", "Alpha OÜ")).isFalse();
  }

  @Test
  void isSameName_sortsWholeTokensNotCharacters() {
    assertThat(nameMatcher.isSameName("Alpha OÜ", "Aplha OÜ")).isFalse();
    assertThat(nameMatcher.isSameName("AlphaBeta OÜ", "Alpha Beta OÜ")).isFalse();
  }
}
