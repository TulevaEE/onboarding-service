package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportedQuantityNormalizerTest {

  private final ReportedQuantityNormalizer normalizer = new ReportedQuantityNormalizer();

  @Test
  void roundedFundRedemptionTakesTheOrderedQuantity() {
    TransactionOrder order = order(FUND, SELL, "18811874.096");

    SebPendingTransactionRow normalized =
        normalizer.normalize(order, row("DLA1116935", "18811874.1"), List.of());

    assertThat(normalized.quantity()).isEqualByComparingTo("18811874.096");
  }

  @Test
  void quantityAlreadyWithinReportedPrecisionIsLeftAlone() {
    TransactionOrder order = order(FUND, SELL, "9588334.049");

    SebPendingTransactionRow normalized =
        normalizer.normalize(order, row("DLA1116936", "9588334.049"), List.of());

    assertThat(normalized.quantity()).isEqualByComparingTo("9588334.049");
  }

  @Test
  void genuineShortfallIsLeftAlone() {
    TransactionOrder order = order(FUND, SELL, "18811874.096");

    SebPendingTransactionRow normalized =
        normalizer.normalize(order, row("DLA1116935", "18811773.0"), List.of());

    assertThat(normalized.quantity()).isEqualByComparingTo("18811773.0");
  }

  @Test
  void closingPieceOfASplitAbsorbsTheRoundingResidue() {
    TransactionOrder order = order(FUND, SELL, "18811874.096");
    List<TransactionExecution> earlierPieces = List.of(execution("DLA1", "10000000"));

    SebPendingTransactionRow normalized =
        normalizer.normalize(order, row("DLA2", "8811874.1"), earlierPieces);

    assertThat(normalized.quantity()).isEqualByComparingTo("8811874.096");
  }

  @Test
  void anEarlierPieceIsNotCountedTwiceWhenSebRestatesIt() {
    TransactionOrder order = order(FUND, SELL, "18811874.096");
    List<TransactionExecution> pieces =
        List.of(execution("DLA1", "10000000"), execution("DLA2", "8811874.1"));

    SebPendingTransactionRow normalized =
        normalizer.normalize(order, row("DLA2", "8811874.1"), pieces);

    assertThat(normalized.quantity()).isEqualByComparingTo("8811874.096");
  }

  @Test
  void orderedQuantityWhoseLastDigitSitsOnTheRoundingBoundaryAbsorbsTheResidue() {
    TransactionOrder order = order(FUND, SELL, "18811874.105");
    List<TransactionExecution> earlierPieces = List.of(execution("DLA1", "10000000"));

    SebPendingTransactionRow normalized =
        normalizer.normalize(order, row("DLA2", "8811874.104"), earlierPieces);

    assertThat(normalized.quantity()).isEqualByComparingTo("8811874.105");
  }

  @Test
  void correctionRowArrivingAfterTheOrderIsFullyExecutedIsLeftAlone() {
    TransactionOrder order = order(FUND, SELL, "18811874.096");
    List<TransactionExecution> fullyExecutedPieces = List.of(execution("DLA1", "18811874.096"));

    SebPendingTransactionRow normalized =
        normalizer.normalize(order, row("DLA2", "0.002"), fullyExecutedPieces);

    assertThat(normalized.quantity()).isEqualByComparingTo("0.002");
  }

  @Test
  void openSplitFarFromTheOrderedTotalIsLeftAlone() {
    TransactionOrder order = order(ETF, BUY, "32746837");

    SebPendingTransactionRow normalized =
        normalizer.normalize(order, row("DLA1126871", "30313"), List.of());

    assertThat(normalized.quantity()).isEqualByComparingTo("30313");
  }

  @Test
  void fundPurchaseOrderedInAmountIsLeftAlone() {
    TransactionOrder order = order(FUND, BUY, null);

    SebPendingTransactionRow normalized =
        normalizer.normalize(order, row("DLA1", "5459.2"), List.of());

    assertThat(normalized.quantity()).isEqualByComparingTo("5459.2");
  }

  private static TransactionOrder order(
      InstrumentType instrumentType, TransactionType side, String orderQuantity) {
    return TransactionOrder.builder()
        .fund(TKF100)
        .instrumentIsin("IE0009FT4LX4")
        .instrumentType(instrumentType)
        .transactionType(side)
        .orderQuantity(orderQuantity == null ? null : new BigDecimal(orderQuantity))
        .orderVenue(OrderVenue.SEB)
        .orderUuid(UUID.randomUUID())
        .build();
  }

  private static SebPendingTransactionRow row(String ourRef, String quantity) {
    BigDecimal qty = new BigDecimal(quantity);
    BigDecimal price = new BigDecimal("17.314");
    return new SebPendingTransactionRow(
        UUID.randomUUID(),
        ourRef,
        "IE0009FT4LX4",
        qty,
        price,
        qty.multiply(price),
        BigDecimal.ZERO,
        qty.multiply(price),
        SELL,
        Instant.parse("2026-08-24T09:48:09Z"),
        LocalDate.of(2026, 8, 27),
        "Tuleva Täiendav Kogumisfond",
        "VP68958",
        "CCF Developed World (ESG Screened) Index Fund Class X0");
  }

  private static TransactionExecution execution(String brokerRef, String quantity) {
    return TransactionExecution.builder()
        .brokerTransactionId(brokerRef)
        .executedQuantity(new BigDecimal(quantity))
        .build();
  }
}
