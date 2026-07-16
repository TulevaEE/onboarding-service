package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ee.tuleva.onboarding.investment.transaction.BatchStatus;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportFormatException;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HistoricalRegistryImportServiceIT {

  private static final String SETTLED_ORDER_UUID = "0f8fad5b-d9cb-469f-a165-70867728950e";

  private static final String FULL_CSV =
      """
      order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
      0f8fad5b-d9cb-469f-a165-70867728950e,EE3600109435,IE00BFG1TM61,BR-1001,BUY,ETF,25000.00,250.000000,2025-03-10 09:00:00,SETTLED,2025-03-12,2025-03-12,2025-03-10 14:30:00,250.000000,100.10,25025.00,25030.00,5.00,initial buy
      GAS-2024-001,EE3600109435,IE0009FT4LX4,BR-1002,SELL,ETF,10000.00,100.000000,2025-04-01 09:00:00,EXECUTED,2025-04-03,,2025-04-01 15:00:00,100.000000,99.50,9950.00,9945.00,5.00,
      GAS-2024-002,EE3600109443,LU0826455353,,BUY,FUND,5000.00,,2025-05-05 09:00:00,SENT,2025-05-09,,,,,,,,pending fund buy
      """;

  @Autowired private HistoricalRegistryImportService importService;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionSettlementRepository settlementRepository;

  @Test
  void importCreatesOrdersExecutionsAndSettlements() {
    HistoricalImportResult result = importService.importCsv(FULL_CSV);

    assertThat(result.rowCount()).isEqualTo(3);
    assertThat(result.ordersCreated()).isEqualTo(3);
    assertThat(result.executionsCreated()).isEqualTo(2);
    assertThat(result.settlementsCreated()).isEqualTo(1);
    assertThat(result.skippedExisting()).isZero();
    assertThat(result.errors()).isEmpty();
    assertThat(result.totalAmountByFund().get(TUK75)).isEqualByComparingTo("34975.00");
    assertThat(result.totalAmountByFund().get(TUK00)).isEqualByComparingTo("5000.00");

    TransactionOrder settledOrder =
        orderRepository.findByOrderUuid(UUID.fromString(SETTLED_ORDER_UUID)).orElseThrow();
    assertThat(settledOrder.getFund()).isEqualTo(TUK75);
    assertThat(settledOrder.getInstrumentIsin()).isEqualTo("IE00BFG1TM61");
    assertThat(settledOrder.getTransactionType()).isEqualTo(TransactionType.BUY);
    assertThat(settledOrder.getInstrumentType()).isEqualTo(InstrumentType.ETF);
    assertThat(settledOrder.getOrderAmount()).isEqualByComparingTo("25000.00");
    assertThat(settledOrder.getOrderQuantity()).isEqualByComparingTo("250.000000");
    assertThat(settledOrder.getOrderVenue()).isEqualTo(OrderVenue.SEB);
    assertThat(settledOrder.getOrderStatus()).isEqualTo(OrderStatus.SETTLED);
    assertThat(settledOrder.getOrderTimestamp()).isEqualTo(Instant.parse("2025-03-10T09:00:00Z"));
    assertThat(settledOrder.getExpectedSettlementDate()).isEqualTo(LocalDate.parse("2025-03-12"));
    assertThat(settledOrder.getComment()).isEqualTo("initial buy");

    TransactionBatch batch = settledOrder.getBatch();
    assertThat(batch.getFund()).isEqualTo(TUK75);
    assertThat(batch.getCreatedBy()).isEqualTo("historical-import");
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.SENT);
    assertThat(batch.getMetadata()).containsEntry("source", "HISTORICAL_IMPORT");

    TransactionExecution execution =
        executionRepository.findAllByOrderId(settledOrder.getId()).getFirst();
    assertThat(execution.getBrokerTransactionId()).isEqualTo("BR-1001");
    assertThat(execution.getExecutionTimestamp()).isEqualTo(Instant.parse("2025-03-10T14:30:00Z"));
    assertThat(execution.getExecutedQuantity()).isEqualByComparingTo("250.000000");
    assertThat(execution.getUnitPrice()).isEqualByComparingTo("100.10");
    assertThat(execution.getTotalConsideration()).isEqualByComparingTo("25025.00");
    assertThat(execution.getSettlementAmount()).isEqualByComparingTo("25030.00");
    assertThat(execution.getCommissionAmount()).isEqualByComparingTo("5.00");
    assertThat(execution.getActualSettlementDate()).isEqualTo(LocalDate.parse("2025-03-12"));
    assertThat(execution.getSource()).isEqualTo("HISTORICAL_IMPORT");
    assertThat(execution.getModifiedBy()).isEqualTo("historical-import");

    TransactionSettlement settlement =
        settlementRepository.findByOrderId(settledOrder.getId()).orElseThrow();
    assertThat(settlement.getReportDate()).isEqualTo(LocalDate.parse("2025-03-12"));

    TransactionOrder executedOrder = findByComment("BR-1002");
    assertThat(executedOrder.getOrderStatus()).isEqualTo(OrderStatus.EXECUTED);
    assertThat(settlementRepository.findByOrderId(executedOrder.getId())).isEmpty();

    TransactionOrder sentOrder = orderRepository.findByInstrumentIsin("LU0826455353").getFirst();
    assertThat(sentOrder.getOrderStatus()).isEqualTo(OrderStatus.SENT);
    assertThat(sentOrder.getOrderQuantity()).isNull();
    assertThat(executionRepository.findAllByOrderId(sentOrder.getId())).isEmpty();
    assertThat(sentOrder.getBatch().getId()).isNotEqualTo(batch.getId());
  }

  @Test
  void importIsIdempotentAcrossReRuns() {
    importService.importCsv(FULL_CSV);
    long ordersAfterFirst = orderRepository.count();
    long executionsAfterFirst = executionRepository.count();
    long settlementsAfterFirst = settlementRepository.count();

    HistoricalImportResult second = importService.importCsv(FULL_CSV);

    assertThat(second.rowCount()).isEqualTo(3);
    assertThat(second.skippedExisting()).isEqualTo(3);
    assertThat(second.ordersCreated()).isZero();
    assertThat(second.executionsCreated()).isZero();
    assertThat(second.settlementsCreated()).isZero();
    assertThat(second.errors()).isEmpty();
    assertThat(orderRepository.count()).isEqualTo(ordersAfterFirst);
    assertThat(executionRepository.count()).isEqualTo(executionsAfterFirst);
    assertThat(settlementRepository.count()).isEqualTo(settlementsAfterFirst);
  }

  @Test
  void importWithInvalidRowPersistsNothing() {
    long ordersBefore = orderRepository.count();
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-2024-010,EE3600109435,IE00BFG1TM61,,BUY,ETF,1000.00,,2025-03-10 09:00:00,SENT,2025-03-12,,,,,,,,
        GAS-2024-011,XX0000000000,IE00BFG1TM61,,BUY,ETF,1000.00,,2025-03-10 09:00:00,SENT,2025-03-12,,,,,,,,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.rowCount()).isEqualTo(2);
    assertThat(result.ordersCreated()).isZero();
    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(3, "Unknown fund: fundIsin=XX0000000000"));
    assertThat(orderRepository.count()).isEqualTo(ordersBefore);
  }

  @Test
  void importWithInFileDuplicateBrokerTransactionIdPersistsNothing() {
    long ordersBefore = orderRepository.count();
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-2024-040,EE3600109435,IE00BFG1TM61,BR-3040,BUY,ETF,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,10.000000,100.00,1000.00,995.00,5.00,
        GAS-2024-041,EE3600109435,IE00BFG1TM61,BR-3040,BUY,ETF,2000.00,20.000000,2025-03-11 09:00:00,EXECUTED,2025-03-13,,2025-03-11 14:30:00,20.000000,100.00,2000.00,1995.00,5.00,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.rowCount()).isEqualTo(2);
    assertThat(result.ordersCreated()).isZero();
    assertThat(result.executionsCreated()).isZero();
    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(
                3, "Duplicate brokerTransactionId in file: brokerTransactionId=BR-3040"));
    assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    assertThat(executionRepository.findByBrokerTransactionId("BR-3040")).isEmpty();
  }

  @Test
  void importWithMultipleBlankBrokerTransactionIdsSucceeds() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-2024-050,EE3600109435,IE00BFG1TM61,,BUY,ETF,1000.00,,2025-03-10 09:00:00,SENT,2025-03-12,,,,,,,,
        GAS-2024-051,EE3600109435,IE00BFG1TM61,,BUY,ETF,2000.00,,2025-03-11 09:00:00,SENT,2025-03-13,,,,,,,,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.ordersCreated()).isEqualTo(2);
  }

  @Test
  void importWithAlreadyPersistedDuplicateBrokerTransactionIdReportsRowError() {
    importService.importCsv(
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-2024-060,EE3600109435,IE00BFG1TM61,BR-3060,BUY,ETF,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,10.000000,100.00,1000.00,995.00,5.00,
        """);
    long ordersBefore = orderRepository.count();
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-2024-061,EE3600109435,IE00BFG1TM61,BR-3060,BUY,ETF,2000.00,20.000000,2025-03-11 09:00:00,EXECUTED,2025-03-13,,2025-03-11 14:30:00,20.000000,100.00,2000.00,1995.00,5.00,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.rowCount()).isEqualTo(1);
    assertThat(result.ordersCreated()).isZero();
    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(
                2, "Duplicate brokerTransactionId: brokerTransactionId=BR-3060"));
    assertThat(orderRepository.count()).isEqualTo(ordersBefore);
  }

  @Test
  void importParsesSemicolonDelimiterAndEstonianDecimals() {
    String csv =
        """
        order_id;fund_isin;instrument_isin;transaction_id;transaction_type;instrument_type;order_amount;order_quantity;order_timestamp;order_status;expected_settlement_date;actual_settlement_date;execution_timestamp;executed_quantity;unit_price;total_consideration;net_settlement_amount;commission_amount;comment
        GAS-2024-020;EE3600109435;IE00BFG1TM61;BR-2020;BUY;ETF;25 000,00;250,000000;2025-03-10 09:00:00;EXECUTED;2025-03-12;;2025-03-10 14:30:00;250,000000;100,10;25 025,00;25 030,00;5,00;
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.ordersCreated()).isEqualTo(1);
    TransactionOrder order = findByComment("BR-2020");
    assertThat(order.getOrderAmount()).isEqualByComparingTo("25000.00");
    assertThat(order.getOrderQuantity()).isEqualByComparingTo("250.000000");
    TransactionExecution execution =
        executionRepository.findByBrokerTransactionId("BR-2020").orElseThrow();
    assertThat(execution.getUnitPrice()).isEqualByComparingTo("100.10");
    assertThat(execution.getTotalConsideration()).isEqualByComparingTo("25025.00");
  }

  @Test
  void importResolvesSingleSeparatorAsThousandsUnderSemicolonDelimiter() {
    String csv =
        """
        order_id;fund_isin;instrument_isin;transaction_id;transaction_type;instrument_type;order_amount;order_quantity;order_timestamp;order_status;expected_settlement_date;actual_settlement_date;execution_timestamp;executed_quantity;unit_price;total_consideration;net_settlement_amount;commission_amount;comment
        GAS-2024-070;EE3600109435;IE00BFG1TM61;BR-2070;BUY;ETF;12.345;;2025-03-10 09:00:00;SENT;2025-03-12;;;;;;;;
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors()).isEmpty();
    TransactionOrder order = orderRepository.findByInstrumentIsin("IE00BFG1TM61").getFirst();
    assertThat(order.getOrderAmount()).isEqualByComparingTo("12345");
  }

  @Test
  void importResolvesCommaDecimalConventionAcrossThousandsAndDecimalForms() {
    String csv =
        """
        order_id;fund_isin;instrument_isin;transaction_id;transaction_type;instrument_type;order_amount;order_quantity;order_timestamp;order_status;expected_settlement_date;actual_settlement_date;execution_timestamp;executed_quantity;unit_price;total_consideration;net_settlement_amount;commission_amount;comment
        GAS-2024-071;EE3600109435;IE00BFG1TM61;BR-2071;BUY;ETF;1.234.567;0.80000;2025-03-10 09:00:00;SENT;2025-03-12;;;;150000000,000;100,000;;;
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors()).isEmpty();
    TransactionOrder order = orderRepository.findByInstrumentIsin("IE00BFG1TM61").getFirst();
    assertThat(order.getOrderAmount()).isEqualByComparingTo("1234567");
    assertThat(order.getOrderQuantity()).isEqualByComparingTo("0.80000");
    TransactionExecution execution =
        executionRepository.findByBrokerTransactionId("BR-2071").orElseThrow();
    assertThat(execution.getUnitPrice()).isEqualByComparingTo("150000000.000");
    assertThat(execution.getTotalConsideration()).isEqualByComparingTo("100.000");
  }

  @Test
  void importResolvesPeriodDecimalConventionAcrossThousandsAndDecimalForms() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-2024-072,EE3600109435,IE00BFG1TM61,BR-2072,BUY,ETF,12.345,"1,234.56",2025-03-10 09:00:00,SENT,2025-03-12,,,,"100,000",1234.56,,,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors()).isEmpty();
    TransactionOrder order = orderRepository.findByInstrumentIsin("IE00BFG1TM61").getFirst();
    assertThat(order.getOrderAmount()).isEqualByComparingTo("12.345");
    assertThat(order.getOrderQuantity()).isEqualByComparingTo("1234.56");
    TransactionExecution execution =
        executionRepository.findByBrokerTransactionId("BR-2072").orElseThrow();
    assertThat(execution.getUnitPrice()).isEqualByComparingTo("100000");
    assertThat(execution.getTotalConsideration()).isEqualByComparingTo("1234.56");
  }

  @Test
  void importWithMissingHeadersThrowsFormatException() {
    String csv =
        """
        order_id,fund_isin
        GAS-2024-030,EE3600109435
        """;

    HistoricalImportFormatException exception =
        catchThrowableOfType(
            HistoricalImportFormatException.class, () -> importService.importCsv(csv));

    assertThat(exception.getMissingHeaders())
        .containsExactly(
            "instrument_isin",
            "order_timestamp",
            "order_status",
            "expected_settlement_date",
            "comment");
    assertThat(exception.getRequiredHeaders())
        .containsExactly(
            "order_id",
            "fund_isin",
            "instrument_isin",
            "order_timestamp",
            "order_status",
            "expected_settlement_date",
            "comment");
  }

  @Test
  void executedRowWithBlankOrderTimestampIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9001,EE3600109435,IE00BFG1TM61,BR-9001,BUY,ETF,1000.00,10.000000,,EXECUTED,2025-03-12,,2025-03-10 14:30:00,10.000000,100.00,1000.00,995.00,5.00,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(
                2, "Missing value: column=order_timestamp, orderId=GAS-9001"));
    assertThat(result.ordersCreated()).isZero();
    assertThat(executionRepository.findByBrokerTransactionId("BR-9001")).isEmpty();
  }

  @Test
  void executedRowWithNoExecutionDataIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9002,EE3600109435,IE00BFG1TM61,BR-9002,BUY,ETF,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,,,,,,,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(
                2, "Terminal order missing execution data: orderId=GAS-9002"));
    assertThat(result.ordersCreated()).isZero();
  }

  @Test
  void executedRowWithBlankUnitPriceIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9003,EE3600109435,IE00BFG1TM61,BR-9003,BUY,ETF,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,10.000000,,1000.00,995.00,5.00,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(
                2, "Missing value: column=unit_price, orderId=GAS-9003"));
    assertThat(result.ordersCreated()).isZero();
  }

  @Test
  void executedEtfBuyWithBlankExecutedQuantityIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9004,EE3600109435,IE00BFG1TM61,BR-9004,BUY,ETF,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,1000.00,995.00,5.00,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(
                2, "Missing value: column=executed_quantity, orderId=GAS-9004"));
    assertThat(result.ordersCreated()).isZero();
  }

  @Test
  void executedSellWithBlankExecutedQuantityIsRejected() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9005,EE3600109443,IE0009FT4LX4,BR-9005,SELL,FUND,1000.00,10.000000,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,1000.00,995.00,5.00,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(
                2, "Missing value: column=executed_quantity, orderId=GAS-9005"));
    assertThat(result.ordersCreated()).isZero();
  }

  @Test
  void executedFundBuyWithBlankExecutedQuantityButPopulatedTotalConsiderationSucceeds() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9006,EE3600109443,IE0009FT4LX4,BR-9006,BUY,FUND,5000.00,,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,5000.00,4995.00,5.00,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.ordersCreated()).isEqualTo(1);
    TransactionExecution execution =
        executionRepository.findByBrokerTransactionId("BR-9006").orElseThrow();
    assertThat(execution.getExecutedQuantity()).isNull();
    assertThat(execution.getTotalConsideration()).isEqualByComparingTo("5000.00");
  }

  @Test
  void nonTerminalRowsWithBlankTimestampAndEconomicsSucceed() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9007,EE3600109435,IE00BFG1TM61,,BUY,ETF,1000.00,10.000000,,PENDING,2025-03-12,,,,,,,,
        GAS-9008,EE3600109435,IE00BFG1TM61,,BUY,ETF,1000.00,10.000000,,SENT,2025-03-12,,,,,,,,
        GAS-9009,EE3600109435,IE00BFG1TM61,,BUY,ETF,1000.00,10.000000,,CANCELLED,2025-03-12,,,,,,,,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.ordersCreated()).isEqualTo(3);
  }

  @Test
  void terminalFundBuyWithoutInstrumentTypeColumnResolvesTypeFromInstrumentReference() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9101,EE3600109443,IE0009FT4LX4,BR-9101,BUY,5000.00,,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,5000.00,4995.00,5.00,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.ordersCreated()).isEqualTo(1);
    TransactionOrder order = orderRepository.findByInstrumentIsin("IE0009FT4LX4").getFirst();
    assertThat(order.getInstrumentType()).isEqualTo(InstrumentType.FUND);
  }

  @Test
  void etfBuyWithoutInstrumentTypeColumnStillRequiresExecutedQuantity() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9102,EE3600109443,IE000I9HGDZ3,BR-9102,BUY,5000.00,,2025-03-10 09:00:00,EXECUTED,2025-03-12,,2025-03-10 14:30:00,,100.00,5000.00,4995.00,5.00,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(
                2, "Missing value: column=executed_quantity, orderId=GAS-9102"));
    assertThat(result.ordersCreated()).isZero();
  }

  @Test
  void unknownInstrumentIsinWithoutInstrumentTypeColumnIsRejectedWithoutEtfGuess() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9103,EE3600109443,XX0000000099,,BUY,5000.00,,2025-03-10 09:00:00,SENT,2025-03-12,,,,,,,,
        """;
    long ordersBefore = orderRepository.count();

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors())
        .containsExactly(
            new HistoricalImportResult.RowError(
                2, "Unknown instrument: instrumentIsin=XX0000000099"));
    assertThat(result.ordersCreated()).isZero();
    assertThat(orderRepository.count()).isEqualTo(ordersBefore);
  }

  @Test
  void explicitInstrumentTypeColumnWinsOverInstrumentReference() {
    String csv =
        """
        order_id,fund_isin,instrument_isin,transaction_id,transaction_type,instrument_type,order_amount,order_quantity,order_timestamp,order_status,expected_settlement_date,actual_settlement_date,execution_timestamp,executed_quantity,unit_price,total_consideration,net_settlement_amount,commission_amount,comment
        GAS-9104,EE3600109443,IE000I9HGDZ3,,BUY,FUND,5000.00,,2025-03-10 09:00:00,SENT,2025-03-12,,,,,,,,
        """;

    HistoricalImportResult result = importService.importCsv(csv);

    assertThat(result.errors()).isEmpty();
    TransactionOrder order = orderRepository.findByInstrumentIsin("IE000I9HGDZ3").getFirst();
    assertThat(order.getInstrumentType()).isEqualTo(InstrumentType.FUND);
  }

  private TransactionOrder findByComment(String brokerTransactionId) {
    TransactionExecution execution =
        executionRepository.findByBrokerTransactionId(brokerTransactionId).orElseThrow();
    return orderRepository.findById(execution.getOrderId()).orElseThrow();
  }
}
