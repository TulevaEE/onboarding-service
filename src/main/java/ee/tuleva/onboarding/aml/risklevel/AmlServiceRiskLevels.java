package ee.tuleva.onboarding.aml.risklevel;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AmlServiceRiskLevels {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final AmlServiceClient amlServiceClient;
  private final Clock clock;

  AssessedRiskLevels fetchThirdPillar() {
    return fetch("third-pillar");
  }

  AssessedRiskLevels fetchTkf() {
    return fetch("tkf");
  }

  private AssessedRiskLevels fetch(String family) {
    log.info("Fetching risk levels from aml-service: family={}", family);
    AssessedRiskLevels riskLevels = amlServiceClient.riskLevels(family);
    requireAssessedToday(family, riskLevels.assessedAt());
    return riskLevels;
  }

  private void requireAssessedToday(String family, Instant assessedAt) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), TALLINN);
    LocalDate assessedOn = LocalDate.ofInstant(assessedAt, TALLINN);
    if (!assessedOn.equals(today)) {
      throw new RiskLevelsRefusedException(
          "Refusing risk levels not assessed on the job's Tallinn date: family="
              + family
              + ", assessedAt="
              + assessedAt
              + ", today="
              + today);
    }
  }
}
