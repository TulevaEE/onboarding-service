package ee.tuleva.onboarding.personalcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RetirementAgeStalenessCheckerTest {

  @Test
  void tableIsFreshWhileEstablishedAgesCoverTwoYearsAhead() {
    int lastEstablishedYear = PersonalCode.lastEstablishedRetirementAgeYear();
    var freshChecker = new RetirementAgeStalenessChecker(clockInYear(lastEstablishedYear - 2));
    var staleChecker = new RetirementAgeStalenessChecker(clockInYear(lastEstablishedYear - 1));

    assertThat(freshChecker.isRetirementAgeTableStale()).isFalse();
    assertThat(staleChecker.isRetirementAgeTableStale()).isTrue();
  }

  private static Clock clockInYear(int year) {
    return Clock.fixed(Instant.parse(year + "-06-01T12:00:00Z"), ZoneId.of("Europe/Tallinn"));
  }
}
