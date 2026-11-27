package ee.tuleva.onboarding.investment.fees.instrument;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ProviderAgreementRepository {

  private final JdbcClient jdbcClient;

  Map<Long, ProviderAgreementVersion> findVersionsValidOn(LocalDate day) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM (
              SELECT v.*,
                     ROW_NUMBER() OVER (
                       PARTITION BY v.provider_agreement_id ORDER BY v.valid_from DESC) AS rn
              FROM investment_provider_agreement_version v
              WHERE v.valid_from <= :day
                AND (v.valid_to IS NULL OR v.valid_to >= :day)
            ) ranked
            WHERE rn = 1
            """)
        .param("day", day)
        .query(
            (rs, rowNum) ->
                new ProviderAgreementVersion(
                    rs.getLong("id"),
                    rs.getLong("provider_agreement_id"),
                    AccrualMethod.valueOf(rs.getString("accrual_method"))))
        .list()
        .stream()
        .collect(toMap(ProviderAgreementVersion::providerAgreementId, identity()));
  }
}
