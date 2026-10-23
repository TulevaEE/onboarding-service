package ee.tuleva.onboarding.investment.kid;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/kid")
@RequiredArgsConstructor
@Profile("!staging")
class KidAdminController {

  private static final BigDecimal ONGOING_CHARGES_FROM_WHICH_A_PERCENT_WAS_TYPED_AS_A_FRACTION =
      new BigDecimal("0.05");

  private final AdminTokenValidator tokenValidator;
  private final KidFiguresService kidFiguresService;

  @GetMapping("/{fund}")
  KidFigures figures(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable TulevaFund fund,
      @RequestParam BigDecimal ongoingCharges) {
    tokenValidator.validateWithOpsAccess(token);
    if (ongoingCharges.signum() < 0
        || ongoingCharges.compareTo(ONGOING_CHARGES_FROM_WHICH_A_PERCENT_WAS_TYPED_AS_A_FRACTION)
            >= 0) {
      throw new ResponseStatusException(
          BAD_REQUEST,
          "Ongoing charges must be a fraction per year below 0.05: ongoingCharges="
              + ongoingCharges);
    }
    try {
      return kidFiguresService.figures(fund, ongoingCharges);
    } catch (KidCalculationRefusedException refusal) {
      throw new ResponseStatusException(CONFLICT, refusal.getMessage(), refusal);
    }
  }
}
