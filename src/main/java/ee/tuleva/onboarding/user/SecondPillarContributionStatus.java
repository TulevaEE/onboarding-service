package ee.tuleva.onboarding.user;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record SecondPillarContributionStatus(
    boolean contributing, @Nullable LocalDate restartDate, @Nullable LocalDate rejoinPossibleFrom) {

  boolean mayRejoinOn(LocalDate day) {
    return !contributing
        && restartDate == null
        && rejoinPossibleFrom != null
        && !rejoinPossibleFrom.isAfter(day);
  }
}
