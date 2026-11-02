package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class OcfKidReviewRepository {

  private final JdbcClient jdbcClient;

  void record(OcfSnapshot snapshot, KidFigure kid, KidReview review) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_kid_review
              (fund_code, publication_id, snapshot_id, calculation_fingerprint, reviewed_ocf,
               reason, reviewed_by, reviewed_at)
            VALUES
              (:fundCode, :publicationId, :snapshotId, :calculationFingerprint, :reviewedOcf,
               :reason, :reviewedBy, :reviewedAt)
            """)
        .param("fundCode", snapshot.fundCode())
        .param("publicationId", kid.publicationId())
        .param("snapshotId", snapshot.storedId())
        .param("calculationFingerprint", OcfCalculationFingerprint.of(snapshot))
        .param("reviewedOcf", snapshot.totalOcf())
        .param("reason", review.reason())
        .param("reviewedBy", review.reviewedBy())
        .param("reviewedAt", Timestamp.from(review.reviewedAt()))
        .update();
  }

  Optional<KidReview> findLatestKeeping(KidFigure kid, BigDecimal ocf) {
    return jdbcClient
        .sql(
            """
            SELECT reviewed_ocf, reviewed_by, reason, reviewed_at FROM investment_ocf_kid_review
            WHERE publication_id = :publicationId
            ORDER BY reviewed_at DESC, id DESC
            """)
        .param("publicationId", kid.publicationId())
        .query(
            (rs, rowNum) ->
                new ReviewedFigure(
                    rs.getBigDecimal("reviewed_ocf"),
                    new KidReview(
                        rs.getString("reviewed_by"),
                        rs.getString("reason"),
                        rs.getTimestamp("reviewed_at").toInstant())))
        .stream()
        .filter(reviewed -> !kid.precision().differ(reviewed.ocf(), ocf))
        .map(ReviewedFigure::review)
        .findFirst();
  }

  private record ReviewedFigure(BigDecimal ocf, KidReview review) {}
}
