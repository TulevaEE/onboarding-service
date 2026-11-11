package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.user.User;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
@Profile({"!dev & !staging"})
@ConditionalOnProperty(
    prefix = "aml-service",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class KycCheckRepository implements KycChecker {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcClient jdbcClient;
  private final JsonMapper objectMapper;

  @Override
  public KycCheck check(User user) {
    Long userId = user.getIdOrThrow();
    return jdbcClient
        .sql("SELECT risk_level, metadata::text FROM kyc_ob_assess_user_risk(:userId::integer)")
        .param("userId", userId)
        .query(
            (rs, rowNum) ->
                toKycCheck(rs.getString("risk_level"), rs.getString("metadata"), userId))
        .single();
  }

  KycCheck toKycCheck(String riskLevel, String metadata, Long userId) {
    if (riskLevel == null) {
      // The assessment function returns a composite NULL when it finds no
      // kyc_survey for the user — surface that clearly instead of an NPE.
      throw new IllegalStateException("KYC risk assessment returned no result: userId=" + userId);
    }
    return new KycCheck(KycCheck.RiskLevel.valueOf(riskLevel), parseJsonb(metadata));
  }

  private Map<String, Object> parseJsonb(String json) {
    if (json == null || json.isEmpty()) {
      return Map.of();
    }
    return objectMapper.readValue(json, MAP_TYPE);
  }
}
