package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderSender;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OrderEmailSummaryTest {

  private static final OrderDay DAY =
      new OrderDay(
          List.of(12L, 13L),
          LocalDate.parse("2026-11-26"),
          Instant.parse("2026-11-26T08:50:00Z"),
          new OrderSender("fundmanager@tuleva.ee", "Fund Manager"),
          List.of(
              order(TUK75, ETF, FT, BUY, "1000.50"),
              order(TUV100, ETF, FT, SELL, "2000"),
              order(TUV100, ETF, SEB, SELL, null),
              order(TUK00, FUND, SEB, BUY, "300"),
              order(TUK00, FUND, SEB, SELL, "120")),
          Map.of());

  @Test
  void countsOnlyTheOrdersItsEmailCarries_andAddsUpTheirAmounts() {
    assertThat(OrderEmailSummary.of(DAY, OrderEmail.FT))
        .isEqualTo(
            new OrderEmailSummary("FT:12,13", OrderEmail.FT, 2, 0, new BigDecimal("3000.50")));
  }

  @Test
  void aFundRedemption_goesToSebAsAQuantity_soItsEstimatedAmountStaysOutOfTheTotal() {
    assertThat(OrderEmailSummary.of(DAY, OrderEmail.SEB_FUND))
        .isEqualTo(
            new OrderEmailSummary(
                "SEB_FUND:12,13", OrderEmail.SEB_FUND, 2, 1, new BigDecimal("300")));
  }

  @Test
  void anOrderGivenAsAQuantity_isCountedButLeftOutOfTheAmount() {
    assertThat(OrderEmailSummary.of(DAY, OrderEmail.SEB_ETF))
        .isEqualTo(
            new OrderEmailSummary("SEB_ETF:12,13", OrderEmail.SEB_ETF, 1, 1, BigDecimal.ZERO));
    assertThat(OrderEmailSummary.of(DAY, OrderEmail.UUID))
        .isEqualTo(
            new OrderEmailSummary("UUID:12,13", OrderEmail.UUID, 3, 1, new BigDecimal("3000.50")));
  }

  @Test
  void eachEmailKindHasATagOfItsOwn_thatADeliveryFailureIsTracedBackBy() {
    assertThat(List.of(OrderEmail.values()).stream().map(OrderEmail::tag))
        .containsExactly(
            "trade-order-ft-etf",
            "trade-order-seb-etf",
            "trade-order-seb-fund",
            "trade-order-uuid-workbook");
    assertThat(OrderEmail.taggedIn(List.of("trade-order", "trade-order-seb-fund")))
        .contains(OrderEmail.SEB_FUND);
    assertThat(OrderEmail.taggedIn(List.of("trade-order"))).isEmpty();
  }

  private static TransactionOrder order(
      TulevaFund fund,
      InstrumentType type,
      OrderVenue venue,
      TransactionType side,
      @Nullable String amount) {
    return TransactionOrder.builder()
        .fund(fund)
        .transactionType(side)
        .instrumentIsin("IE00TEST0001")
        .instrumentType(type)
        .orderVenue(venue)
        .orderAmount(amount == null ? null : new BigDecimal(amount))
        .orderQuantity(side == SELL ? new BigDecimal("10") : null)
        .build();
  }
}
