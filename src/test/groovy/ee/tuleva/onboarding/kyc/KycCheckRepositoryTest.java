package ee.tuleva.onboarding.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

class KycCheckRepositoryTest {

  private final KycCheckRepository repository =
      new KycCheckRepository(
          JdbcClient.create(new DriverManagerDataSource()), JsonMapper.builder().build());

  @Test
  void toKycCheck_mapsRiskLevelAndMetadata() {
    var check = repository.toKycCheck("LOW", "{\"score\": 10}", 1L);

    assertThat(check).isEqualTo(new KycCheck(KycCheck.RiskLevel.LOW, Map.of("score", 10)));
  }

  @Test
  void toKycCheck_failsClearlyWhenAssessmentReturnsNoResult() {
    assertThatThrownBy(() -> repository.toKycCheck(null, null, 1L))
        .isInstanceOf(IllegalStateException.class);
  }
}
