package ee.tuleva.onboarding.kyc;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class KycCheckRepository implements KycChecker {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcClient jdbcClient;
  private final JsonMapper objectMapper;

  @Override
  public KycCheck check(Long userId) {
    return jdbcClient
        .sql("SELECT risk_level, metadata::text FROM kyc_ob_assess_user_risk(:userId::integer)")
        .param("userId", userId)
        .query(
            (rs, rowNum) -> {
              var riskLevel = KycCheck.RiskLevel.valueOf(rs.getString("risk_level"));
              var metadata = parseJsonb(rs.getString("metadata"));
              return new KycCheck(riskLevel, metadata);
            })
        .single();
  }

  private Map<String, Object> parseJsonb(String json) {
    if (json == null || json.isEmpty()) {
      return Map.of();
    }
    return objectMapper.readValue(json, MAP_TYPE);
  }
}
