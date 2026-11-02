package ee.tuleva.onboarding.investment.fees.ocf;

import static java.time.ZoneOffset.UTC;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class OcfExceptionRepository {

  private final JdbcClient jdbcClient;

  void record(OcfSnapshot snapshot, ExceptionDecision decision) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_exception
              (snapshot_id, calculation_fingerprint, reason, decided_by, decided_at)
            VALUES (:snapshotId, :calculationFingerprint, :reason, :decidedBy, :decidedAt)
            """)
        .param("snapshotId", snapshot.storedId())
        .param("calculationFingerprint", OcfCalculationFingerprint.of(snapshot))
        .param("reason", decision.reason())
        .param("decidedBy", decision.decidedBy())
        .param("decidedAt", OffsetDateTime.ofInstant(decision.decidedAt(), UTC))
        .update();
  }

  Optional<ExceptionDecision> findForTheCurrentCalculation(OcfSnapshot snapshot) {
    return jdbcClient
        .sql(
            """
            SELECT decided_by, reason, decided_at FROM investment_ocf_exception
            WHERE snapshot_id = :snapshotId AND calculation_fingerprint = :calculationFingerprint
            """)
        .param("snapshotId", snapshot.storedId())
        .param("calculationFingerprint", OcfCalculationFingerprint.of(snapshot))
        .query(
            (rs, rowNum) ->
                new ExceptionDecision(
                    rs.getString("decided_by"),
                    rs.getString("reason"),
                    rs.getTimestamp("decided_at").toInstant()))
        .optional();
  }
}
