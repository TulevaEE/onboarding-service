package ee.tuleva.onboarding.savings.fund.taxreport;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SavingsFundTaxReportController {

  private static final int MIN_YEAR = 1;
  private static final int MAX_YEAR = 9999;

  private final SavingsFundTaxReportService savingsFundTaxReportService;

  @Operation(summary = "Get realised gains on savings fund redemptions for a tax year")
  @GetMapping("/savings-fund/tax-report")
  public SavingsFundTaxReport getTaxReport(
      @AuthenticationPrincipal AuthenticatedPerson person,
      @RequestParam int year,
      @RequestParam(defaultValue = "WEIGHTED_AVERAGE") CostBasisMethod method) {
    if (year < MIN_YEAR || year > MAX_YEAR) {
      throw new ResponseStatusException(BAD_REQUEST, "Invalid tax report year: year=" + year);
    }
    return savingsFundTaxReportService.getTaxReport(person, year, method);
  }
}
