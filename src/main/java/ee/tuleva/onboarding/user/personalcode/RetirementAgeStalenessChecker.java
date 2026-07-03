package ee.tuleva.onboarding.user.personalcode;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@NullMarked
public class RetirementAgeStalenessChecker {

  private final Clock estonianClock;

  @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Tallinn")
  public void checkEstablishedRetirementAges() {
    if (isRetirementAgeTableStale()) {
      log.error(
          "Established retirement age missing: lastEstablishedYear={}, requiredThroughYear={}",
          PersonalCode.lastEstablishedRetirementAgeYear(),
          requiredThroughYear());
    }
  }

  public boolean isRetirementAgeTableStale() {
    return PersonalCode.lastEstablishedRetirementAgeYear() < requiredThroughYear();
  }

  private int requiredThroughYear() {
    return LocalDate.now(estonianClock).getYear() + 2;
  }
}
