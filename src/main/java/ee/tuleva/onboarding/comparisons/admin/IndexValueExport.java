package ee.tuleva.onboarding.comparisons.admin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.IndexValueReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
class IndexValueExport {

  static final int MAX_KEYS = 100;
  static final int MAX_ROWS = 50_000;

  private static final CSVFormat CSV_FORMAT =
      CSVFormat.DEFAULT.builder().setHeader("key", "date", "value", "provider", "updatedAt").get();

  private final FundValueRepository fundValueRepository;
  private final IndexValueReader indexValueReader;

  byte[] latest(String keys) {
    var requested = requestedKeys(keys);
    var values = indexValueReader.findLatestValuesByKeys(requested);
    rejectUnknown(requested, values.stream().map(FundValue::key).collect(toSet()));
    return toCsv(values);
  }

  byte[] between(String keys, LocalDate startDate, LocalDate endDate) {
    if (startDate.isAfter(endDate)) {
      throw badRequest(
          "startDate is after endDate: startDate=" + startDate + ", endDate=" + endDate);
    }
    var keyList = knownKeys(keys);
    rejectTooMany(indexValueReader.countValuesBetweenDatesForKeys(keyList, startDate, endDate));
    var values =
        indexValueReader.findValuesBetweenDatesForKeys(keyList, startDate, endDate, MAX_ROWS + 1);
    rejectTooMany(values.size());
    return toCsv(values);
  }

  private static void rejectTooMany(long rows) {
    if (rows > MAX_ROWS) {
      throw badRequest(
          "Too many rows, narrow the dates or keys: rows=" + rows + ", max=" + MAX_ROWS);
    }
  }

  private List<String> knownKeys(String keys) {
    var requested = requestedKeys(keys);
    rejectUnknown(
        requested, fundValueRepository.findLatestDateByKeys(Set.copyOf(requested)).keySet());
    return requested;
  }

  private static void rejectUnknown(List<String> requested, Set<String> known) {
    var unknown = requested.stream().filter(key -> !known.contains(key)).toList();
    if (!unknown.isEmpty()) {
      throw badRequest("Unknown keys, no index values stored: keys=" + String.join(",", unknown));
    }
  }

  private static List<String> requestedKeys(String keys) {
    var keyList =
        Arrays.stream(keys.split(","))
            .map(String::trim)
            .filter(key -> !key.isEmpty())
            .distinct()
            .toList();
    if (keyList.isEmpty()) {
      throw badRequest("No keys requested: keys=" + keys);
    }
    if (keyList.size() > MAX_KEYS) {
      throw badRequest("Too many keys: keys=" + keyList.size() + ", max=" + MAX_KEYS);
    }
    return keyList;
  }

  private static ResponseStatusException badRequest(String reason) {
    return new ResponseStatusException(BAD_REQUEST, reason);
  }

  private static byte[] toCsv(List<FundValue> values) {
    var outputStream = new ByteArrayOutputStream();
    try (var printer = new CSVPrinter(new OutputStreamWriter(outputStream, UTF_8), CSV_FORMAT)) {
      for (var row : values) {
        printer.printRecord(
            row.key(), row.date(), row.value().toPlainString(), row.provider(), row.updatedAt());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to generate index values CSV", e);
    }
    return outputStream.toByteArray();
  }
}
