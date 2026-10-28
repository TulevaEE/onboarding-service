package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryStatus.MATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryStatus.MISMATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryStatus.NOT_IN_REGISTRY;
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
  private static final String ISIN = "IE00BKPTWY98";

  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private EntityManager entityManager;

  private SebFundConfirmationRegistryCheck check;

  @BeforeEach
  void setUp() {
    check = new SebFundConfirmationRegistryCheck(executionRepository, orderRepository);
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
                    "kogus 1000.00 vs 999",
                    "hind 12.4 vs 12.3",
                    "kogumaksumus 12400.00 vs 12287.7")));
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
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(fund).createdBy("test-user").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(fund)
                .instrumentIsin(isin)
                .transactionType(side)
                .instrumentType(instrumentType)
                .orderQuantity(new BigDecimal("1000"))
                .orderVenue(SEB)
                .orderUuid(UUID.randomUUID())
                .build());
    TransactionExecution execution =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId(REFERENCE)
                .executionTimestamp(Instant.parse("2026-01-05T12:48:00Z"))
                .executedQuantity(new BigDecimal(quantity))
                .unitPrice(new BigDecimal(price))
                .totalConsideration(new BigDecimal(total))
                .source("SEB_OOTEL")
                .build());
    entityManager.flush();
    entityManager.clear();
    return execution;
  }

  private static SebFundConfirmation confirmation() {
    return new SebFundConfirmation(
        TUK75,
        REFERENCE,
        Instant.parse("2026-01-05T09:48:17Z"),
        LocalDate.of(2026, 1, 6),
        LocalDate.of(2026, 1, 9),
        ISIN,
        BUY,
        new BigDecimal("1000.00"),
        new BigDecimal("12.4"),
        new BigDecimal("12400.00"),
        new BigDecimal("0.00"),
        new BigDecimal("12400.00"));
  }
}
