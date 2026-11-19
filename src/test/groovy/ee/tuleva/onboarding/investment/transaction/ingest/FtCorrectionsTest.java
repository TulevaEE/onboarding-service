package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.CANCELLATION;
import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.NORMAL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationType;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FtCorrectionsTest {

  private static final String ISIN = "IE00BFG1TM61";
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 8);
  private static final String ACCOUNT = "Tuleva Additional Investment Fund";

  private final FtConfirmation first = confirmation(NORMAL, "1000", "20.40");
  private final FtConfirmation cancellation = confirmation(CANCELLATION, "1000", "20.400000");
  private final FtConfirmation newConfirmation = confirmation(NORMAL, "1000", "20.20");

  @Test
  void confirmationFtCancelled_isVoided() {
    FtCorrections corrections = FtCorrections.among(List.of(first, cancellation));

    assertThat(corrections.isVoided(first)).isTrue();
  }

  @Test
  void confirmationWithoutACancellation_isNotVoided() {
    FtCorrections corrections = FtCorrections.among(List.of(first, newConfirmation));

    assertThat(corrections.isVoided(first)).isFalse();
  }

  @Test
  void cancellationItself_isNotVoided() {
    FtCorrections corrections = FtCorrections.among(List.of(first, cancellation));

    assertThat(corrections.isVoided(cancellation)).isFalse();
  }

  @Test
  void newConfirmationAtTheSamePriceButADifferentQuantity_isNotVoidedByTheCancellation() {
    FtConfirmation correctedQuantity = confirmation(NORMAL, "1010", "20.40");
    FtCorrections corrections =
        FtCorrections.among(List.of(first, cancellation, correctedQuantity));

    assertThat(corrections.isVoided(correctedQuantity)).isFalse();
  }

  @Test
  void cancellationWritingTheAccountInCapitals_stillVoidsTheConfirmationItCancels() {
    FtConfirmation capitalisedAccount =
        confirmation(TKF100, CANCELLATION, "1000", "20.40", ACCOUNT.toUpperCase(Locale.ROOT));
    FtCorrections corrections = FtCorrections.among(List.of(first, capitalisedAccount));

    assertThat(corrections.isVoided(first)).isTrue();
  }

  @Test
  void cancellationFollowedByANewConfirmation_coversTheOneOrder() {
    FtCorrections corrections = FtCorrections.among(List.of(first, cancellation, newConfirmation));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000"))).isTrue();
  }

  @Test
  void cancellationWithOnlyTheConfirmationItCancelled_coversNoOrder() {
    FtCorrections corrections = FtCorrections.among(List.of(first, cancellation));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000"))).isFalse();
  }

  @Test
  void anotherTradeThatDay_doesNotCoverTheOrderFtCancelledWithoutSendingANewConfirmation() {
    FtConfirmation otherTrade = confirmation(NORMAL, "300", "20.20");
    FtCorrections corrections = FtCorrections.among(List.of(first, cancellation, otherTrade));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000", "300")))
        .isFalse();
  }

  @Test
  void anotherTradeThatDayConfirmedTwiceAtDifferentPrices_doesNotCoverTheCancelledOrder() {
    FtConfirmation otherTrade = confirmation(NORMAL, "300", "20.20");
    FtConfirmation otherTradeAgain = confirmation(NORMAL, "300", "20.21");
    FtCorrections corrections =
        FtCorrections.among(List.of(first, cancellation, otherTrade, otherTradeAgain));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000", "300")))
        .isFalse();
  }

  @Test
  void cancelAndResendOfOneOfTwoTradesThatDay_coversItsOrder() {
    FtConfirmation otherTrade = confirmation(NORMAL, "300", "20.20");
    FtCorrections corrections =
        FtCorrections.among(List.of(first, cancellation, newConfirmation, otherTrade));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000", "300")))
        .isTrue();
  }

  @Test
  void newConfirmationWithinOneUnitOfTheCancellationButOfAnotherOrder_doesNotCoverIt() {
    FtConfirmation offByOne = confirmation(NORMAL, "1001", "20.40");
    FtConfirmation itsCancellation = confirmation(CANCELLATION, "1001", "20.40");
    FtConfirmation otherOrdersConfirmation = confirmation(NORMAL, "1002", "20.20");
    FtCorrections corrections =
        FtCorrections.among(List.of(offByOne, itsCancellation, otherOrdersConfirmation));

    assertThat(corrections.newConfirmationsCoverEveryOrder(itsCancellation, orders("1000", "1003")))
        .isFalse();
  }

  @Test
  void twoNewConfirmationsOfOneOrder_doNotCoverTheTwoOrdersTheCancellationCouldBe() {
    FtConfirmation between = confirmation(NORMAL, "1001", "20.40");
    FtConfirmation itsCancellation = confirmation(CANCELLATION, "1001", "20.40");
    FtConfirmation sameOrderOnce = confirmation(NORMAL, "1000", "20.20");
    FtConfirmation sameOrderAgain = confirmation(NORMAL, "1000", "20.21");
    FtCorrections corrections =
        FtCorrections.among(List.of(between, itsCancellation, sameOrderOnce, sameOrderAgain));

    assertThat(corrections.newConfirmationsCoverEveryOrder(itsCancellation, orders("1000", "1002")))
        .isFalse();
  }

  @Test
  void cancellationOfAQuantityThatCollidesWithAnotherOrder_isNotCoveredByThatOrdersConfirmation() {
    FtConfirmation wrongQuantity = confirmation(NORMAL, "2000", "20.40");
    FtConfirmation itsCancellation = confirmation(CANCELLATION, "2000", "20.40");
    FtConfirmation otherOrders = confirmation(NORMAL, "2000", "20.20");
    FtCorrections corrections =
        FtCorrections.among(List.of(wrongQuantity, itsCancellation, otherOrders));

    assertThat(corrections.newConfirmationsCoverEveryOrder(itsCancellation, orders("1000", "2000")))
        .isFalse();
  }

  @Test
  void anotherOrdersConfirmation_isNotLentToTheCancelledTrade() {
    FtConfirmation between = confirmation(NORMAL, "1001", "20.40");
    FtConfirmation itsCancellation = confirmation(CANCELLATION, "1001", "20.40");
    FtConfirmation replacement = confirmation(NORMAL, "1000", "20.20");
    FtConfirmation thirdOrders = confirmation(NORMAL, "1003", "20.20");
    FtCorrections corrections =
        FtCorrections.among(List.of(between, itsCancellation, replacement, thirdOrders));

    assertThat(
            corrections.newConfirmationsCoverEveryOrder(
                itsCancellation, orders("1000", "1002", "1003")))
        .isFalse();
  }

  @Test
  void oneNewConfirmation_doesNotCoverTwoOrdersOfTheCancelledQuantity() {
    FtCorrections corrections = FtCorrections.among(List.of(first, cancellation, newConfirmation));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000", "1000")))
        .isFalse();
  }

  @Test
  void theSameNewConfirmationWrittenWithMoreDecimalsInASecondEmail_countsOnce() {
    FtConfirmation sameWithMoreDecimals = confirmation(NORMAL, "1000.00", "20.200000");
    FtCorrections corrections =
        FtCorrections.among(List.of(cancellation, newConfirmation, sameWithMoreDecimals));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000", "1000")))
        .isFalse();
  }

  @Test
  void cancellationOfAWrongQuantity_isCoveredWhenEveryOrderHasANewConfirmation() {
    FtConfirmation wrongQuantity = confirmation(NORMAL, "900", "20.40");
    FtConfirmation itsCancellation = confirmation(CANCELLATION, "900", "20.40");
    FtCorrections corrections =
        FtCorrections.among(List.of(wrongQuantity, itsCancellation, newConfirmation));

    assertThat(corrections.newConfirmationsCoverEveryOrder(itsCancellation, orders("1000")))
        .isTrue();
  }

  @Test
  void cancellationOfAWrongQuantity_isNotCoveredByConfirmationsOfAnotherOrderAlone() {
    FtConfirmation wrongQuantity = confirmation(NORMAL, "900", "20.40");
    FtConfirmation itsCancellation = confirmation(CANCELLATION, "900", "20.40");
    FtConfirmation otherTradeFirstPart = confirmation(NORMAL, "140", "20.20");
    FtConfirmation otherTradeSecondPart = confirmation(NORMAL, "160", "20.20");
    FtCorrections corrections =
        FtCorrections.among(
            List.of(wrongQuantity, itsCancellation, otherTradeFirstPart, otherTradeSecondPart));

    assertThat(corrections.newConfirmationsCoverEveryOrder(itsCancellation, orders("1000", "300")))
        .isFalse();
  }

  @Test
  void cancellationOfAWrongQuantity_isCoveredWhenEachOrderHasANewConfirmationOfItsQuantity() {
    FtConfirmation wrongQuantity = confirmation(NORMAL, "900", "20.40");
    FtConfirmation itsCancellation = confirmation(CANCELLATION, "900", "20.40");
    FtConfirmation otherTrade = confirmation(NORMAL, "300", "20.20");
    FtCorrections corrections =
        FtCorrections.among(List.of(wrongQuantity, itsCancellation, newConfirmation, otherTrade));

    assertThat(corrections.newConfirmationsCoverEveryOrder(itsCancellation, orders("1000", "300")))
        .isTrue();
  }

  @Test
  void cancellationOfAWrongQuantity_isNotCoveredWithoutANewConfirmation() {
    FtConfirmation wrongQuantity = confirmation(NORMAL, "900", "20.40");
    FtConfirmation itsCancellation = confirmation(CANCELLATION, "900", "20.40");
    FtCorrections corrections = FtCorrections.among(List.of(wrongQuantity, itsCancellation));

    assertThat(corrections.newConfirmationsCoverEveryOrder(itsCancellation, orders("1000")))
        .isFalse();
  }

  @Test
  void confirmationForAnotherFund_doesNotCoverTheCancelledOrder() {
    FtConfirmation otherFund =
        confirmation(TUV100, NORMAL, "1000", "20.20", "Tuleva III Pillar Pension Fund");
    FtCorrections corrections = FtCorrections.among(List.of(first, cancellation, otherFund));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000"))).isFalse();
  }

  @Test
  void confirmationForAnotherTradeDate_doesNotCoverTheCancelledOrder() {
    FtConfirmation nextDay =
        new FtConfirmation(
            TKF100,
            ISIN,
            TRADE_DATE.plusDays(1),
            new BigDecimal("1000"),
            new BigDecimal("20.20"),
            NORMAL,
            ACCOUNT);
    FtCorrections corrections = FtCorrections.among(List.of(cancellation, nextDay));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000"))).isFalse();
  }

  @Test
  void normalConfirmation_isNeverCoveredLikeACancellation() {
    FtCorrections corrections = FtCorrections.among(List.of(first, newConfirmation));

    assertThat(corrections.newConfirmationsCoverEveryOrder(first, orders("1000"))).isFalse();
  }

  @Test
  void suppressedCancellation_voidsNothing() {
    FtCorrections corrections = FtCorrections.among(List.of(first, suppressed(cancellation)));

    assertThat(corrections.isVoided(first)).isFalse();
  }

  @Test
  void suppressedConfirmation_doesNotCoverACancelledOrder() {
    FtCorrections corrections =
        FtCorrections.among(List.of(first, cancellation, suppressed(newConfirmation)));

    assertThat(corrections.newConfirmationsCoverEveryOrder(cancellation, orders("1000"))).isFalse();
  }

  @Test
  void nullRows_areLeftOut() {
    FtCorrections corrections = FtCorrections.among(Arrays.asList(null, first, cancellation));

    assertThat(corrections.isVoided(first)).isTrue();
  }

  private static List<TransactionOrder> orders(String... quantities) {
    return IntStream.range(0, quantities.length)
        .mapToObj(
            index ->
                TransactionOrder.builder()
                    .id((long) index + 1)
                    .orderQuantity(new BigDecimal(quantities[index]))
                    .build())
        .toList();
  }

  private static FtConfirmation suppressed(FtConfirmation confirmation) {
    return new FtConfirmation(
        confirmation.fund(),
        confirmation.isin(),
        confirmation.tradeDate(),
        confirmation.quantity(),
        confirmation.grossPrice(),
        confirmation.type(),
        confirmation.account(),
        true);
  }

  private static FtConfirmation confirmation(
      FtConfirmationType type, String quantity, String grossPrice) {
    return confirmation(TKF100, type, quantity, grossPrice, ACCOUNT);
  }

  private static FtConfirmation confirmation(
      TulevaFund fund,
      FtConfirmationType type,
      String quantity,
      String grossPrice,
      String account) {
    return new FtConfirmation(
        fund,
        ISIN,
        TRADE_DATE,
        new BigDecimal(quantity),
        new BigDecimal(grossPrice),
        type,
        account);
  }
}
