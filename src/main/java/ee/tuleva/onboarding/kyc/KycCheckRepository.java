package ee.tuleva.onboarding.kyc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class KycCheckRepository implements KycChecker {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

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
    try {
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse jsonb metadata", e);
    }
  }
}
