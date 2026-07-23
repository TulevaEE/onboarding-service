package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;

import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.party.PartyId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SavingsFundOnboardingRepository {

  private final JdbcClient jdbcClient;

  public List<String> findPersonCodes() {
    return jdbcClient
        .sql("SELECT code FROM savings_fund_onboarding WHERE type = 'PERSON'")
        .query(String.class)
        .list();
  }

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

  public List<String> findCompletedLegalEntitiesWithFailedOwnershipChecksSince(Instant since) {
    return jdbcClient
        .sql(
            """
            SELECT o.code
            FROM savings_fund_onboarding o
            WHERE o.type = 'LEGAL_ENTITY'
              AND o.status = 'COMPLETED'
              AND EXISTS (
                SELECT 1
                FROM aml_check ac
                JOIN company c ON ac.company_id = c.id
                WHERE c.registry_code = o.code
                  AND ac.type IN (:ownershipCheckTypes)
                  AND ac.success = false
                  AND ac.created_time >= :since
              )
            ORDER BY o.code
            """)
        .param("ownershipCheckTypes", ownershipCheckTypeNames())
        .param("since", Timestamp.from(since))
        .query(String.class)
        .list();
  }

  private static List<String> ownershipCheckTypeNames() {
    return Arrays.stream(KybCheckType.values())
        .filter(KybCheckType::isOwnershipCheck)
        .map(type -> "KYB_" + type.name())
        .toList();
  }

  @Transactional
  public boolean insertOnboardingStatusIfAbsent(
      String code, PartyId.Type type, SavingsFundOnboardingStatus status) {
    return jdbcClient
            .sql(
                """
                INSERT INTO savings_fund_onboarding (code, type, status)
                VALUES (:code, :type, :status)
                ON CONFLICT DO NOTHING
                """)
            .param("code", code)
            .param("type", type.name())
            .param("status", status.name())
            .update()
        > 0;
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
