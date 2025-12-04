package ee.tuleva.onboarding.kyc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class KycCheckRepository implements KycChecker {

  private final JdbcClient jdbcClient;

  @Override
  public KycCheck check(Long userId) {
    return jdbcClient
        // User ID in database layer is SERIAL (int), on Java layer Long
        .sql("SELECT * FROM kyc_ob_assess_user_risk(:userId::integer)")
        .param("userId", userId)
        .query(KycCheck.class)
        .single();
  }
}
