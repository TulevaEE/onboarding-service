package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryImportService.SOURCE_HISTORICAL_IMPORT;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryStatus.MATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryStatus.MISMATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryStatus.NOT_IN_REGISTRY;
import static ee.tuleva.onboarding.investment.transaction.ingest.TransactionExecutionMapper.SOURCE_SEB_OOTEL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryComparison;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class SebFundConfirmationRegistryCheckIT {

  private static final String REFERENCE = "DLA0000001";
  private static final String FIRST_FILL = "DLA0000002";
  private static final String ISIN = "IE00BKPTWY98";

  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private EntityManager entityManager;

  private SebFundConfirmationRegistryCheck check;

  @BeforeEach
  void setUp() {
    check =
        new SebFundConfirmationRegistryCheck(
            executionRepository, orderRepository, new ReportedQuantityNormalizer());
  }

  @Test
  void compare_saysNothingAboutATradeThatIsNotInTheRegistry() {
    assertThat(check.compare(confirmation())).isEqualTo(RegistryComparison.NOT_IN_REGISTRY);
    assertThat(check.compare(confirmation()).status()).isEqualTo(NOT_IN_REGISTRY);
  }

  @Test
  void compare_matchesAnExecutionWithTheConfirmedFiguresToSebsPrecision() {
    persist(TUK75, ISIN, BUY, FUND, "1000.0000", "12.40000000", "12400.00");

    assertThat(check.compare(confirmation())).isEqualTo(new RegistryComparison(MATCH, List.of()));
  }

  @Test
  void compare_listsEveryFigureTheRegistryHoldsDifferently() {
    persist(TUK75, ISIN, BUY, FUND, "999.0000", "12.30000000", "12287.70");

    assertThat(check.compare(confirmation()))
        .isEqualTo(
            new RegistryComparison(
                MISMATCH,
                List.of(
                    "kogus 1000 vs 999", "hind 12.4 vs 12.3", "kogumaksumus 12400 vs 12287.7")));
  }

  @Test
  void compare_comparesAtThePrecisionTheRegistryStores() {
    persist(TUK75, ISIN, BUY, FUND, "1234.5679", "12.40000000", "15308.64");

    assertThat(check.compare(confirmation("1234.56789", "12.4", "15308.64")))
        .isEqualTo(new RegistryComparison(MATCH, List.of()));
  }

  @Test
  void compare_matchesAPriceThatAgreesAtSebsPrecisionEvenWhereRoundingToTheRegistryWouldNot() {
    persist(TUK75, ISIN, BUY, FUND, "1000.0000", "123.45678940", "123456.79");

    assertThat(check.compare(confirmation("1000.00", "123.456789449", "123456.79")))
        .isEqualTo(new RegistryComparison(MATCH, List.of()));
  }

  @Test
  void compare_matchesAPriceSebRoundedToTenDigitsBeforeTheRegistryStoredIt() {
    persist(TUK75, ISIN, BUY, FUND, "1000.0000", "1.23456790", "1234.57");

    assertThat(check.compare(confirmation("1000.00", "1.2345678949", "1234.57")))
        .isEqualTo(new RegistryComparison(MATCH, List.of()));
  }

  @Test
  void compare_stillFindsAPriceOneStoredUnitAway() {
    persist(TUK75, ISIN, BUY, FUND, "1000.0000", "1.23456791", "1234.57");

    assertThat(check.compare(confirmation("1000.00", "1.2345678949", "1234.57")))
        .isEqualTo(new RegistryComparison(MISMATCH, List.of("hind 1.2345678949 vs 1.23456791")));
  }

  @Test
  void compare_findsATotalThatNoRoundingOfTheConfirmationProduces() {
    persist(TUK75, ISIN, BUY, FUND, "1000.0000", "12.40000000", "123456789.15");

    assertThat(check.compare(confirmation("1000.00", "12.4", "123456789.10")))
        .isEqualTo(
            new RegistryComparison(MISMATCH, List.of("kogumaksumus 123456789.1 vs 123456789.15")));
  }

  @Test
  void compare_matchesBothFillsOfARedemptionWhoseClosingFillAbsorbedTheRounding() {
    TransactionOrder redemption = order(SELL, "18811874.1046");
    execution(redemption, FIRST_FILL, "10000000.0100", "1.00000000", "10000000.01");
    execution(redemption, REFERENCE, "8811874.0946", "1.00000000", "8811874.09");

    assertThat(check.compare(confirmation(FIRST_FILL, SELL, "10000000.0092", "1", "10000000.01")))
        .isEqualTo(new RegistryComparison(MATCH, List.of()));
    assertThat(check.compare(confirmation(REFERENCE, SELL, "8811874.0954", "1", "8811874.09")))
        .isEqualTo(new RegistryComparison(MATCH, List.of()));
  }

  @Test
  void compare_findsAFillFromSebsReportThatTheNormalizerWouldHaveCompleted() {
    TransactionOrder redemption = order(SELL, "18811874.1046");
    execution(redemption, REFERENCE, "18811874.1000", "1.00000000", "18811874.10");

    assertThat(check.compare(confirmation(REFERENCE, SELL, "18811874.1046", "1", "18811874.10")))
        .isEqualTo(new RegistryComparison(MISMATCH, List.of("kogus 18811874.1046 vs 18811874.1")));
  }

  @Test
  void compare_matchesAHistoricalQuantityStoredAsSebReportedIt() {
    TransactionOrder redemption = order(SELL, "18811874.1046");
    execution(
        redemption,
        REFERENCE,
        SOURCE_HISTORICAL_IMPORT,
        "18811874.1000",
        "1.00000000",
        "18811874.10");

    assertThat(check.compare(confirmation(REFERENCE, SELL, "18811874.1046", "1", "18811874.10")))
        .isEqualTo(new RegistryComparison(MATCH, List.of()));
  }

  @Test
  void compare_stillFindsAHistoricalQuantityOneStoredUnitFromSebs() {
    TransactionOrder redemption = order(SELL, "18811874.1046");
    execution(
        redemption,
        REFERENCE,
        SOURCE_HISTORICAL_IMPORT,
        "18811874.1001",
        "1.00000000",
        "18811874.10");

    assertThat(check.compare(confirmation(REFERENCE, SELL, "18811874.1046", "1", "18811874.10")))
        .isEqualTo(
            new RegistryComparison(MISMATCH, List.of("kogus 18811874.1046 vs 18811874.1001")));
  }

  @Test
  void compare_findsAPriceFromSebsReportThatSebsTenDigitsCannotGive() {
    persist(TUK75, ISIN, BUY, FUND, "1000.0000", "1.23456789", "1234.57");

    assertThat(check.compare(confirmation("1000.00", "1.2345678949", "1234.57")))
        .isEqualTo(new RegistryComparison(MISMATCH, List.of("hind 1.2345678949 vs 1.23456789")));
  }

  @Test
  void compare_matchesAHistoricalPriceAtTheStoredScale() {
    execution(
        order(BUY, "1000"),
        REFERENCE,
        SOURCE_HISTORICAL_IMPORT,
        "1000.0000",
        "1.23456789",
        "1234.57");

    assertThat(check.compare(confirmation("1000.00", "1.2345678949", "1234.57")))
        .isEqualTo(new RegistryComparison(MATCH, List.of()));
  }

  @Test
  void compare_matchesAHandEnteredPriceAtTheStoredScale() {
    execution(order(BUY, "1000"), REFERENCE, "MANUAL", "1000.0000", "1.23456789", "1234.57");

    assertThat(check.compare(confirmation("1000.00", "1.2345678949", "1234.57")))
        .isEqualTo(new RegistryComparison(MATCH, List.of()));
  }

  @Test
  void compare_stillFindsAClosingFillThatTheRoundingDoesNotExplain() {
    TransactionOrder redemption = order(SELL, "18811874.1046");
    execution(redemption, FIRST_FILL, "10000000.0100", "1.00000000", "10000000.01");
    execution(redemption, REFERENCE, "8811874.0946", "1.00000000", "8811874.09");

    assertThat(check.compare(confirmation(REFERENCE, SELL, "8811874.0854", "1", "8811874.09")))
        .isEqualTo(new RegistryComparison(MISMATCH, List.of("kogus 8811874.0854 vs 8811874.0946")));
  }

  @Test
  void compare_saysWhenTheReferenceBelongsToAnotherTradeInTheRegistry() {
    persist(TUK00, "IE00BFG1TM61", SELL, ETF, "1000.0000", "12.40000000", "12400.00");

    assertThat(check.compare(confirmation()))
        .isEqualTo(
            new RegistryComparison(
                MISMATCH,
                List.of(
                    "fond TUK75 vs TUK00",
                    "ISIN IE00BKPTWY98 vs IE00BFG1TM61",
                    "suund BUY vs SELL",
                    "instrumendi liik FUND vs ETF")));
  }

  @Test
  void executionOfTheSameTrade_findsTheExecutionOfTheConfirmedFundTrade() {
    TransactionExecution execution = persist(TUK75, ISIN, BUY, FUND, "1", "1", "1");

    assertThat(check.executionOfTheSameTrade(confirmation()))
        .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(execution.getId()));
  }

  @Test
  void executionOfTheSameTrade_ignoresAnExecutionOfAnotherInstrument() {
    persist(TUK75, "IE00BFG1TM61", BUY, FUND, "1", "1", "1");

    assertThat(check.executionOfTheSameTrade(confirmation())).isEmpty();
  }

  @Test
  void executionOfTheSameTrade_ignoresAnEtfFillWithTheSameReference() {
    persist(TUK75, ISIN, BUY, ETF, "1", "1", "1");

    assertThat(check.executionOfTheSameTrade(confirmation())).isEmpty();
  }

  @Test
  void executionOfTheSameTrade_waitsUntilTheTradeIsInTheRegistry() {
    assertThat(check.executionOfTheSameTrade(confirmation())).isEmpty();
  }

  private TransactionExecution persist(
      TulevaFund fund,
      String isin,
      TransactionType side,
      InstrumentType instrumentType,
      String quantity,
      String price,
      String total) {
    return execution(
        order(fund, isin, side, instrumentType, "1000"), REFERENCE, quantity, price, total);
  }

  private TransactionOrder order(TransactionType side, String quantity) {
    return order(TUK75, ISIN, side, FUND, quantity);
  }

  private TransactionOrder order(
      TulevaFund fund,
      String isin,
      TransactionType side,
      InstrumentType instrumentType,
      String quantity) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(fund).createdBy("test-user").build());
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(fund)
            .instrumentIsin(isin)
            .transactionType(side)
            .instrumentType(instrumentType)
            .orderQuantity(new BigDecimal(quantity))
            .orderVenue(SEB)
            .orderUuid(UUID.randomUUID())
            .build());
  }

  private TransactionExecution execution(
      TransactionOrder order, String reference, String quantity, String price, String total) {
    return execution(order, reference, SOURCE_SEB_OOTEL, quantity, price, total);
  }

  private TransactionExecution execution(
      TransactionOrder order,
      String reference,
      String source,
      String quantity,
      String price,
      String total) {
    TransactionExecution execution =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId(reference)
                .executionTimestamp(Instant.parse("2026-01-05T12:48:00Z"))
                .executedQuantity(new BigDecimal(quantity))
                .unitPrice(new BigDecimal(price))
                .totalConsideration(new BigDecimal(total))
                .source(source)
                .build());
    entityManager.flush();
    entityManager.clear();
    return execution;
  }

  private static SebFundConfirmation confirmation() {
    return confirmation("1000.00", "12.4", "12400.00");
  }

  private static SebFundConfirmation confirmation(String quantity, String price, String total) {
    return confirmation(REFERENCE, BUY, quantity, price, total);
  }

  private static SebFundConfirmation confirmation(
      String reference, TransactionType side, String quantity, String price, String total) {
    return new SebFundConfirmation(
        TUK75,
        reference,
        Instant.parse("2026-01-05T09:48:17Z"),
        LocalDate.of(2026, 1, 6),
        LocalDate.of(2026, 1, 9),
        ISIN,
        side,
        new BigDecimal(quantity),
        new BigDecimal(price),
        new BigDecimal(total),
        new BigDecimal("0.00"),
        new BigDecimal(total));
  }
}
