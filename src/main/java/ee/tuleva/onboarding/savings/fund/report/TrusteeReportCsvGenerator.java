package ee.tuleva.onboarding.savings.fund.report;

import static java.math.RoundingMode.HALF_UP;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
class TrusteeReportCsvGenerator {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private static final String[] HEADERS = {
    "Kuupäev",
    "NAV",
    "Väljalastud osakute kogus",
    "Väljalastud osakute summa",
    "Tagasivõetud osakute kogus",
    "Tagasivõetud osakute summa",
    "Fondi väljalastud osakute arv"
  };

  byte[] generate(List<TrusteeReportRow> rows) {
    try (var outputStream = new ByteArrayOutputStream();
        var writer = new OutputStreamWriter(outputStream, UTF_8)) {

      outputStream.write(UTF8_BOM);

      var format = CSVFormat.DEFAULT.builder().setHeader(HEADERS).build();

      try (var printer = new CSVPrinter(writer, format)) {
        for (var row : rows) {
          printer.printRecord(
              row.reportDate().format(DATE_FORMAT),
              formatNav(row.nav()),
              formatUnits(row.issuedUnits()),
              formatEuros(row.issuedAmount()),
              formatUnits(row.redeemedUnits()),
              formatEuros(row.redeemedAmount()),
              formatUnits(row.totalOutstandingUnits()));
        }
      }

      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate trustee report CSV", e);
    }
  }

  private static String formatNav(BigDecimal value) {
    return value.setScale(4, HALF_UP).toPlainString().replace('.', ',');
  }

  private static String formatEuros(BigDecimal value) {
    return value.setScale(2, HALF_UP).toPlainString().replace('.', ',');
  }

  private static String formatUnits(BigDecimal value) {
    return value.setScale(3, HALF_UP).toPlainString().replace('.', ',');
  }
}
