package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OwnershipLimitProvider {

  private final JdbcClient jdbcClient;

  List<OwnershipLimit> findAllLatestAsOf(LocalDate asOf) {
    return Arrays.stream(TulevaFund.values())
        .flatMap(fund -> findLatestByFundAsOf(fund, asOf).stream())
        .toList();
  }

  private Optional<OwnershipLimit> findLatestByFundAsOf(TulevaFund fund, LocalDate asOf) {
    return jdbcClient
        .sql(
            """
            SELECT effective_date, soft_limit_percent, hard_limit_percent
            FROM investment_ownership_limit
            WHERE fund_code = :fundCode AND effective_date <= :asOf
            ORDER BY effective_date DESC
            LIMIT 1
            """)
        .param("fundCode", fund.getCode())
        .param("asOf", asOf)
        .query(
            (rs, rowNum) ->
                new OwnershipLimit(
                    fund,
                    rs.getObject("effective_date", LocalDate.class),
                    rs.getBigDecimal("soft_limit_percent"),
                    rs.getBigDecimal("hard_limit_percent")))
        .optional();
  }
}
