package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(CashBufferReviewRepository.class)
class CashBufferReviewRepositoryTest {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

  @Autowired private CashBufferReviewRepository repository;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void storesTheRecommendationWithTheWindowAndEveryInputItWasComputedFrom() {
    var review = review(TUK75, SEPTEMBER, "44700.00", null);

    repository.save(review);

    assertThat(repository.findByFundAndMonth(TUK75, SEPTEMBER))
        .hasValueSatisfying(
            stored ->
                assertThat(stored)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(review));
  }

  @Test
  void aSecondRunForTheSameFundAndMonthReplacesTheFirst() {
    repository.save(review(TUK75, SEPTEMBER, "44700.00", "77000.00"));
    var rerun = review(TUK75, SEPTEMBER, "46100.00", "77000.00");

    repository.save(rerun);

    assertThat(repository.findByFundAndMonth(TUK75, SEPTEMBER))
        .hasValueSatisfying(
            stored ->
                assertThat(stored.recommendation().recommended()).isEqualByComparingTo("46100.00"));
    assertThat(rowsIn("investment_cash_buffer_review")).isEqualTo(1);
    assertThat(rowsIn("investment_cash_buffer_review_month")).isEqualTo(2);
  }

  @Test
  void keepsEachFundsReviewApart() {
    repository.save(review(TUK75, SEPTEMBER, "44700.00", null));

    assertThat(repository.findByFundAndMonth(TUK00, SEPTEMBER)).isEmpty();
    assertThat(repository.findByFundAndMonth(TUK75, SEPTEMBER.minusMonths(1))).isEmpty();
  }

  private long rowsIn(String table) {
    return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
  }

  private static CashBufferReview review(
      TulevaFund fund, YearMonth month, String recommended, String reserveHard) {
    var model =
        new BufferModel(
            new BigDecimal("0.95"),
            new BigDecimal("0.20"),
            new BigDecimal("0.1"),
            new BigDecimal("24000.00"));
    return new CashBufferReview(
        fund,
        month,
        LocalDate.of(2026, 10, 6),
        new FlowWindow(
            List.of(
                new MonthlyFlows(
                    month.minusMonths(1),
                    new BigDecimal("800000.00"),
                    new BigDecimal("12000.00"),
                    new BigDecimal("60000.00"),
                    new BigDecimal("500000.00"),
                    new BigDecimal("5000.00"),
                    1),
                new MonthlyFlows(
                    month,
                    new BigDecimal("1000000.00"),
                    new BigDecimal("11000.00"),
                    new BigDecimal("4000.00"),
                    new BigDecimal("0.00"),
                    new BigDecimal("0.00"),
                    0))),
        new Recommendation(
            model,
            new BigDecimal("65700.00"),
            new BigDecimal("480000.00"),
            new BigDecimal("3000.00"),
            new BigDecimal(recommended)),
        new ConfiguredReserve(
            LocalDate.of(2026, 1, 1),
            new BigDecimal("131000.00"),
            reserveHard == null ? null : new BigDecimal(reserveHard)),
        new Drift(new BigDecimal("-86300.00"), new BigDecimal("50000.00"), true, 2, 2));
  }
}
