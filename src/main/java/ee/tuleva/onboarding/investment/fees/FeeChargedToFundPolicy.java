package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeeChargedToFundPolicy {

  private final JdbcClient jdbcClient;

  public boolean chargedToFund(TulevaFund fund, FeeType feeType, LocalDate date) {
    return resolverFor(fund, feeType).chargedOn(date);
  }

  public Resolver resolverFor(TulevaFund fund, FeeType feeType) {
    List<Policy> rows =
        jdbcClient
            .sql(
                """
                SELECT charged_to_fund, valid_from, valid_to
                FROM investment_fee_policy
                WHERE fund_code = :fundCode
                  AND fee_type = :feeType
                ORDER BY valid_from
                """)
            .param("fundCode", fund.name())
            .param("feeType", feeType.name())
            .query(
                (rs, rowNum) ->
                    new Policy(
                        rs.getBoolean("charged_to_fund"),
                        rs.getDate("valid_from").toLocalDate(),
                        rs.getDate("valid_to") != null
                            ? rs.getDate("valid_to").toLocalDate()
                            : null))
            .list();

    if (rows.isEmpty()) {
      throw new IllegalStateException(
          "No fee policy configured: fund=" + fund + ", feeType=" + feeType);
    }
    return new Resolver(fund, feeType, rows);
  }

  public record Resolver(TulevaFund fund, FeeType feeType, List<Policy> rows) {

    public boolean chargedOn(LocalDate date) {
      List<Policy> applicable = rows.stream().filter(policy -> policy.covers(date)).toList();
      if (applicable.size() > 1) {
        throw new IllegalStateException(
            "Overlapping fee policy rows, close the earlier one: fund="
                + fund
                + ", feeType="
                + feeType
                + ", date="
                + date);
      }
      if (applicable.size() == 1) {
        return applicable.getFirst().chargedToFund();
      }
      if (predatesTheFund(date)) {
        return foundingPolicy().chargedToFund();
      }
      throw new IllegalStateException(
          "Gap in the fee policy, no row covers this date: fund="
              + fund
              + ", feeType="
              + feeType
              + ", date="
              + date);
    }

    /**
     * A date before the fund existed is answered with the founding row rather than thrown on, so
     * that a NAV recomputed past the inception date does not fail on the fee policy. The anchor is
     * the fund's inception and not the earliest row's {@code valid_from}: anchoring on the row
     * would make it back-extend over its own late start, so a policy that begins after the fund did
     * would be silently answered for the days in between instead of reported as the gap it is.
     */
    private boolean predatesTheFund(LocalDate date) {
      return date.isBefore(fund.getInceptionDate());
    }

    private Policy foundingPolicy() {
      return rows.getFirst();
    }
  }

  public record Policy(boolean chargedToFund, LocalDate validFrom, @Nullable LocalDate validTo) {
    boolean covers(LocalDate date) {
      return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
  }
}
