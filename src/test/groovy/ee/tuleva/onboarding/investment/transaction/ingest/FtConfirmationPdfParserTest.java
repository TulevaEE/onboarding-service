package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class FtConfirmationPdfParserTest {

  private final FtConfirmationPdfParser parser =
      new FtConfirmationPdfParser(new FtAccountToFundResolver());

  @Test
  void extractField_matchesLabelAndValueOnSameLine() {
    String text = "Allocation ID: MID9BlFbos-00\nISIN: IE00BJZ2DC62";

    Optional<String> result = FtConfirmationPdfParser.extractField(text, "Allocation ID");

    assertThat(result).contains("MID9BlFbos-00");
  }

  @Test
  void extractField_fallsBackToValueOnNextLine() {
    String text = "Allocation ID:\nMID9BlFbos-00\nISIN: IE00BJZ2DC62";

    Optional<String> result = FtConfirmationPdfParser.extractField(text, "Allocation ID");

    assertThat(result).contains("MID9BlFbos-00");
  }

  @Test
  void extractField_doesNotMatchLabelThatIsAPrefixOfAnotherLabel() {
    String text = "Trade Date Time: 20260717-09:32:44 UTC\nTrade Date: 20260717";

    Optional<String> result = FtConfirmationPdfParser.extractField(text, "Trade Date");

    assertThat(result).contains("20260717");
  }

  @Test
  void parseTradeDate_parsesYyyyMMdd() {
    LocalDate result = FtConfirmationPdfParser.parseTradeDate("20260717");

    assertThat(result).isEqualTo(LocalDate.of(2026, 7, 17));
  }

  @Test
  void parseTradeDate_malformedValueThrows() {
    assertThatThrownBy(() -> FtConfirmationPdfParser.parseTradeDate("2026-07-17"))
        .isInstanceOf(FtConfirmationPdfParseException.class);
  }

  @Test
  void parseQuantity_stripsCommaThousandsSeparators() {
    BigDecimal result = FtConfirmationPdfParser.parseQuantity("1,047");

    assertThat(result).isEqualByComparingTo("1047");
  }

  @Test
  void parseQuantity_malformedValueThrows() {
    assertThatThrownBy(() -> FtConfirmationPdfParser.parseQuantity("abc"))
        .isInstanceOf(FtConfirmationPdfParseException.class);
  }

  @Test
  void parseGrossPrice_stripsTrailingEurAndKeepsSixDecimalPrecision() {
    BigDecimal result = FtConfirmationPdfParser.parseGrossPrice("56.850000 EUR");

    assertThat(result).isEqualByComparingTo("56.850000");
    assertThat(result.scale()).isEqualTo(6);
  }

  @Test
  void parseGrossPrice_malformedValueThrows() {
    assertThatThrownBy(() -> FtConfirmationPdfParser.parseGrossPrice("not-a-price EUR"))
        .isInstanceOf(FtConfirmationPdfParseException.class);
  }

  @Test
  void isCancellation_detectsCancellationPhraseCaseInsensitively() {
    String text = "Dear Sir/Madam,\nWe confirm the following Trade Cancellation.\n";

    assertThat(FtConfirmationPdfParser.isCancellation(text)).isTrue();
  }

  @Test
  void isCancellation_normalTradeIsNotACancellation() {
    String text = "Dear Sir/Madam,\nWe confirm the following trade .\n";

    assertThat(FtConfirmationPdfParser.isCancellation(text)).isFalse();
  }

  @Test
  void parse_extractsAllFieldsFromACleanConfirmationPdf() {
    byte[] pdf =
        confirmationPdf(
            "TEST0001-00",
            "Tuleva Additional Investment Fund",
            "20260115",
            "IE00BFG1TM61",
            "2,500",
            "12.345600 EUR",
            false);

    FtConfirmation result = parser.parse(pdf);

    assertThat(result)
        .isEqualTo(
            new FtConfirmation(
                TKF100,
                "IE00BFG1TM61",
                LocalDate.of(2026, 1, 15),
                new BigDecimal("2500"),
                new BigDecimal("12.345600"),
                FtConfirmationType.NORMAL,
                "Tuleva Additional Investment Fund"));
  }

  @Test
  void parse_resolvesTuv100FromAccountAlias() {
    byte[] pdf =
        confirmationPdf(
            "TEST0002-00",
            "TULEVA III SAMBA PENSIONIFOND",
            "20260115",
            "IE000I9HGDZ3",
            "88,496",
            "9.920000 EUR",
            false);

    FtConfirmation result = parser.parse(pdf);

    assertThat(result.fund()).isEqualTo(TUV100);
  }

  @Test
  void parse_resolvesTuk75FromMaakpeAccountAlias() {
    byte[] pdf =
        confirmationPdf(
            "TEST0003-00", "MAAKPE", "20260115", "IE000I9HGDZ3", "123,542", "9.920000 EUR", false);

    FtConfirmation result = parser.parse(pdf);

    assertThat(result.fund()).isEqualTo(TUK75);
  }

  @Test
  void parse_detectsCancellationFromBodyPhrase() {
    byte[] pdf =
        confirmationPdf(
            "TEST0004-00",
            "Tuleva Additional Investment Fund",
            "20260115",
            "IE00BFG1TM61",
            "2,500",
            "12.345600 EUR",
            true);

    FtConfirmation result = parser.parse(pdf);

    assertThat(result.type()).isEqualTo(FtConfirmationType.CANCELLATION);
    assertThat(result.isCancellation()).isTrue();
  }

  @Test
  void parse_unrecognizedAccountThrows() {
    byte[] pdf =
        confirmationPdf(
            "TEST0005-00",
            "Some Unmapped Fund Name",
            "20260115",
            "IE00BFG1TM61",
            "2,500",
            "12.345600 EUR",
            false);

    assertThatThrownBy(() -> parser.parse(pdf)).isInstanceOf(FtConfirmationPdfParseException.class);
  }

  @Test
  void parse_missingRequiredFieldThrows() {
    byte[] pdf =
        confirmationPdfMissingIsin(
            "TEST0006-00",
            "Tuleva Additional Investment Fund",
            "20260115",
            "2,500",
            "12.345600 EUR");

    assertThatThrownBy(() -> parser.parse(pdf)).isInstanceOf(FtConfirmationPdfParseException.class);
  }

  private static byte[] confirmationPdfMissingIsin(
      String allocationId, String account, String tradeDate, String quantity, String grossPrice) {
    List<String> lines =
        List.of(
            "Trade Confirmation",
            "Dear Sir/Madam,",
            "We confirm the following trade .",
            "Allocation ID: " + allocationId,
            "Counterparty: TULEVA FONDID AS",
            "Account: " + account,
            "Trade Date: " + tradeDate,
            "Quantity: " + quantity,
            "Gross Price: " + grossPrice,
            "Settlement Currency: EUR");
    return renderPdf(lines);
  }

  private static byte[] confirmationPdf(
      String allocationId,
      String account,
      String tradeDate,
      String isin,
      String quantity,
      String grossPrice,
      boolean cancellation) {
    List<String> lines =
        List.of(
            "Trade Confirmation",
            "Dear Sir/Madam,",
            cancellation
                ? "We confirm the following trade cancellation."
                : "We confirm the following trade .",
            "Allocation ID: " + allocationId,
            "Counterparty: TULEVA FONDID AS",
            "Account: " + account,
            "Trade Date Time: " + tradeDate + "-09:32:44 UTC",
            "Trade Date: " + tradeDate,
            "Settlement Date: " + tradeDate,
            "Security Description: SYNTHETIC TEST SECURITY",
            "Bloomberg Ticker: TEST GY Equity",
            "ISIN: " + isin,
            "Your Direction: Buy",
            "Quantity: " + quantity,
            "Gross Price: " + grossPrice,
            "Net Price: " + grossPrice,
            "Settlement Currency: EUR");
    return renderPdf(lines);
  }

  private static byte[] renderPdf(List<String> lines) {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(font, 10);
        cs.newLineAtOffset(50, 750);
        for (String line : lines) {
          cs.showText(line);
          cs.newLineAtOffset(0, -14);
        }
        cs.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
