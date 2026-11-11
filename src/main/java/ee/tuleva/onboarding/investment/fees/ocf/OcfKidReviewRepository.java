package ee.tuleva.onboarding.investment.fees.ocf;

import static java.time.ZoneOffset.UTC;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class OcfKidReviewRepository {

  private final JdbcClient jdbcClient;

  boolean recordUnlessPublishedOrRecalculated(
      OcfSnapshot snapshot, KidFigure kid, KidReview review) {
    return jdbcClient
            .sql(
                """
                INSERT INTO investment_ocf_kid_review
                  (fund_code, publication_id, snapshot_id, calculation_fingerprint, reviewed_ocf,
                   reason, reviewed_by, reviewed_at)
                SELECT reviewed.fund_code, :publicationId, reviewed.id, :calculationFingerprint,
                       :reviewedOcf, :reason, :reviewedBy, :reviewedAt
                FROM investment_ocf_snapshot reviewed
                WHERE reviewed.id = :snapshotId
                  AND reviewed.total_ocf = :reviewedOcf
                  AND reviewed.management_fee_rate = :managementFeeRate
                  AND reviewed.complete = :complete
                  AND reviewed.checks = :checks
                  AND NOT EXISTS (
                    SELECT 1 FROM investment_ocf_snapshot published
                    WHERE published.fund_code = reviewed.fund_code
                      AND published.snapshot_month = reviewed.snapshot_month
                      AND published.published_at IS NOT NULL)
                """)
            .param("publicationId", kid.publicationId())
            .param("snapshotId", snapshot.storedId())
            .param("calculationFingerprint", OcfCalculationFingerprint.of(snapshot))
            .param("reviewedOcf", snapshot.totalOcf())
            .param("managementFeeRate", snapshot.managementFeeRate())
            .param("complete", snapshot.complete())
            .param("checks", snapshot.checks())
            .param("reason", review.reason())
            .param("reviewedBy", review.reviewedBy())
            .param("reviewedAt", OffsetDateTime.ofInstant(review.reviewedAt(), UTC))
            .update()
        == 1;
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
        .list()
        .stream()
        .filter(reviewed -> !kid.precision().differ(reviewed.ocf(), ocf))
        .map(ReviewedFigure::review)
        .findFirst();
  }

  private record ReviewedFigure(BigDecimal ocf, KidReview review) {}
}
