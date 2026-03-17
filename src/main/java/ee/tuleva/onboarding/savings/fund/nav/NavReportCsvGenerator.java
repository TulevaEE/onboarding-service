package ee.tuleva.onboarding.savings.fund.nav;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
class NavReportCsvGenerator {

  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private static final String[] HEADERS = {
    "nav_date",
    "fund_code",
    "account_type",
    "account_name",
    "account_id",
    "quantity",
    "market_price",
    "currency",
    "market_value"
  };

  byte[] generate(List<NavReportRow> rows) {
    try (var outputStream = new ByteArrayOutputStream();
        var writer = new OutputStreamWriter(outputStream, UTF_8)) {

      outputStream.write(UTF8_BOM);

      var format = CSVFormat.DEFAULT.builder().setHeader(HEADERS).get();

      try (var printer = new CSVPrinter(writer, format)) {
        for (var row : rows) {
          printer.printRecord(
              row.getNavDate(),
              row.getFundCode(),
              row.getAccountType(),
              row.getAccountName(),
              row.getAccountId() != null ? row.getAccountId() : "",
              formatDecimal(row.getQuantity()),
              formatDecimal(row.getMarketPrice()),
              row.getCurrency().name(),
              formatDecimal(row.getMarketValue()));
        }
      }

      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate NAV report CSV", e);
    }
  }

  private static String formatDecimal(BigDecimal value) {
    return value != null ? value.toPlainString() : "";
  }
}
