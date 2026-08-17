package ee.tuleva.onboarding.comparisons;

import static java.nio.charset.StandardCharsets.UTF_8;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
class IndexValueController {

  static final int MAX_KEYS = 100;

  private final FundValueRepository fundValueRepository;

  @GetMapping("/index-values")
  @NoCache
  ResponseEntity<byte[]> getIndexValues(
      @RequestParam String keys,
      @RequestParam(defaultValue = "csv") String format,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate) {
    List<String> keyList =
        Arrays.stream(keys.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

    if (keyList.isEmpty()) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "keys must not be empty");
    }
    if (keyList.size() > MAX_KEYS) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Too many keys: max " + MAX_KEYS + " allowed");
    }

    List<FundValue> values;
    if (startDate != null && endDate != null) {
      values = fundValueRepository.findValuesBetweenDatesForKeys(keyList, startDate, endDate);
    } else {
      values = fundValueRepository.findLatestValuesByKeys(keyList);
    }

    byte[] csv = toCsv(values);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"index-values.csv\"")
        .body(csv);
  }

  private byte[] toCsv(List<FundValue> values) {
    try (var outputStream = new ByteArrayOutputStream();
        var writer = new OutputStreamWriter(outputStream, UTF_8)) {
      var csvFormat =
          CSVFormat.DEFAULT.builder().setHeader("key", "date", "value", "provider").get();
      try (var printer = new CSVPrinter(writer, csvFormat)) {
        for (var row : values) {
          printer.printRecord(row.key(), row.date(), row.value().toPlainString(), row.provider());
        }
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate index values CSV", e);
    }
  }
}
