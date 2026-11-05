package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfParser.ARIAL_GLYPH_TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GlyphCharacterTableTest {

  private static final String CHARACTERS_ON_SEB_FUND_CONFIRMATIONS =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÄÖÕÜäöõüŠšŽž/:,.()-+@€ ";

  @Test
  void committedArialTableCoversEveryCharacterOnASebFundConfirmationInBothWeights() {
    GlyphCharacterTable table = GlyphCharacterTable.fromClasspath(ARIAL_GLYPH_TABLE);

    Map<String, Long> fingerprintsPerCharacter =
        table
            .toTsv()
            .lines()
            .map(line -> Character.toString(Integer.parseInt(line.split("\t")[1], 16)))
            .collect(Collectors.groupingBy(character -> character, Collectors.counting()));

    Set<String> required =
        CHARACTERS_ON_SEB_FUND_CONFIRMATIONS
            .codePoints()
            .mapToObj(Character::toString)
            .collect(Collectors.toSet());
    assertThat(fingerprintsPerCharacter.keySet()).containsAll(required);
    assertThat(required.stream().filter(character -> !character.equals(" ")))
        .allSatisfy(character -> assertThat(fingerprintsPerCharacter.get(character)).isEqualTo(2L));
  }

  @Test
  void tsvRoundTripKeepsEveryMapping() {
    GlyphCharacterTable table = new GlyphCharacterTable(Map.of("abc", "ä", "def", " "));

    GlyphCharacterTable reloaded = GlyphCharacterTable.fromTsv(table.toTsv());

    assertThat(reloaded.character("abc")).contains("ä");
    assertThat(reloaded.character("def")).contains(" ");
    assertThat(reloaded.character("ghi")).isEmpty();
  }

  @Test
  void missingResourceFailsLoudly() {
    assertThatThrownBy(() -> GlyphCharacterTable.fromClasspath("no/such/table.tsv"))
        .isInstanceOf(IllegalStateException.class);
  }
}
