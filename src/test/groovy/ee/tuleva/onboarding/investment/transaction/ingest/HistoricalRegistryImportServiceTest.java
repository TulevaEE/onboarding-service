package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.investment.instrument.InstrumentReference;
import ee.tuleva.onboarding.investment.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult.RowError;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

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
    importService =
        new HistoricalRegistryImportService(
            batchRepository,
            orderRepository,
            executionRepository,
            settlementService,
            instrumentReferenceService,
            clock);
  }

  @Test
  void rowIsRejectedWhenInstrumentReferenceHasNoInstrumentType() {
    InstrumentReference reference = instrumentReference(INSTRUMENT_ISIN, null);
    given(instrumentReferenceService.findByIsin(INSTRUMENT_ISIN))
        .willReturn(Optional.of(reference));

    HistoricalImportResult result = importService.importCsv(csvWithoutInstrumentTypeColumn());

    assertThat(result.errors()).extracting(RowError::rowNumber).containsExactly(2);
    assertThat(result.ordersCreated()).isZero();
    assertThat(result.executionsCreated()).isZero();
    assertThat(result.settlementsCreated()).isZero();
    then(instrumentReferenceService).should().findByIsin(INSTRUMENT_ISIN);
    then(orderRepository).shouldHaveNoInteractions();
    then(executionRepository).shouldHaveNoInteractions();
  }

  @Test
  void rowIsRejectedWhenInstrumentReferenceHasUnrecognisedInstrumentType() {
    InstrumentReference reference = instrumentReference(INSTRUMENT_ISIN, "BOND");
    given(instrumentReferenceService.findByIsin(INSTRUMENT_ISIN))
        .willReturn(Optional.of(reference));

    HistoricalImportResult result = importService.importCsv(csvWithoutInstrumentTypeColumn());

    assertThat(result.errors()).extracting(RowError::rowNumber).containsExactly(2);
    assertThat(result.ordersCreated()).isZero();
    assertThat(result.executionsCreated()).isZero();
    assertThat(result.settlementsCreated()).isZero();
    then(instrumentReferenceService).should().findByIsin(INSTRUMENT_ISIN);
    then(orderRepository).shouldHaveNoInteractions();
    then(executionRepository).shouldHaveNoInteractions();
  }

  private static String csvWithoutInstrumentTypeColumn() {
    return """
        order_id,fund_isin,instrument_isin,order_timestamp,order_status,expected_settlement_date,comment,transaction_type
        GAS-9201,%s,%s,,SENT,,,BUY
        """
        .formatted(FUND_ISIN, INSTRUMENT_ISIN);
  }

  private static InstrumentReference instrumentReference(String isin, String instrumentType) {
    InstrumentReference reference = BeanUtils.instantiateClass(InstrumentReference.class);
    ReflectionTestUtils.setField(reference, "isin", isin);
    ReflectionTestUtils.setField(reference, "instrumentType", instrumentType);
    return reference;
  }
}
