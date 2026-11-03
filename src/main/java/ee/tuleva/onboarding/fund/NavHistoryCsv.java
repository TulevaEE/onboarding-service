package ee.tuleva.onboarding.fund;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

record NavHistoryCsv(String isin, List<NavValueResponse> navHistory) {

  private static final DateTimeFormatter ESTONIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  byte[] toBytes() {
    try (var outputStream = new ByteArrayOutputStream();
        var writer = new OutputStreamWriter(outputStream, UTF_8)) {
      outputStream.write(UTF8_BOM);
      var csvFormat =
          CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader("Kuupäev", "NAV (EUR)").get();
      try (var printer = new CSVPrinter(writer, csvFormat)) {
        for (var row : navHistory) {
          printer.printRecord(ESTONIAN_DATE.format(row.date()), row.value().toPlainString());
        }
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate NAV history CSV: isin=" + isin, e);
    }
  }
}
