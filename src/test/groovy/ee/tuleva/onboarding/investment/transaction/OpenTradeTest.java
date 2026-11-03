package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OpenTradeTest {

  private static final String ISIN = "IE00BFG1TM61";
  private static final Instant TRADE_TIME = Instant.parse("2026-10-16T09:37:55Z");
  private static final OpenTrade WITHOUT_REFERENCE =
      trade(TUK75, null, BUY, ISIN, "500", TRADE_TIME);

  @Test
  void aRowWithoutAReferenceIsTheSameTradeAsARowOfTheSameSideIsinQuantityAndTradeTime() {
    var withReference = trade(TUK75, "DLA1", BUY, ISIN, "500.000", TRADE_TIME);

    assertThat(WITHOUT_REFERENCE.isSameTradeAs(withReference)).isTrue();
    assertThat(withReference.isSameTradeAs(WITHOUT_REFERENCE)).isTrue();
  }

  @Test
  void aRowWithoutAReferenceIsAnotherTradeWhenTheFundSideIsinQuantityOrTradeTimeDiffers() {
    assertThat(
            List.of(
                trade(TUK00, "DLA1", BUY, ISIN, "500", TRADE_TIME),
                trade(TUK75, "DLA1", SELL, ISIN, "500", TRADE_TIME),
                trade(TUK75, "DLA1", BUY, "IE00BKPTWY98", "500", TRADE_TIME),
                trade(TUK75, "DLA1", BUY, ISIN, "501", TRADE_TIME),
                trade(TUK75, "DLA1", BUY, ISIN, null, TRADE_TIME),
                trade(TUK75, "DLA1", BUY, ISIN, "500", TRADE_TIME.plusSeconds(1))))
        .noneMatch(WITHOUT_REFERENCE::isSameTradeAs);
  }

  @Test
  void aRowWithoutAQuantityCannotBeMatchedWithoutAReference() {
    var withoutQuantity = trade(TUK75, null, BUY, ISIN, null, TRADE_TIME);

    assertThat(withoutQuantity.isSameTradeAs(WITHOUT_REFERENCE)).isFalse();
    assertThat(withoutQuantity.isSameTradeAs(withoutQuantity)).isFalse();
  }

  @Test
  void rowsOfTwoFundsAreNeverOneTradeEvenUnderOneReference() {
    var ofTuk75 = trade(TUK75, "DLA1", BUY, ISIN, "500", TRADE_TIME);
    var ofTuk00 = trade(TUK00, "DLA1", BUY, ISIN, "500", TRADE_TIME);

    assertThat(ofTuk75.isSameTradeAs(ofTuk00)).isFalse();
    assertThat(ofTuk75.hasSameReferenceAs(ofTuk00)).isFalse();
  }

  @Test
  void twoRowsHaveTheSameReferenceOnlyWhenBothCarryIt() {
    var withReference = trade(TUK75, "DLA1", BUY, ISIN, "500", TRADE_TIME);
    var revised = trade(TUK75, "DLA1", BUY, ISIN, "500.37", TRADE_TIME);

    assertThat(withReference.hasSameReferenceAs(revised)).isTrue();
    assertThat(withReference.hasSameReferenceAs(WITHOUT_REFERENCE)).isFalse();
    assertThat(WITHOUT_REFERENCE.hasSameReferenceAs(withReference)).isFalse();
    assertThat(withReference.hasSameReferenceAs(trade(TUK75, "DLA2", BUY, ISIN, "500", TRADE_TIME)))
        .isFalse();
  }

  private static OpenTrade trade(
      TulevaFund fund,
      @Nullable String reference,
      TransactionType side,
      String isin,
      @Nullable String quantity,
      Instant tradeDate) {
    return new OpenTrade(
        fund,
        side,
        new BigDecimal("5000.00"),
        reference,
        isin,
        LocalDate.of(2026, 10, 22),
        quantity == null ? null : new BigDecimal(quantity),
        tradeDate);
  }
}
