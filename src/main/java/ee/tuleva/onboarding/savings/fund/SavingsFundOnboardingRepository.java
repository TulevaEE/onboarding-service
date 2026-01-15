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

  public boolean isOnboardingCompleted(String personalCode) {
    return findStatusByPersonalCode(personalCode).filter(status -> status == COMPLETED).isPresent();
  }

  public Optional<SavingsFundOnboardingStatus> findStatusByPersonalCode(String personalCode) {
    return jdbcClient
        .sql("SELECT status FROM savings_fund_onboarding WHERE personal_code = :personalCode")
        .param("personalCode", personalCode)
        .query(String.class)
        .optional()
        .map(SavingsFundOnboardingStatus::valueOf);
  }

  public void saveOnboardingStatus(String personalCode, SavingsFundOnboardingStatus status) {
    int updated =
        jdbcClient
            .sql(
                "UPDATE savings_fund_onboarding SET status = :status WHERE personal_code = :personalCode")
            .param("personalCode", personalCode)
            .param("status", status.name())
            .update();

    if (updated == 0) {
      jdbcClient
          .sql(
              "INSERT INTO savings_fund_onboarding (personal_code, status) VALUES (:personalCode, :status)")
          .param("personalCode", personalCode)
          .param("status", status.name())
          .update();
    }
  }
}
