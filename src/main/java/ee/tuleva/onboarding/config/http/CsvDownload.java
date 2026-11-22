package ee.tuleva.onboarding.config.http;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class CsvDownload {

  public static final DateTimeFormatter ESTONIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
  private static final char ESTONIAN_SPREADSHEET_DELIMITER = ';';
  private static final MediaType CSV_IN_UTF8 = MediaType.parseMediaType("text/csv;charset=UTF-8");

  private CsvDownload() {}

  public static byte[] bytes(List<String> header, List<List<String>> records) {
    try (var outputStream = new ByteArrayOutputStream();
        var writer = new OutputStreamWriter(outputStream, UTF_8)) {
      outputStream.write(UTF8_BOM);
      var csvFormat =
          CSVFormat.DEFAULT
              .builder()
              .setDelimiter(ESTONIAN_SPREADSHEET_DELIMITER)
              .setHeader(header.toArray(String[]::new))
              .get();
      try (var printer = new CSVPrinter(writer, csvFormat)) {
        printer.printRecords(records);
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write CSV: header=" + header, e);
    }
  }

  public static ResponseEntity<byte[]> attachment(String fileNamePrefix, String isin, byte[] csv) {
    return ResponseEntity.ok()
        .contentType(CSV_IN_UTF8)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\""
                + fileNamePrefix
                + "-"
                + isin.replaceAll("[^A-Za-z0-9]", "")
                + ".csv\"")
        .body(csv);
  }
}
