package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.config.http.NoCache;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class FundController {

  private final FundService fundService;

  @Operation(summary = "Get info about available funds")
  @GetMapping("/funds")
  public List<ExtendedApiFundResponse> get(
      @RequestParam("fundManager.name") Optional<String> fundManagerName) {
    return fundService.getFunds(fundManagerName);
  }

  @Operation(summary = "Get NAV history for a fund")
  @GetMapping("/funds/{isin}/nav")
  @NoCache
  public ResponseEntity<?> getNavHistory(
      @PathVariable String isin,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate,
      @RequestParam(required = false) String format) {
    if ("csv".equalsIgnoreCase(format)) {
      byte[] csv = fundService.getNavHistoryCsv(isin, startDate, endDate);
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"nav-" + isin.replaceAll("[^A-Za-z0-9]", "") + ".csv\"")
          .body(csv);
    }
    return ResponseEntity.ok(fundService.getNavHistory(isin, startDate, endDate));
  }
}
