package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SavingsFundOnboardingRepository {

  private final JdbcClient jdbcClient;

  public boolean isOnboardingCompleted(Long userId) {
    return findStatusByUserId(userId).filter(status -> status == COMPLETED).isPresent();
  }

  public Optional<SavingsFundOnboardingStatus> findStatusByUserId(Long userId) {
    return jdbcClient
        .sql("SELECT status FROM savings_fund_onboarding WHERE user_id = :userId")
        .param("userId", userId)
        .query(String.class)
        .optional()
        .map(SavingsFundOnboardingStatus::valueOf);
  }

  public void saveOnboardingStatus(Long userId, SavingsFundOnboardingStatus status) {
    int updated =
        jdbcClient
            .sql("UPDATE savings_fund_onboarding SET status = :status WHERE user_id = :userId")
            .param("userId", userId)
            .param("status", status.name())
            .update();

    if (updated == 0) {
      jdbcClient
          .sql("INSERT INTO savings_fund_onboarding (user_id, status) VALUES (:userId, :status)")
          .param("userId", userId)
          .param("status", status.name())
          .update();
    }
  }
}
