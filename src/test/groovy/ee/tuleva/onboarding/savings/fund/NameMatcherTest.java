package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
  void isSameName_ignoresTypographicQuotesSubstitutedWithAsciiPunctuation() {
    assertThat(nameMatcher.isSameName("Osaühing „Alpha”", "Osaühing .Alpha.")).isTrue();
    assertThat(nameMatcher.isSameName("O’Brien", "O'Brien")).isTrue();
  }

  @Test
  void isSameName_ignoresNonBreakingSpaceAndZeroWidthFormatting() {
    assertThat(nameMatcher.isSameName("Osaühing Alpha", "Osaühing Alpha")).isTrue();
    assertThat(nameMatcher.isSameName("Al­pha OÜ", "Alpha OÜ")).isTrue();
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

  @Test
  void similarity_isOneForTheSameNameWrittenWithoutItsDiacritics() {
    assertThat(nameMatcher.similarity("PÄRT ÕLEKÕRS", "Part Olekors")).isEqualTo(1.0);
    assertThat(nameMatcher.similarity("Jüri Šmidt-Žukov", "JURI SMIDT ZUKOV")).isEqualTo(1.0);
    assertThat(nameMatcher.similarity("Õie Väärtmäe", "oie vaartmae")).isEqualTo(1.0);
  }

  @Test
  void similarity_isOneForAGermanOrEnglishTransliterationOfTheDiacritics() {
    assertThat(nameMatcher.similarity("Jürgen Müller", "JUERGEN MUELLER")).isEqualTo(1.0);
    assertThat(nameMatcher.similarity("Juergen Mueller", "Jürgen Müller")).isEqualTo(1.0);
    assertThat(nameMatcher.similarity("Šarlota Žukova", "Sharlota Zhukova")).isEqualTo(1.0);
    assertThat(nameMatcher.similarity("Mägi Öövel", "Maegi Oeoevel")).isEqualTo(1.0);
  }

  @Test
  void similarity_isOneWhateverTheOrderOfTheWords() {
    assertThat(nameMatcher.similarity("ÕLEKÕRS PÄRT", "Part Olekors")).isEqualTo(1.0);
    assertThat(nameMatcher.similarity("Tamm, Anna Maria", "Maria Anna Tamm")).isEqualTo(1.0);
  }

  @Test
  void similarity_isOneLessTheEditsPerCharacterOfTheLongerName() {
    assertThat(nameMatcher.similarity("Aleksandr Ivanov", "ALEKSANDER IVANOV"))
        .isCloseTo(1 - 1.0 / 17, within(1e-12));
    assertThat(nameMatcher.similarity("Mari Tamm", "Mari Kask"))
        .isCloseTo(1 - 3.0 / 9, within(1e-12));
  }

  @Test
  void similarity_comparesTheWordsInTheOrderClosestToTheClientsEvenWithATypo() {
    assertThat(nameMatcher.similarity("ZRU KATI", "Kati Aru"))
        .isCloseTo(1 - 1.0 / 8, within(1e-12));
  }

  @Test
  void similarity_isZeroWithoutADistinguishingName() {
    assertThat(nameMatcher.similarity(null, "Mari Tamm")).isZero();
    assertThat(nameMatcher.similarity("Mari Tamm", null)).isZero();
    assertThat(nameMatcher.similarity("", "")).isZero();
    assertThat(nameMatcher.similarity("FIE", "FIE")).isZero();
    assertThat(nameMatcher.similarity("OÜ", "Osaühing")).isZero();
  }

  @Test
  void isSimilarName_acceptsAtLeastNinetyPercentSimilarity() {
    assertThat(nameMatcher.isSimilarName("Mari Tamme", "MARI TAMM")).isTrue();
    assertThat(nameMatcher.isSimilarName("Aleksandr Ivanov", "Aleksander Ivanov")).isTrue();
    assertThat(nameMatcher.isSimilarName("Jürgen Müller", "JUERGEN MUELLER")).isTrue();
  }

  @Test
  void isSimilarName_rejectsLessThanNinetyPercentSimilarity() {
    assertThat(nameMatcher.isSimilarName("Mari Tammela", "MARI TAMM")).isFalse();
    assertThat(nameMatcher.isSimilarName("Kati Aro", "Kati Aru")).isFalse();
    assertThat(nameMatcher.isSimilarName("Mari Kask", "Mari Tamm")).isFalse();
    assertThat(nameMatcher.isSimilarName("Wise Europe SA", "Mari Tamm")).isFalse();
    assertThat(nameMatcher.isSimilarName(null, "Mari Tamm")).isFalse();
  }

  @Test
  void looksLikeACompany_recognisesOsauhingWithOrWithoutDiacriticsInAnyCase() {
    assertThat(nameMatcher.looksLikeACompany("Maasikas OÜ")).isTrue();
    assertThat(nameMatcher.looksLikeACompany("MAASIKAS OU")).isTrue();
    assertThat(nameMatcher.looksLikeACompany("maasikas ou")).isTrue();
    assertThat(nameMatcher.looksLikeACompany("Osaühing Maasikas")).isTrue();
    assertThat(nameMatcher.looksLikeACompany("OSAUHING MAASIKAS")).isTrue();
    assertThat(nameMatcher.looksLikeACompany("osauhing maasikas")).isTrue();
  }

  @Test
  void looksLikeACompany_recognisesTheLegalFormNextToPunctuationAndUnicodeVariants() {
    assertThat(nameMatcher.looksLikeACompany("Maasikas OÜ,")).isTrue();
    assertThat(nameMatcher.looksLikeACompany("Maasikas (OÜ)")).isTrue();
    assertThat(nameMatcher.looksLikeACompany("Maasikas OU\u0308")).isTrue();
    assertThat(nameMatcher.looksLikeACompany("OS\u00ADAÜHING Maasikas")).isTrue();
  }

  @Test
  void looksLikeACompany_doesNotMatchTheLegalFormInsideALongerWord() {
    assertThat(nameMatcher.looksLikeACompany("PÄRT SOÜLA")).isFalse();
    assertThat(nameMatcher.looksLikeACompany("OUMAR MAASIKAS")).isFalse();
    assertThat(nameMatcher.looksLikeACompany("SOULA")).isFalse();
  }

  @Test
  void looksLikeACompany_isFalseForOtherLegalFormsAndMissingNames() {
    assertThat(nameMatcher.looksLikeACompany("Maasikas AS")).isFalse();
    assertThat(nameMatcher.looksLikeACompany(null)).isFalse();
    assertThat(nameMatcher.looksLikeACompany("")).isFalse();
  }
}
