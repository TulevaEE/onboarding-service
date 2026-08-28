package ee.tuleva.onboarding.admin;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalanceSynchronizer;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerSnapshotDateValidator;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerSynchronizer;
import ee.tuleva.onboarding.kyb.KybCheckOverrideService;
import ee.tuleva.onboarding.kyb.KybCheckType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Profile("!staging")
public class AdminController {

  private final AdminTokenValidator tokenValidator;
  private final FundBalanceSynchronizer fundBalanceSynchronizer;
  private final UnitOwnerSynchronizer unitOwnerSynchronizer;
  private final UnitOwnerSnapshotDateValidator unitOwnerSnapshotDateValidator;
  private final KybCheckOverrideService kybCheckOverrideService;
  private final Clock clock;

  @PostMapping("/backfill-unit-counts")
  public String backfillUnitCounts(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate to) {

    validateToken(token);

    log.info("Admin triggered unit count backfill: from={}, to={}", from, to);
    fundBalanceSynchronizer.backfillUnitCounts(from, to);

    return "Backfilled unit counts from " + from + " to " + to;
  }

  @PostMapping("/sync-unit-owners")
  public String syncUnitOwners(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate snapshotDate) {

    validateToken(token);

    LocalDate date = snapshotDate != null ? snapshotDate : LocalDate.now(clock);
    try {
      unitOwnerSnapshotDateValidator.validate(date);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
    }

    log.info("Admin triggered unit owner snapshot sync: snapshotDate={}", date);
    unitOwnerSynchronizer.sync(date);

    return "Synchronized unit owner snapshot for " + date;
  }

  @PostMapping("/override-kyb-check")
  public String overrideKybCheck(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String registryCode,
      @RequestParam KybCheckType checkType,
      @RequestParam String reason,
      @RequestParam(required = false) Instant expiresAt) {

    validateTokenWithOpsAccess(token);
    if (!checkType.isManuallyForceable()) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Check is not manually forceable: " + checkType);
    }
    if (reason.isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "A reason is required");
    }
    if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
      throw new ResponseStatusException(BAD_REQUEST, "Expiry must be in the future: " + expiresAt);
    }
    log.info(
        "Admin overriding KYB check: registryCode={}, checkType={}, reason={}",
        registryCode,
        checkType,
        reason);
    if (expiresAt == null) {
      kybCheckOverrideService.forceSuccess(registryCode, checkType, reason);
    } else {
      kybCheckOverrideService.forceSuccess(registryCode, checkType, reason, expiresAt);
    }

    return "Saved KYB check override: registryCode=" + registryCode + ", checkType=" + checkType;
  }

  private void validateTokenWithOpsAccess(String token) {
    tokenValidator.validateWithOpsAccess(token);
  }

  private void validateToken(String token) {
    tokenValidator.validate(token);
  }
}
