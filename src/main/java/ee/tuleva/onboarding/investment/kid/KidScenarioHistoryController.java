package ee.tuleva.onboarding.investment.kid;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.config.http.CsvDownload;
import ee.tuleva.onboarding.config.http.NoCache;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/funds")
@RequiredArgsConstructor
class KidScenarioHistoryController {

  private final KidPublicationService kidPublicationService;

  @Operation(summary = "Get the monthly history of a fund's KID performance scenarios")
  @GetMapping("/{isin}/kid-scenarios")
  @NoCache
  ResponseEntity<?> scenarioHistory(
      @PathVariable String isin, @RequestParam(required = false) @Nullable String format) {
    var history =
        kidPublicationService
            .publishedScenarioHistory(isin)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    if ("csv".equalsIgnoreCase(format)) {
      return CsvDownload.attachment(
          "kid-scenarios", isin, new KidScenarioHistoryCsv(history).toBytes());
    }
    return ResponseEntity.ok(history);
  }
}
