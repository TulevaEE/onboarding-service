package ee.tuleva.onboarding.investment.fees.ocf;

import static java.util.Objects.requireNonNull;

import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@RequiredArgsConstructor
class OcfPublicationRepository {

  private final JdbcClient jdbcClient;
  private final TransactionTemplate transactionTemplate;

  boolean recordFromCalculation(OcfSnapshot snapshot, OcfPublication publication) {
    return Boolean.TRUE.equals(
        transactionTemplate.execute(
            status -> {
              if (!markPublished(snapshot, publication.publishedIn())) {
                return false;
              }
              insert(publication);
              return true;
            }));
  }

  KidFigure recordGiven(OcfPublication publication) {
    return publication.recordedAs(insert(publication));
  }

  boolean isRecorded(String fundCode, LocalDate validFrom, String publishedIn) {
    return jdbcClient
            .sql(
                """
            SELECT COUNT(*) FROM investment_ocf_publication
            WHERE fund_code = :fundCode AND valid_from = :validFrom
              AND published_in = :publishedIn
            """)
            .param("fundCode", fundCode)
            .param("validFrom", validFrom)
            .param("publishedIn", publishedIn)
            .query(Long.class)
            .single()
        > 0;
  }

  Optional<KidFigure> findInForceOn(String fundCode, LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT id, valid_from, published_ocf, published_decimals, basis, published_in
            FROM investment_ocf_publication
            WHERE fund_code = :fundCode AND valid_from <= :date
            ORDER BY valid_from DESC, recorded_at DESC, id DESC
            LIMIT 1
            """)
        .param("fundCode", fundCode)
        .param("date", date)
        .query(
            (rs, rowNum) ->
                new KidFigure(
                    rs.getLong("id"),
                    rs.getObject("valid_from", LocalDate.class),
                    rs.getBigDecimal("published_ocf"),
                    rs.getInt("published_decimals"),
                    OcfPublicationBasis.valueOf(rs.getString("basis")),
                    rs.getString("published_in")))
        .optional();
  }

  private boolean markPublished(OcfSnapshot snapshot, String publishedIn) {
    return jdbcClient
            .sql(
                """
                UPDATE investment_ocf_snapshot SET
                  published_at = CURRENT_TIMESTAMP,
                  published_in = :publishedIn
                WHERE id = :id
                  AND published_at IS NULL
                  AND total_ocf = :totalOcf
                  AND management_fee_rate = :managementFeeRate
                  AND complete = :complete
                  AND checks = :checks
                """)
            .param("publishedIn", publishedIn)
            .param("id", snapshot.storedId())
            .param("totalOcf", snapshot.totalOcf())
            .param("managementFeeRate", snapshot.managementFeeRate())
            .param("complete", snapshot.complete())
            .param("checks", snapshot.checks())
            .update()
        == 1;
  }

  private long insert(OcfPublication publication) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_publication
              (fund_code, valid_from, published_ocf, published_decimals, basis, basis_reason,
               snapshot_id,
               management_fee_rate, management_fee_rate_id, ex_post_period_start,
               ex_post_period_end, published_in, published_by)
            VALUES
              (:fundCode, :validFrom, :publishedOcf, :publishedDecimals, :basis, :basisReason,
               :snapshotId,
               :managementFeeRate, :managementFeeRateId, :exPostPeriodStart,
               :exPostPeriodEnd, :publishedIn, :publishedBy)
            """)
        .param("fundCode", publication.fundCode())
        .param("validFrom", publication.validFrom())
        .param("publishedOcf", publication.publishedOcf())
        .param("publishedDecimals", publication.publishedDecimals())
        .param("basis", publication.basis().name())
        .param("basisReason", publication.basisReason())
        .param("snapshotId", publication.snapshotId())
        .param("managementFeeRate", publication.managementFeeRate())
        .param("managementFeeRateId", publication.managementFeeRateId())
        .param("exPostPeriodStart", publication.exPostPeriodStart())
        .param("exPostPeriodEnd", publication.exPostPeriodEnd())
        .param("publishedIn", publication.publishedIn())
        .param("publishedBy", publication.publishedBy())
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey(), "OCF publication insert returned no id").longValue();
  }
}
