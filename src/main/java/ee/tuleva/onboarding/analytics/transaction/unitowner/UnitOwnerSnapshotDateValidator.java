package ee.tuleva.onboarding.analytics.transaction.unitowner;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnitOwnerSnapshotDateValidator {

  private final UnitOwnerRepository repository;

  public void validate(LocalDate snapshotDate) {
    int maxBackfillDays = 5;
    LocalDate today = LocalDate.now(ClockHolder.clock());

    if (snapshotDate.isAfter(today)) {
      throw new IllegalArgumentException(
          "Cannot record a unit owner snapshot for a future date: snapshotDate=%s, today=%s"
              .formatted(snapshotDate, today));
    }

    if (snapshotDate.isBefore(today.minusDays(maxBackfillDays))) {
      throw new IllegalArgumentException(
          "Cannot record a unit owner snapshot older than %d days, because EPIS returns current"
                  .formatted(maxBackfillDays)
              + " holdings and the snapshot date is only a label: snapshotDate=%s, today=%s"
                  .formatted(snapshotDate, today));
    }

    if (repository.existsBySnapshotDate(snapshotDate)) {
      throw new IllegalArgumentException(
          "Unit owner snapshot already exists: snapshotDate=%s".formatted(snapshotDate));
    }
  }
}
