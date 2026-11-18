package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

record OnboardingStatusChanges(JdbcClient jdbcClient) {

  record Change(
      @Nullable SavingsFundOnboardingStatus oldStatus,
      SavingsFundOnboardingStatus newStatus,
      OnboardingStatusChangeReason reason,
      Instant changedAt) {}

  List<Change> of(String code, PartyId.Type type) {
    return jdbcClient
        .sql(
            """
            SELECT old_status, new_status, reason, changed_at
            FROM savings_fund_onboarding_status_change
            WHERE code = :code AND type = :type
            ORDER BY id
            """)
        .param("code", code)
        .param("type", type.name())
        .query(
            (rs, rowNum) ->
                new Change(
                    rs.getString("old_status") == null
                        ? null
                        : SavingsFundOnboardingStatus.valueOf(rs.getString("old_status")),
                    SavingsFundOnboardingStatus.valueOf(rs.getString("new_status")),
                    OnboardingStatusChangeReason.valueOf(rs.getString("reason")),
                    rs.getTimestamp("changed_at").toInstant()))
        .list();
  }
}
