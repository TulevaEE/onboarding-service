package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.ON_HOLD;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;

import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SavingsFundOnboardingRepository {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  public List<String> findPersonCodes() {
    return jdbcClient
        .sql("SELECT code FROM savings_fund_onboarding WHERE type = 'PERSON'")
        .query(String.class)
        .list()
        .stream()
        .map(Objects::requireNonNull)
        .toList();
  }

  public List<String> findPersonCodes(SavingsFundOnboardingStatus status, Instant changedBefore) {
    return findCodes(PartyId.Type.PERSON, status, changedBefore);
  }

  private List<String> findCodes(
      PartyId.Type type, SavingsFundOnboardingStatus status, Instant changedBefore) {
    return jdbcClient
        .sql(
            """
            SELECT code
            FROM savings_fund_onboarding
            WHERE type = :type AND status = :status AND updated_at < :changedBefore
            ORDER BY code
            """)
        .param("type", type.name())
        .param("status", status.name())
        .param("changedBefore", Timestamp.from(changedBefore))
        .query(String.class)
        .list()
        .stream()
        .map(Objects::requireNonNull)
        .toList();
  }

  @Transactional
  public boolean changeStatus(
      String code,
      PartyId.Type type,
      SavingsFundOnboardingStatus expected,
      SavingsFundOnboardingStatus status,
      OnboardingStatusChangeReason reason) {
    Instant changedAt = clock.instant();
    boolean changed =
        jdbcClient
                .sql(
                    """
                    UPDATE savings_fund_onboarding
                    SET status = :status, updated_at = :updatedAt
                    WHERE code = :code AND type = :type AND status = :expected
                    """)
                .param("code", code)
                .param("type", type.name())
                .param("expected", expected.name())
                .param("status", status.name())
                .param("updatedAt", Timestamp.from(changedAt))
                .update()
            > 0;
    if (changed) {
      recordStatusChange(code, type, Optional.of(expected), status, reason, changedAt);
    }
    return changed;
  }

  @Transactional
  public boolean holdRejected(
      String code, PartyId.Type type, Instant rejectedBefore, OnboardingStatusChangeReason reason) {
    Instant changedAt = clock.instant();
    boolean held =
        jdbcClient
                .sql(
                    """
                    UPDATE savings_fund_onboarding
                    SET status = :status, updated_at = :updatedAt
                    WHERE code = :code AND type = :type AND status = :expected
                      AND updated_at < :rejectedBefore
                    """)
                .param("code", code)
                .param("type", type.name())
                .param("expected", REJECTED.name())
                .param("status", ON_HOLD.name())
                .param("updatedAt", Timestamp.from(changedAt))
                .param("rejectedBefore", Timestamp.from(rejectedBefore))
                .update()
            > 0;
    if (held) {
      recordStatusChange(code, type, Optional.of(REJECTED), ON_HOLD, reason, changedAt);
    }
    return held;
  }

  public List<String> findPendingLegalEntityCodes() {
    return jdbcClient
        .sql(
            """
            SELECT code
            FROM savings_fund_onboarding
            WHERE type = 'LEGAL_ENTITY' AND status = 'PENDING'
            ORDER BY code
            """)
        .query(String.class)
        .list()
        .stream()
        .map(Objects::requireNonNull)
        .toList();
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

  private Optional<SavingsFundOnboardingStatus> findStatusForUpdate(
      String code, PartyId.Type type) {
    return jdbcClient
        .sql(
            "SELECT status FROM savings_fund_onboarding WHERE code = :code AND type = :type"
                + " FOR UPDATE")
        .param("code", code)
        .param("type", type.name())
        .query(String.class)
        .optional()
        .map(SavingsFundOnboardingStatus::valueOf);
  }

  public List<String> findCompletedLegalEntitiesWithFailedChecksSince(
      Instant since, Collection<KybCheckType> checkTypes) {
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
                  AND ac.type IN (:checkTypes)
                  AND ac.success = false
                  AND ac.created_time >= :since
              )
            ORDER BY o.code
            """)
        .param("checkTypes", amlCheckTypeNames(checkTypes))
        .param("since", Timestamp.from(since))
        .query(String.class)
        .list()
        .stream()
        .map(Objects::requireNonNull)
        .toList();
  }

  private static List<String> amlCheckTypeNames(Collection<KybCheckType> checkTypes) {
    return checkTypes.stream().map(type -> "KYB_" + type.name()).toList();
  }

  @Transactional
  public boolean insertOnboardingStatusIfAbsent(
      String code,
      PartyId.Type type,
      SavingsFundOnboardingStatus status,
      OnboardingStatusChangeReason reason) {
    Instant changedAt = clock.instant();
    boolean inserted =
        jdbcClient
                .sql(
                    """
                    INSERT INTO savings_fund_onboarding (code, type, status, updated_at)
                    VALUES (:code, :type, :status, :updatedAt)
                    ON CONFLICT DO NOTHING
                    """)
                .param("code", code)
                .param("type", type.name())
                .param("status", status.name())
                .param("updatedAt", Timestamp.from(changedAt))
                .update()
            > 0;
    if (inserted) {
      recordStatusChange(code, type, Optional.empty(), status, reason, changedAt);
    }
    return inserted;
  }

  @Transactional
  public Optional<SavingsFundOnboardingStatus> saveOnboardingStatus(
      String code,
      PartyId.Type type,
      SavingsFundOnboardingStatus status,
      OnboardingStatusChangeReason reason) {
    jdbcClient
        .sql("SELECT pg_advisory_xact_lock(:key)")
        .param("key", (long) ("onboarding:" + code).hashCode())
        .query((rs, rowNum) -> 0)
        .optional();

    var previousStatus = findStatusForUpdate(code, type);
    Instant changedAt = clock.instant();

    int updated =
        jdbcClient
            .sql(
                """
                UPDATE savings_fund_onboarding
                SET status = :status, updated_at = :updatedAt
                WHERE code = :code AND type = :type
                """)
            .param("code", code)
            .param("type", type.name())
            .param("status", status.name())
            .param("updatedAt", Timestamp.from(changedAt))
            .update();

    if (updated == 0) {
      jdbcClient
          .sql(
              """
              INSERT INTO savings_fund_onboarding (code, type, status, updated_at)
              VALUES (:code, :type, :status, :updatedAt)
              """)
          .param("code", code)
          .param("type", type.name())
          .param("status", status.name())
          .param("updatedAt", Timestamp.from(changedAt))
          .update();
    }

    if (previousStatus.filter(status::equals).isEmpty()) {
      recordStatusChange(code, type, previousStatus, status, reason, changedAt);
    }

    return previousStatus;
  }

  private void recordStatusChange(
      String code,
      PartyId.Type type,
      Optional<SavingsFundOnboardingStatus> oldStatus,
      SavingsFundOnboardingStatus newStatus,
      OnboardingStatusChangeReason reason,
      Instant changedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO savings_fund_onboarding_status_change
              (code, type, old_status, new_status, reason, changed_at)
            VALUES (:code, :type, :oldStatus, :newStatus, :reason, :changedAt)
            """)
        .param("code", code)
        .param("type", type.name())
        .param("oldStatus", oldStatus.map(Enum::name).orElse(null), Types.VARCHAR)
        .param("newStatus", newStatus.name())
        .param("reason", reason.name())
        .param("changedAt", Timestamp.from(changedAt))
        .update();
  }
}
