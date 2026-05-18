package ee.tuleva.onboarding.investment.transaction.portfolio;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Accepts a baseline CSV with the following columns (header row required):
 *
 * <pre>fund_isin,instrument_isin,quantity,avg_unit_cost,baseline_date</pre>
 *
 * One row per (fund, instrument); baseline_date must be identical within a fund. Reloading replaces
 * the existing baseline for the fund.
 */
@Slf4j
@RestController
@RequestMapping("/v1/portfolio/baseline")
@Profile("admin")
@RequiredArgsConstructor
class PortfolioBaselineImportController {

  private final PortfolioBaselineImporter importer;

  @PostMapping
  ResponseEntity<Map<String, Object>> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "loadedBy", required = false) String loadedBy)
      throws IOException {
    String by = loadedBy == null || loadedBy.isBlank() ? "admin-api" : loadedBy;
    List<PortfolioBaseline> saved = importer.importCsv(file.getInputStream(), by);
    return ResponseEntity.ok(
        Map.of(
            "importedFunds",
            saved.size(),
            "fundIsins",
            saved.stream().map(PortfolioBaseline::getFundIsin).toList()));
  }
}
