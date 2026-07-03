package ee.tuleva.onboarding.user.personalcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RetirementAgeStalenessCheckerTest {

  @Test
  void tableIsFreshWhileEstablishedAgesCoverTwoYearsAhead() {
    var lastCoveredYear = PersonalCode.lastEstablishedRetirementAgeYear() - 2;
    var checker = new RetirementAgeStalenessChecker(clockAtStartOf(lastCoveredYear));

    assertThat(checker.isRetirementAgeTableStale()).isFalse();
  }

  @Test
  void tableIsStaleWhenAYearThatShouldBeEstablishedIsMissing() {
    var firstUncoveredYear = PersonalCode.lastEstablishedRetirementAgeYear() - 1;
    var checker = new RetirementAgeStalenessChecker(clockAtStartOf(firstUncoveredYear));

    assertThat(checker.isRetirementAgeTableStale()).isTrue();
  }

  private Clock clockAtStartOf(int year) {
    return Clock.fixed(Instant.parse(year + "-01-01T10:00:00Z"), ZoneId.of("Europe/Tallinn"));
  }
}
