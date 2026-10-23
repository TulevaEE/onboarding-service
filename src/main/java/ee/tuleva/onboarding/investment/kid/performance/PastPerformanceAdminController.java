package ee.tuleva.onboarding.investment.kid.performance;

import static org.springframework.http.HttpStatus.CONFLICT;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/kid")
@RequiredArgsConstructor
@Profile("!staging")
class PastPerformanceAdminController {

  private final AdminTokenValidator tokenValidator;
  private final PastPerformanceService pastPerformanceService;

  @GetMapping("/{fund}/past-performance")
  PastPerformance pastPerformance(
      @RequestHeader("X-Admin-Token") String token, @PathVariable TulevaFund fund) {
    tokenValidator.validateWithOpsAccess(token);
    try {
      return pastPerformanceService.pastPerformance(fund);
    } catch (PastPerformanceRefusedException refusal) {
      throw new ResponseStatusException(CONFLICT, refusal.getMessage(), refusal);
    }
  }
}
