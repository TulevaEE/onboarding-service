package ee.tuleva.onboarding.comparisons;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.config.http.NoCache;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
class IndexValueController {

  static final int MAX_KEYS = 100;
  static final int MAX_ROWS = 50_000;
  static final String CSV = "csv";

  private final FundValueRepository fundValueRepository;
  private final AdminTokenValidator tokenValidator;

  @GetMapping("/index-values")
  @NoCache
  ResponseEntity<byte[]> getIndexValues(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String keys,
      @RequestParam(defaultValue = CSV) String format,
      @RequestParam(required = false) @Nullable LocalDate startDate,
      @RequestParam(required = false) @Nullable LocalDate endDate) {
    tokenValidator.validate(token);

    if (!CSV.equalsIgnoreCase(format)) {
      throw badRequest("Unsupported format: format=" + format + ", supported=" + CSV);
    }
    List<String> keyList = requestedKeys(keys);
    List<FundValue> values = lookUp(keyList, startDate, endDate);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"index-values.csv\"")
        .body(toCsv(values));
  }

  private List<String> requestedKeys(String keys) {
    List<String> keyList =
        Arrays.stream(keys.split(",")).map(String::trim).filter(key -> !key.isEmpty()).toList();
    if (keyList.isEmpty()) {
      throw badRequest("keys must not be empty");
    }
    if (keyList.size() > MAX_KEYS) {
      throw badRequest("Too many keys: keys=" + keyList.size() + ", max=" + MAX_KEYS);
    }
    return keyList;
  }

  private List<FundValue> lookUp(
      List<String> keys, @Nullable LocalDate startDate, @Nullable LocalDate endDate) {
    if (startDate == null && endDate == null) {
      return fundValueRepository.findLatestValuesByKeys(keys);
    }
    if (startDate == null || endDate == null) {
      throw badRequest("startDate and endDate must be given together, or neither");
    }
    if (startDate.isAfter(endDate)) {
      throw badRequest(
          "startDate is after endDate: startDate=" + startDate + ", endDate=" + endDate);
    }
    List<FundValue> values =
        fundValueRepository.findValuesBetweenDatesForKeys(keys, startDate, endDate, MAX_ROWS + 1);
    if (values.size() > MAX_ROWS) {
      throw badRequest(
          "Too many rows: max=" + MAX_ROWS + ", narrow the date range or the key list");
    }
    return values;
  }

  private static ResponseStatusException badRequest(String reason) {
    return new ResponseStatusException(BAD_REQUEST, reason);
  }

  private byte[] toCsv(List<FundValue> values) {
    try (var outputStream = new ByteArrayOutputStream();
        var writer = new OutputStreamWriter(outputStream, UTF_8)) {
      var csvFormat =
          CSVFormat.DEFAULT
              .builder()
              .setHeader("key", "date", "value", "provider", "updatedAt")
              .get();
      try (var printer = new CSVPrinter(writer, csvFormat)) {
        for (var row : values) {
          printer.printRecord(
              row.key(), row.date(), row.value().toPlainString(), row.provider(), row.updatedAt());
        }
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to generate index values CSV", e);
    }
  }
}
