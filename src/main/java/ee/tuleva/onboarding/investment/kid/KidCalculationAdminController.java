package ee.tuleva.onboarding.investment.kid;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/kid")
@RequiredArgsConstructor
@Profile("!staging")
class KidCalculationAdminController {

  private final AdminTokenValidator tokenValidator;
  private final KidCalculationService kidCalculationService;
  private final KidCalculationReader kidCalculationReader;

  @GetMapping("/{fund}/calculations")
  List<KidMonthCalculation> calculations(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable TulevaFund fund,
      @RequestParam Optional<YearMonth> from,
      @RequestParam Optional<YearMonth> through) {
    tokenValidator.validateWithOpsAccess(token);
    try {
      return kidCalculationReader
          .read(fund, from, through)
          .orElseThrow(
              () -> new ResponseStatusException(NOT_FOUND, "No KID for the fund: fund=" + fund));
    } catch (InvalidKidRequestException invalid) {
      throw new ResponseStatusException(BAD_REQUEST, invalid.getMessage(), invalid);
    }
  }

  @PostMapping("/{fund}/calculations")
  StoredKidCalculation calculate(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable TulevaFund fund,
      @RequestParam YearMonth month) {
    tokenValidator.validateWithOpsAccess(token);
    try {
      return kidCalculationService.calculateAndStore(fund, month);
    } catch (InvalidKidRequestException invalid) {
      throw new ResponseStatusException(BAD_REQUEST, invalid.getMessage(), invalid);
    } catch (DuplicateKeyException calculatedAtTheSameTime) {
      throw new ResponseStatusException(
          CONFLICT,
          "Another calculation of the month took the same version, try again: fund=%s, month=%s"
              .formatted(fund, month),
          calculatedAtTheSameTime);
    }
  }
}
