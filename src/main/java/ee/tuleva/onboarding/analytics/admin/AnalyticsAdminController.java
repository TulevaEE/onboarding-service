package ee.tuleva.onboarding.analytics.admin;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalanceSynchronizer;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerSnapshotDateValidator;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerSynchronizer;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Profile("!staging")
public class AnalyticsAdminController {

  private final AdminTokenValidator tokenValidator;
  private final FundBalanceSynchronizer fundBalanceSynchronizer;
  private final UnitOwnerSynchronizer unitOwnerSynchronizer;
  private final UnitOwnerSnapshotDateValidator unitOwnerSnapshotDateValidator;
  private final Clock clock;

  @PostMapping("/backfill-unit-counts")
  public String backfillUnitCounts(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate to) {

    tokenValidator.validate(token);

    log.info("Admin triggered unit count backfill: from={}, to={}", from, to);
    fundBalanceSynchronizer.backfillUnitCounts(from, to);

    return "Backfilled unit counts from " + from + " to " + to;
  }

  @PostMapping("/sync-unit-owners")
  public String syncUnitOwners(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(required = false) @Nullable @DateTimeFormat(iso = DATE)
          LocalDate snapshotDate) {

    tokenValidator.validate(token);

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
}
