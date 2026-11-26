package ee.tuleva.onboarding.savings.fund.admin;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseApproval;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseEmailFailedException;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseRefusedException;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseService;
import ee.tuleva.onboarding.savings.fund.nav.NavReleaseStatus;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin/nav")
@RequiredArgsConstructor
@Profile("!staging")
public class NavReleaseAdminController {

  private final AdminTokenValidator tokenValidator;
  private final NavReleaseService navReleaseService;

  @GetMapping("/{fund}/{navDate}")
  public NavReleaseStatus status(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable TulevaFund fund,
      @PathVariable @DateTimeFormat(iso = DATE) LocalDate navDate) {
    tokenValidator.validateWithOpsAccess(token);
    return answer(() -> navReleaseService.status(fund, navDate));
  }

  @PostMapping("/{fund}/{navDate}/release")
  public NavReleaseStatus release(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable TulevaFund fund,
      @PathVariable @DateTimeFormat(iso = DATE) LocalDate navDate,
      @RequestBody NavReleaseApproval approval) {
    tokenValidator.validateWithOpsAccess(token);
    log.info(
        "Admin releasing NAV: fund={}, navDate={}, calculationId={}, approvedBy={}",
        fund,
        navDate,
        approval.calculationId(),
        approval.approvedBy());
    return answer(() -> navReleaseService.release(fund, navDate, approval));
  }

  private static NavReleaseStatus answer(Supplier<NavReleaseStatus> action) {
    try {
      return action.get();
    } catch (NoSuchElementException e) {
      throw refused(NOT_FOUND, e);
    } catch (NavReleaseRefusedException e) {
      throw refused(CONFLICT, e);
    } catch (NavReleaseEmailFailedException e) {
      throw refused(BAD_GATEWAY, e);
    }
  }

  private static ResponseStatusException refused(HttpStatus status, RuntimeException e) {
    log.info("Admin NAV release refused: status={}, error={}", status.value(), e.getMessage());
    return new ResponseStatusException(status, e.getMessage(), e);
  }
}
