package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmation.SEB_TIME_ZONE;

import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class SebFundConfirmationPdfParser {

  static final String ARIAL_GLYPH_TABLE = "investment/seb-fund-confirmation/arial-glyphs.tsv";

  private static final Pattern CURRENCY_AFTER_THE_AMOUNT = Pattern.compile("\\s+[A-Z]{3}$");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yy");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final Map<String, TransactionType> SIDE_BY_TRADE_TYPE_CODE =
      Map.of("SUBS", BUY, "REDM", SELL);

  private final SebClientNameToFundResolver fundResolver;
  private final GlyphCharacterTable glyphTable;

  @Autowired
  SebFundConfirmationPdfParser(SebClientNameToFundResolver fundResolver) {
    this(fundResolver, GlyphCharacterTable.fromClasspath(ARIAL_GLYPH_TABLE));
  }

  SebFundConfirmationPdfParser(
      SebClientNameToFundResolver fundResolver, GlyphCharacterTable glyphTable) {
    this.fundResolver = fundResolver;
    this.glyphTable = glyphTable;
  }

  SebFundConfirmation parse(byte[] pdfBytes) {
    Map<String, List<String>> fields = fields(lines(pdfBytes));
    String tradeReference = value(fields, "Tehingu nr");
    return new SebFundConfirmation(
        fund(value(fields, "Kliendi nimi"), tradeReference),
        tradeReference,
        receivedAt(fields, tradeReference),
        date(value(fields, "Hinna arvutamise kuupäev"), tradeReference),
        date(value(fields, "Tegelik väärtuspäev"), tradeReference),
        value(fields, "ISIN kood"),
        side(value(fields, "Tehingu liik"), tradeReference),
        amount(value(fields, "Kogus"), tradeReference),
        amount(value(fields, "Hind"), tradeReference),
        amount(value(fields, "Kokkulepitud tehingusumma"), tradeReference),
        amount(value(fields, "Tehingutasu"), tradeReference),
        amount(value(fields, "Kogumaksumus"), tradeReference));
  }

  private List<String> lines(byte[] pdfBytes) {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      return GlyphTextExtractor.lines(document, glyphTable);
    } catch (IOException e) {
      throw new SebFundConfirmationPdfParseException("SEB fund confirmation PDF unreadable", e);
    }
  }

  private static Map<String, List<String>> fields(List<String> lines) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    lines.stream()
        .map(line -> line.split("\t"))
        .filter(cells -> cells.length > 1)
        .forEach(
            cells ->
                fields.putIfAbsent(
                    cells[0].trim(),
                    Arrays.stream(cells, 1, cells.length).map(String::trim).toList()));
    return fields;
  }

  private static String value(Map<String, List<String>> fields, String label) {
    return values(fields, label).getFirst();
  }

  private static List<String> values(Map<String, List<String>> fields, String label) {
    List<String> values = fields.get(label);
    if (values == null || values.isEmpty()) {
      throw new SebFundConfirmationPdfParseException(
          "SEB fund confirmation missing field: field=" + label);
    }
    return values;
  }

  private TulevaFund fund(String clientName, String tradeReference) {
    return fundResolver
        .resolve(clientName)
        .orElseThrow(
            () ->
                new SebFundConfirmationPdfParseException(
                    "SEB fund confirmation client is not a Tuleva fund: tradeReference="
                        + tradeReference));
  }

  private static Instant receivedAt(Map<String, List<String>> fields, String tradeReference) {
    List<String> values = values(fields, "Korralduse saamise aeg");
    if (values.size() < 2) {
      throw new SebFundConfirmationPdfParseException(
          "SEB fund confirmation order receipt has no time: tradeReference=" + tradeReference);
    }
    try {
      return date(values.get(0), tradeReference)
          .atTime(LocalTime.parse(values.get(1), TIME))
          .atZone(SEB_TIME_ZONE)
          .toInstant();
    } catch (DateTimeParseException e) {
      throw new SebFundConfirmationPdfParseException(
          "SEB fund confirmation has malformed receipt time: tradeReference=" + tradeReference, e);
    }
  }

  private static LocalDate date(String value, String tradeReference) {
    try {
      return LocalDate.parse(value, DATE);
    } catch (DateTimeParseException e) {
      throw new SebFundConfirmationPdfParseException(
          "SEB fund confirmation has malformed date: tradeReference="
              + tradeReference
              + ", value="
              + value,
          e);
    }
  }

  private static TransactionType side(String tradeType, String tradeReference) {
    TransactionType side = SIDE_BY_TRADE_TYPE_CODE.get(tradeType.split(" ")[0]);
    if (side == null) {
      throw new SebFundConfirmationPdfParseException(
          "SEB fund confirmation has unknown trade type: tradeReference="
              + tradeReference
              + ", tradeType="
              + tradeType);
    }
    return side;
  }

  private static BigDecimal amount(String value, String tradeReference) {
    try {
      return new BigDecimal(
          CURRENCY_AFTER_THE_AMOUNT.matcher(value).replaceFirst("").replace(",", ""));
    } catch (NumberFormatException e) {
      throw new SebFundConfirmationPdfParseException(
          "SEB fund confirmation has malformed amount: tradeReference="
              + tradeReference
              + ", value="
              + value,
          e);
    }
  }
}
