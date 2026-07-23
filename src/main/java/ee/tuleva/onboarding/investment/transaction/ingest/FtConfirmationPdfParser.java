package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.CANCELLATION;
import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.NORMAL;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
class FtConfirmationPdfParser {

  private static final Pattern LINE_SEPARATOR = Pattern.compile("\\r\\n|\\r|\\n");
  private static final DateTimeFormatter TRADE_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final Pattern TRAILING_EUR = Pattern.compile("(?i)\\s*EUR\\s*$");
  private static final Pattern CANCELLATION_PHRASE = Pattern.compile("(?i)trade cancellation");

  private final FtAccountToFundResolver accountToFundResolver;

  FtConfirmation parse(byte[] pdfBytes) {
    String text = extractText(pdfBytes);

    String allocationId = extractRequired(text, "Allocation ID");
    String account = extractRequired(text, "Account", allocationId);
    TulevaFund fund =
        accountToFundResolver
            .resolve(account)
            .orElseThrow(
                () ->
                    new FtConfirmationPdfParseException(
                        "FT confirmation PDF has unrecognized Account: account="
                            + account
                            + ", allocationId="
                            + allocationId));
    String isin = extractRequired(text, "ISIN", allocationId);
    LocalDate tradeDate = parseTradeDate(extractRequired(text, "Trade Date", allocationId));
    BigDecimal quantity = parseQuantity(extractRequired(text, "Quantity", allocationId));
    BigDecimal grossPrice = parseGrossPrice(extractRequired(text, "Gross Price", allocationId));
    FtConfirmationType type = isCancellation(text) ? CANCELLATION : NORMAL;

    return new FtConfirmation(fund, isin, tradeDate, quantity, grossPrice, type, account);
  }

  private static String extractText(byte[] pdfBytes) {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      return new PDFTextStripper().getText(document);
    } catch (IOException e) {
      throw new FtConfirmationPdfParseException("FT confirmation PDF could not be read", e);
    }
  }

  private static String extractRequired(String text, String label) {
    return extractField(text, label)
        .orElseThrow(
            () ->
                new FtConfirmationPdfParseException(
                    "FT confirmation PDF missing required field: field=" + label));
  }

  private static String extractRequired(String text, String label, String allocationId) {
    return extractField(text, label)
        .orElseThrow(
            () ->
                new FtConfirmationPdfParseException(
                    "FT confirmation PDF missing required field: field="
                        + label
                        + ", allocationId="
                        + allocationId));
  }

  static LocalDate parseTradeDate(String rawTradeDate) {
    try {
      return LocalDate.parse(rawTradeDate, TRADE_DATE_FORMAT);
    } catch (DateTimeParseException e) {
      throw new FtConfirmationPdfParseException(
          "FT confirmation PDF has malformed Trade Date: value=" + rawTradeDate, e);
    }
  }

  static BigDecimal parseQuantity(String rawQuantity) {
    String cleaned = rawQuantity.replace(",", "");
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      throw new FtConfirmationPdfParseException(
          "FT confirmation PDF has malformed Quantity: value=" + rawQuantity, e);
    }
  }

  static boolean isCancellation(String text) {
    return CANCELLATION_PHRASE.matcher(text).find();
  }

  static BigDecimal parseGrossPrice(String rawGrossPrice) {
    String cleaned = TRAILING_EUR.matcher(rawGrossPrice).replaceFirst("").trim();
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      throw new FtConfirmationPdfParseException(
          "FT confirmation PDF has malformed Gross Price: value=" + rawGrossPrice, e);
    }
  }

  static Optional<String> extractField(String text, String label) {
    String[] lines = LINE_SEPARATOR.split(text, -1);
    Pattern sameLine =
        Pattern.compile(Pattern.quote(label) + "\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    Pattern labelOnly = Pattern.compile(Pattern.quote(label) + "\\s*:?", Pattern.CASE_INSENSITIVE);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) {
        continue;
      }
      var sameLineMatch = sameLine.matcher(line);
      if (sameLineMatch.matches()) {
        String value = sameLineMatch.group(1).trim();
        if (!value.isEmpty()) {
          return Optional.of(value);
        }
        return nextNonBlankLine(lines, i + 1);
      }
      if (labelOnly.matcher(line).matches()) {
        return nextNonBlankLine(lines, i + 1);
      }
    }
    return Optional.empty();
  }

  private static Optional<String> nextNonBlankLine(String[] lines, int fromIndex) {
    for (int i = fromIndex; i < lines.length; i++) {
      String line = lines[i].trim();
      if (!line.isEmpty()) {
        return Optional.of(line);
      }
    }
    return Optional.empty();
  }
}
