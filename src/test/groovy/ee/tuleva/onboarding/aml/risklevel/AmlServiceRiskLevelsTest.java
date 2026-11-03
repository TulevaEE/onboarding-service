package ee.tuleva.onboarding.aml.risklevel;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class AmlServiceRiskLevelsTest {

  private static final Clock AT_THE_JOBS_TIME =
      Clock.fixed(Instant.parse("2026-10-28T03:30:00Z"), UTC);

  private final AmlServiceClient amlServiceClient = mock(AmlServiceClient.class);

  @Test
  void givesTodaysThirdPillarRun() {
    AssessedRiskLevels todaysRun = run(Instant.parse("2026-10-28T02:45:00Z"));
    given(amlServiceClient.riskLevels("third-pillar")).willReturn(todaysRun);

    assertThat(riskLevelsAt(AT_THE_JOBS_TIME).fetchThirdPillar()).isEqualTo(todaysRun);
  }

  @Test
  void givesTodaysTkfRun() {
    AssessedRiskLevels todaysRun = run(Instant.parse("2026-10-28T02:15:00Z"));
    given(amlServiceClient.riskLevels("tkf")).willReturn(todaysRun);

    assertThat(riskLevelsAt(AT_THE_JOBS_TIME).fetchTkf()).isEqualTo(todaysRun);
  }

  @Test
  void refusesAnotherDaysRun() {
    given(amlServiceClient.riskLevels("third-pillar"))
        .willReturn(run(Instant.parse("2026-10-27T02:45:00Z")));

    assertThatThrownBy(riskLevelsAt(AT_THE_JOBS_TIME)::fetchThirdPillar)
        .isInstanceOf(RiskLevelsRefusedException.class);
  }

  @Test
  void refusesYesterdaysTallinnRunAlthoughItIsOfTheSameUtcDate() {
    given(amlServiceClient.riskLevels("tkf"))
        .willReturn(run(Instant.parse("2026-10-27T02:15:00Z")));

    assertThatThrownBy(
            riskLevelsAt(Clock.fixed(Instant.parse("2026-10-27T22:30:00Z"), UTC))::fetchTkf)
        .isInstanceOf(RiskLevelsRefusedException.class);
  }

  @Test
  void acceptsTodaysTallinnRunAlthoughItIsOfTheUtcDateBefore() {
    AssessedRiskLevels justAfterTallinnMidnight = run(Instant.parse("2026-10-27T22:30:00Z"));
    given(amlServiceClient.riskLevels("tkf")).willReturn(justAfterTallinnMidnight);

    assertThat(riskLevelsAt(AT_THE_JOBS_TIME).fetchTkf()).isEqualTo(justAfterTallinnMidnight);
  }

  @Test
  void passesOnAnAmlServiceThatDoesNotAnswer() {
    willThrow(new ResourceAccessException("request timed out"))
        .given(amlServiceClient)
        .riskLevels("tkf");

    assertThatThrownBy(riskLevelsAt(AT_THE_JOBS_TIME)::fetchTkf)
        .isInstanceOf(ResourceAccessException.class);
  }

  private AmlServiceRiskLevels riskLevelsAt(Clock clock) {
    return new AmlServiceRiskLevels(amlServiceClient, clock);
  }

  private static AssessedRiskLevels run(Instant assessedAt) {
    return new AssessedRiskLevels(
        assessedAt, List.of(new RiskLevelResult("38501010001", 1, Map.of("level", 1))));
  }
}
