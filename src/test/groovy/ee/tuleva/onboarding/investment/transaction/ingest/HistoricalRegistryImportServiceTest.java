package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoricalRegistryImportServiceTest {

  private static final String FUND_ISIN = "EE3600001707";
  private static final String INSTRUMENT_ISIN = "US0000000001";

  @Mock private TransactionBatchRepository batchRepository;
  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private TransactionSettlementService settlementService;
  @Mock private InstrumentReferenceService instrumentReferenceService;
  @Mock private Clock clock;

  private HistoricalRegistryImportService importService;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(clock.instant())
        .thenReturn(Instant.parse("2026-05-13T07:00:00Z"));
    org.mockito.Mockito.lenient()
        .when(orderRepository.save(any()))
        .thenReturn(TransactionOrder.builder().id(1L).build());
    HistoricalRegistryCsvParser csvParser =
        new HistoricalRegistryCsvParser(
            new HistoricalRegistryRowParser(instrumentReferenceService));
    importService =
        new HistoricalRegistryImportService(
            batchRepository,
            orderRepository,
            executionRepository,
            settlementService,
            csvParser,
            clock);
  }

  @Test
  void fundSellRowWithoutTotalConsideration_isNotTreatedAsAFundSubscription() {
    HistoricalImportResult result =
        importService.importCsv(settledRowCsv("SELL", "", "2025-03-12", ""));

    assertThat(result.errors()).isEmpty();
    assertThat(result.ordersCreated()).isEqualTo(1);
  }

  @Test
  void settledRowWithBothDates_prefersActualSettlementDateOverExpected() {
    importService.importCsv(settledRowCsv("BUY", "2025-03-01", "2025-03-12", "15000.00"));

    verify(settlementService).recordSettlement(any(), eq(LocalDate.of(2025, 3, 12)));
  }

  @Test
  void settledRowWithOnlyExpectedDate_usesExpectedSettlementDate() {
    importService.importCsv(settledRowCsv("BUY", "2025-03-09", "", "15000.00"));

    verify(settlementService).recordSettlement(any(), eq(LocalDate.of(2025, 3, 9)));
  }

  private static String settledRowCsv(
      String side,
      String expectedSettlementDate,
      String actualSettlementDate,
      String totalConsideration) {
    return """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9301,%s,%s,BR-9301,%s,FUND,,15000.000000,2025-03-10 09:00:00,SETTLED,%s,%s,2025-03-10 14:30:00,15000.000000,1.234500,%s,,,
        """
        .formatted(
            FUND_ISIN,
            INSTRUMENT_ISIN,
            side,
            expectedSettlementDate,
            actualSettlementDate,
            totalConsideration);
  }
}
