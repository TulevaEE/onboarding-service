package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;

import ee.tuleva.onboarding.party.PartyId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SavingsFundOnboardingRepository {

  private final JdbcClient jdbcClient;

  public boolean isOnboardingCompleted(String code, PartyId.Type type) {
    return findStatus(code, type).filter(status -> status == COMPLETED).isPresent();
  }

  public Optional<SavingsFundOnboardingStatus> findStatus(String code, PartyId.Type type) {
    return jdbcClient
        .sql("SELECT status FROM savings_fund_onboarding WHERE code = :code AND type = :type")
        .param("code", code)
        .param("type", type.name())
        .query(String.class)
        .optional()
        .map(SavingsFundOnboardingStatus::valueOf);
  }

  @Transactional
  public void saveOnboardingStatus(
      String code, PartyId.Type type, SavingsFundOnboardingStatus status) {
    jdbcClient
        .sql("SELECT pg_advisory_xact_lock(:key)")
        .param("key", (long) ("onboarding:" + code).hashCode())
        .query((rs, rowNum) -> 0)
        .optional();

    int updated =
        jdbcClient
            .sql(
                "UPDATE savings_fund_onboarding SET status = :status WHERE code = :code AND type = :type")
            .param("code", code)
            .param("type", type.name())
            .param("status", status.name())
            .update();

    if (updated == 0) {
      jdbcClient
          .sql(
              "INSERT INTO savings_fund_onboarding (code, type, status) VALUES (:code, :type, :status)")
          .param("code", code)
          .param("type", type.name())
          .param("status", status.name())
          .update();
    }
  }
}
