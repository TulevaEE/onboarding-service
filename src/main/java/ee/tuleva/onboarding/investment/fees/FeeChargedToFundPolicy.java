package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Decides whether a fee is charged to the fund (it reduces NAV and is posted to the fund's ledger)
 * or borne by Tuleva (it is only accrued to track the cost).
 *
 * <p>Every fund and fee type must have an explicit row. An unconfigured pair, overlapping rows and
 * gaps between rows all throw rather than falling back to a default: guessing "charged" would
 * silently put a fee back into a published unit price, and guessing "not charged" would silently
 * stop the management fee from accruing. Failing the NAV run is the recoverable outcome; both
 * guesses are not. Dates before the earliest row predate the fund and answer with that first row,
 * the founding policy.
 */
@Component
@RequiredArgsConstructor
public class FeeChargedToFundPolicy {

  private final JdbcClient jdbcClient;

  public boolean chargedToFund(TulevaFund fund, FeeType feeType, LocalDate date) {
    return resolverFor(fund, feeType).chargedOn(date);
  }

  /**
   * Reads the policy once for callers that ask about many dates -- a period of accruals, a window
   * of daily checks. Asking per date would repeat the same query for every day of the window, and
   * the answer cannot change underneath a single run.
   */
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

      // Rows start at the fund's inception, so a date before the first one predates the fund. Read
      // it as the founding policy still standing rather than as an absence of one, which would
      // silently stop the management fee from accruing. A gap between rows is a mistake, not a
      // statement, and must not resolve to either answer by accident.
      if (date.isBefore(rows.getFirst().validFrom())) {
        return rows.getFirst().chargedToFund();
      }
      throw new IllegalStateException(
          "Gap in the fee policy, no row covers this date: fund="
              + fund
              + ", feeType="
              + feeType
              + ", date="
              + date);
    }
  }

  public record Policy(boolean chargedToFund, LocalDate validFrom, LocalDate validTo) {
    boolean covers(LocalDate date) {
      return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
  }
}
