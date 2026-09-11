package ee.tuleva.onboarding.account.transaction;

import static ee.tuleva.onboarding.epis.CashFlow.Type.CASH;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH_WORKPLACE;
import static ee.tuleva.onboarding.epis.CashFlow.Type.OTHER;
import static ee.tuleva.onboarding.epis.CashFlow.Type.REFUND;
import static ee.tuleva.onboarding.epis.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.epis.CashFlow.Type.TRANSFER_FROM_PIK;
import static ee.tuleva.onboarding.epis.CashFlow.Type.TRANSFER_TO_PIK;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransactionTest {

  private static final Instant BOOKED = Instant.parse("2025-06-29T10:00:00Z");
  private static final Instant PRICED = Instant.parse("2025-07-01T00:00:00Z");

  @Test
  void carriesThePricingTimeOfTheCashFlow() {
    CashFlow cashFlow =
        CashFlow.builder()
            .isin("EE0000003283")
            .time(BOOKED)
            .priceTime(PRICED)
            .amount(new BigDecimal("100.00"))
            .currency(Currency.EUR)
            .type(CONTRIBUTION_CASH)
            .units(new BigDecimal("10.00000"))
            .nav(new BigDecimal("10.0000"))
            .build();

    Transaction transaction = Transaction.from(cashFlow);

    assertThat(transaction.time()).isEqualTo(BOOKED);
    assertThat(transaction.priceTime()).isEqualTo(PRICED);
  }

  @Test
  void pricesAtTheBookingTimeWhenNoPricingTimeIsKnown() {
    assertThat(Transaction.builder().time(BOOKED).build().priceTime()).isEqualTo(BOOKED);
  }

  @ParameterizedTest
  @EnumSource(
      value = CashFlow.Type.class,
      names = {"CONTRIBUTION_CASH", "CONTRIBUTION_CASH_WORKPLACE", "CONTRIBUTION"})
  void contributionsAreAcquisitions(CashFlow.Type type) {
    assertThat(Transaction.builder().type(type).build().isAcquisition()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = CashFlow.Type.class,
      names = {"SUBTRACTION", "CASH", "REFUND", "TRANSFER_TO_PIK", "TRANSFER_FROM_PIK", "OTHER"})
  void everythingElseIsNotAnAcquisition(CashFlow.Type type) {
    assertThat(Transaction.builder().type(type).build().isAcquisition()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(CashFlow.Type.class)
  void everyTypeIsPinnedByTheseTests(CashFlow.Type type) {
    assertThat(type)
        .isIn(
            CONTRIBUTION_CASH,
            CONTRIBUTION_CASH_WORKPLACE,
            CONTRIBUTION,
            SUBTRACTION,
            CASH,
            REFUND,
            TRANSFER_TO_PIK,
            TRANSFER_FROM_PIK,
            OTHER);
  }
}
