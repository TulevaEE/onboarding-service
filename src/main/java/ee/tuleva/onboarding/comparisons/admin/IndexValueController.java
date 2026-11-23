package ee.tuleva.onboarding.comparisons.admin;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.config.http.NoCache;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
class IndexValueController {

  private static final MediaType TEXT_CSV_UTF8 = MediaType.parseMediaType("text/csv;charset=UTF-8");

  private final IndexValueExport indexValueExport;
  private final AdminTokenValidator tokenValidator;

  @GetMapping(
      value = "/index-values",
      params = {"!startDate", "!endDate"})
  @NoCache
  ResponseEntity<byte[]> getLatestIndexValues(
      @RequestHeader("X-Admin-Token") String token, @RequestParam String keys) {
    tokenValidator.validateReadAccess(token);
    return csv(indexValueExport.latest(keys));
  }

  @GetMapping(
      value = "/index-values",
      params = {"startDate", "endDate"})
  @NoCache
  ResponseEntity<byte[]> getIndexValuesBetween(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String keys,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate endDate) {
    tokenValidator.validateReadAccess(token);
    return csv(indexValueExport.between(keys, startDate, endDate));
  }

  private static ResponseEntity<byte[]> csv(byte[] body) {
    return ResponseEntity.ok()
        .contentType(TEXT_CSV_UTF8)
        .header(CONTENT_DISPOSITION, "attachment; filename=\"index-values.csv\"")
        .body(body);
  }
}
