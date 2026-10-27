package ee.tuleva.onboarding.user;

import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record SecondPillarContributionPeriod(
    Status status, LocalDate startDate, @Nullable LocalDate endDate) {

  public enum Status {
    CONTRIBUTING,
    CONTRIBUTING_WITHOUT_ACCOUNT,
    NOT_CONTRIBUTING,
    PAUSED;

    boolean makesContributions() {
      return switch (this) {
        case CONTRIBUTING, CONTRIBUTING_WITHOUT_ACCOUNT -> true;
        case NOT_CONTRIBUTING, PAUSED -> false;
      };
    }
  }

  boolean makesContributions() {
    return status.makesContributions();
  }

  boolean overlaysContributions() {
    return !makesContributions();
  }

  boolean stopsContributions() {
    return status == Status.NOT_CONTRIBUTING;
  }

  boolean hasStartedBy(LocalDate day) {
    return !startDate.isAfter(day);
  }

  boolean isInEffectOn(LocalDate day) {
    return hasStartedBy(day) && (endDate == null || !endDate.isBefore(day));
  }

  boolean startsContributingAfter(LocalDate day) {
    return !hasStartedBy(day) && makesContributions();
  }

  Optional<LocalDate> lastContributionDayBefore(LocalDate day) {
    return Optional.ofNullable(endDate).filter(end -> makesContributions() && end.isBefore(day));
  }
}
