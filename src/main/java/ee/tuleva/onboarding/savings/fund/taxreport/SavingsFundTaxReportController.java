package ee.tuleva.onboarding.savings.fund.taxreport;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SavingsFundTaxReportController {

  private final SavingsFundTaxReportService savingsFundTaxReportService;

  @Operation(summary = "Get realised gains on savings fund redemptions for a tax year")
  @GetMapping("/savings-fund/tax-report")
  public SavingsFundTaxReport getTaxReport(
      @AuthenticationPrincipal AuthenticatedPerson person,
      @RequestParam int year,
      @RequestParam(defaultValue = "WEIGHTED_AVERAGE") CostBasisMethod method) {
    return savingsFundTaxReportService.getTaxReport(person, year, method);
  }
}
