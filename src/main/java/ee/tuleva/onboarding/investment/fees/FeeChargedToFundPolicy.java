package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
      if (predatesTheFoundingPolicy(date)) {
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
     * Sum only the days this policy actually charges to the fund.
     *
     * <p>The reason this exists: a month-to-date accrual is a sum over many days, and asking
     * "charged?" once — for the last of them — silently applies that answer to every earlier day. A
     * policy that flips mid-month then puts NAV and the ledger out of step for the whole month.
     * Per-day evaluation also keeps the gap and overlap validation in {@link #chargedOn}, which a
     * single lookup would only apply to one date.
     */
    public BigDecimal sumChargedDays(Map<LocalDate, BigDecimal> amountsByDate) {
      return amountsByDate.entrySet().stream()
          .filter(entry -> chargedOn(entry.getKey()))
          .map(Map.Entry::getValue)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean predatesTheFoundingPolicy(LocalDate date) {
      return date.isBefore(foundingPolicy().validFrom());
    }

    private Policy foundingPolicy() {
      return rows.getFirst();
    }
  }

  public record Policy(boolean chargedToFund, LocalDate validFrom, LocalDate validTo) {
    boolean covers(LocalDate date) {
      return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
  }
}
