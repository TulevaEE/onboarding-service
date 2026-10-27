package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfs.buyFields;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfs.confirmation;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationPdfs.liberationSansTable;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SebFundConfirmationPdfParserTest {

  private static final GlyphCharacterTable TABLE = liberationSansTable();

  private final SebFundConfirmationPdfParser parser =
      new SebFundConfirmationPdfParser(new SebClientNameToFundResolver(), TABLE);

  @Test
  void parse_readsEveryFieldOfABuyConfirmationWhoseFontsHaveNoCharacterMap() {
    SebFundConfirmation result = parser.parse(confirmation(buyFields()));

    assertThat(result)
        .isEqualTo(
            new SebFundConfirmation(
                TUK75,
                "DLA1234567",
                Instant.parse("2026-01-05T09:48:17Z"),
                LocalDate.of(2026, 1, 6),
                LocalDate.of(2026, 1, 9),
                "IE00BKPTWY98",
                BUY,
                new BigDecimal("1000.00"),
                new BigDecimal("12.345678901"),
                new BigDecimal("12345.68"),
                new BigDecimal("0.00"),
                new BigDecimal("12345.68")));
  }

  @Test
  void parse_keepsTheFieldsApartFromTextAtTheSameHeightOnALaterPage() {
    byte[] twoPages = confirmation(buyFields(), List.of("Tehingu nr", "DLA9999999"));

    assertThat(parser.parse(twoPages)).isEqualTo(parser.parse(confirmation(buyFields())));
  }

  @Test
  void parse_readsARedemptionAsASell() {
    Map<String, List<String>> fields = buyFields();
    fields.put("Tehingu liik", List.of("REDM Müük (Osakute tagasivõtmine)"));

    SebFundConfirmation result = parser.parse(confirmation(fields));

    assertThat(result.side()).isEqualTo(SELL);
  }

  @Test
  void parse_rejectsATradeTypeItDoesNotKnow() {
    Map<String, List<String>> fields = buyFields();
    fields.put("Tehingu liik", List.of("SWCH Vahetus"));

    assertThatThrownBy(() -> parser.parse(confirmation(fields)))
        .isInstanceOf(SebFundConfirmationPdfParseException.class);
  }

  @Test
  void parse_rejectsAClientNameThatIsNotATulevaFund() {
    Map<String, List<String>> fields = buyFields();
    fields.put("Kliendi nimi", List.of("SOME OTHER FUND"));

    assertThatThrownBy(() -> parser.parse(confirmation(fields)))
        .isInstanceOf(SebFundConfirmationPdfParseException.class);
  }

  @Test
  void parse_rejectsAConfirmationWithoutAPricingDate() {
    Map<String, List<String>> fields = buyFields();
    fields.remove("Hinna arvutamise kuupäev");

    assertThatThrownBy(() -> parser.parse(confirmation(fields)))
        .isInstanceOf(SebFundConfirmationPdfParseException.class);
  }

  @Test
  void parse_rejectsAMalformedDate() {
    Map<String, List<String>> fields = buyFields();
    fields.put("Hinna arvutamise kuupäev", List.of("2026-01-06"));

    assertThatThrownBy(() -> parser.parse(confirmation(fields)))
        .isInstanceOf(SebFundConfirmationPdfParseException.class);
  }

  @Test
  void parse_failsRatherThanGuessWhenAGlyphIsNotInTheTable() {
    SebFundConfirmationPdfParser parserWithEmptyTable =
        new SebFundConfirmationPdfParser(
            new SebClientNameToFundResolver(), new GlyphCharacterTable(Map.of()));

    assertThatThrownBy(() -> parserWithEmptyTable.parse(confirmation(buyFields())))
        .isInstanceOf(SebFundConfirmationPdfParseException.class);
  }

  @Test
  void parse_rejectsBytesThatAreNotAPdf() {
    assertThatThrownBy(() -> parser.parse(new byte[] {1, 2, 3}))
        .isInstanceOf(SebFundConfirmationPdfParseException.class);
  }
}
