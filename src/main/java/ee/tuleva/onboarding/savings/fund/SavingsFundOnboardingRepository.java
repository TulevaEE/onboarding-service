package ee.tuleva.onboarding.savings.fund;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SavingsFundOnboardingRepository {

  private final JdbcClient jdbcClient;

  public boolean isOnboardingCompleted(Long userId) {
    return jdbcClient
        .sql("SELECT 1 FROM savings_fund_onboarding WHERE user_id = :userId")
        .param("userId", userId)
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  public void completeOnboarding(Long userId) {
    jdbcClient
        .sql("INSERT INTO savings_fund_onboarding (user_id) VALUES (:userId)")
        .param("userId", userId)
        .update();
  }
}
