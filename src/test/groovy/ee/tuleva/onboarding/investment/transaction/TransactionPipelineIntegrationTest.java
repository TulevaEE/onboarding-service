package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.AWAITING_CONFIRMATION;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.CALCULATED;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("classpath:db/snapshots/transaction-pipeline/TKF100_2026-02-10.sql")
@Disabled
class TransactionPipelineIntegrationTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TEST_DATE = LocalDate.of(2026, 2, 10);
  private static final Instant FIXED_INSTANT =
      TEST_DATE.atStartOfDay(TALLINN).toInstant().plusSeconds(3600);
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, TALLINN);

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    Clock testClock() {
      return FIXED_CLOCK;
    }
  }

  @Autowired private TransactionPreparationService preparationService;
  @Autowired private TransactionCommandRepository commandRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(FIXED_CLOCK);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void processCommand_rebalanceMode_createsExpectedOrders() {
    var command = createCommand(REBALANCE);

    var result = preparationService.processCommand(command);

    assertThat(result).isNotNull();
    assertThat(result.batch().getStatus()).isEqualTo(AWAITING_CONFIRMATION);
    assertThat(command.getStatus()).isEqualTo(CALCULATED);

    List<TransactionOrder> orders = result.orders();
    assertThat(orders).hasSize(9);

    Map<String, TransactionOrder> ordersByIsin =
        orders.stream().collect(toMap(TransactionOrder::getInstrumentIsin, order -> order));

    BigDecimal tolerance = BigDecimal.ONE;

    assertBuyOrder(ordersByIsin, "IE00BMDBMY19", new BigDecimal("24288"), ETF, SEB, tolerance);
    assertBuyOrder(ordersByIsin, "IE00BFG1TM61", new BigDecimal("53487"), FUND, SEB, tolerance);
    assertSellOrder(ordersByIsin, "IE00BJZ2DC62", new BigDecimal("34875"), ETF, FT, tolerance);
    assertBuyOrder(ordersByIsin, "LU0476289540", new BigDecimal("1675"), ETF, FT, tolerance);
    assertSellOrder(ordersByIsin, "IE000F60HVH9", new BigDecimal("38571"), ETF, FT, tolerance);
    assertSellOrder(ordersByIsin, "IE000O58J820", new BigDecimal("36991"), ETF, FT, tolerance);
    assertBuyOrder(ordersByIsin, "LU1291099718", new BigDecimal("28701"), ETF, FT, tolerance);
    assertBuyOrder(ordersByIsin, "LU1291106356", new BigDecimal("3981"), ETF, SEB, tolerance);
    assertSellOrder(ordersByIsin, "LU1291102447", new BigDecimal("5696"), ETF, SEB, tolerance);

    entityManager.flush();
    var auditEvents = auditEventRepository.findByBatchIdOrderByCreatedAt(result.batch().getId());
    assertThat(auditEvents)
        .extracting(TransactionAuditEvent::getEventType)
        .contains("CALCULATION_COMPLETED");
  }

  @Test
  void processCommand_buyMode_producesNoOrdersWhenCashBelowReserve() {
    var command = createCommand(BUY);

    var result = preparationService.processCommand(command);

    assertThat(result).isNotNull();
    List<TransactionOrder> orders = result.orders();
    assertThat(orders).isEmpty();
  }

  @Test
  void finalizeConfirmedBatch_generatesExportsAndSetsStatusToSent() {
    var command = createCommand(REBALANCE);
    var result = preparationService.processCommand(command);
    entityManager.flush();

    var batch = result.batch();
    batch.setStatus(BatchStatus.CONFIRMED);
    batchRepository.save(batch);
    entityManager.flush();

    preparationService.finalizeConfirmedBatch(batch);
    entityManager.flush();

    assertThat(batch.getStatus()).isEqualTo(SENT);

    List<TransactionOrder> orders = orderRepository.findByBatchId(batch.getId());
    assertThat(orders).allMatch(order -> order.getOrderStatus() == OrderStatus.SENT);
    assertThat(orders).allMatch(order -> order.getOrderTimestamp() != null);
    assertThat(orders).allMatch(order -> order.getExpectedSettlementDate() != null);

    orders.stream()
        .filter(order -> order.getInstrumentType() == ETF)
        .forEach(
            order ->
                assertThat(order.getExpectedSettlementDate()).isEqualTo(LocalDate.of(2026, 2, 12)));

    orders.stream()
        .filter(order -> order.getInstrumentType() == FUND)
        .forEach(
            order ->
                assertThat(order.getExpectedSettlementDate()).isEqualTo(LocalDate.of(2026, 2, 17)));

    Map<String, Object> metadata = batch.getMetadata();
    assertThat(metadata).containsKey("xlsxExport");
    assertThat(metadata).containsKey("sebFundXlsx");
    assertThat(metadata).containsKey("sebEtfXlsx");
    assertThat(metadata).containsKey("ftEtfXlsx");

    var auditEvents = auditEventRepository.findByBatchIdOrderByCreatedAt(batch.getId());
    assertThat(auditEvents)
        .extracting(TransactionAuditEvent::getEventType)
        .contains("BATCH_FINALIZED");
  }

  @Test
  void finalizeConfirmedBatch_sebFundExportContainsCorrectData() throws Exception {
    var batch = runFullPipeline();

    byte[] sebFundXlsx = decodeExport(batch.getMetadata(), "sebFundXlsx");
    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(sebFundXlsx))) {
      Sheet sheet = workbook.getSheetAt(0);

      Row dataRow = sheet.getRow(3);
      assertThat(dataRow).isNotNull();
      assertThat(dataRow.getCell(9).getStringCellValue()).isEqualTo("SUBS");
      assertThat(dataRow.getCell(13).getStringCellValue()).isEqualTo("IE00BFG1TM61");
      assertThat(new BigDecimal(String.valueOf(dataRow.getCell(16).getNumericCellValue())))
          .isCloseTo(new BigDecimal("53487"), within(BigDecimal.ONE));
    }
  }

  @Test
  void finalizeConfirmedBatch_sebEtfExportContainsCorrectData() throws Exception {
    var batch = runFullPipeline();

    byte[] sebEtfXlsx = decodeExport(batch.getMetadata(), "sebEtfXlsx");
    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(sebEtfXlsx))) {
      Sheet sheet = workbook.getSheetAt(0);

      Map<String, Row> rowsByIsin = new java.util.HashMap<>();
      for (int i = 2; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row != null && row.getCell(2) != null) {
          rowsByIsin.put(row.getCell(2).getStringCellValue(), row);
        }
      }

      assertThat(rowsByIsin).containsKey("IE00BMDBMY19");
      assertThat(rowsByIsin.get("IE00BMDBMY19").getCell(3).getStringCellValue())
          .isEqualTo("ESGM.DE");
      assertThat(rowsByIsin.get("IE00BMDBMY19").getCell(7).getStringCellValue()).isEqualTo("BUY");

      assertThat(rowsByIsin).containsKey("LU1291106356");
      assertThat(rowsByIsin.get("LU1291106356").getCell(7).getStringCellValue()).isEqualTo("BUY");

      assertThat(rowsByIsin).containsKey("LU1291102447");
      assertThat(rowsByIsin.get("LU1291102447").getCell(7).getStringCellValue()).isEqualTo("SELL");
    }
  }

  @Test
  void finalizeConfirmedBatch_ftEtfExportContainsCorrectData() throws Exception {
    var batch = runFullPipeline();

    byte[] ftEtfXlsx = decodeExport(batch.getMetadata(), "ftEtfXlsx");
    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(ftEtfXlsx))) {
      Sheet sheet = workbook.getSheetAt(0);

      Map<String, Row> rowsByIsin = new java.util.HashMap<>();
      for (int i = 2; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row != null
            && row.getCell(2) != null
            && !row.getCell(2).getStringCellValue().isEmpty()) {
          rowsByIsin.put(row.getCell(2).getStringCellValue(), row);
        }
      }

      assertThat(rowsByIsin).containsKey("IE00BJZ2DC62");
      assertThat(rowsByIsin).containsKey("LU0476289540");
      assertThat(rowsByIsin).containsKey("IE000F60HVH9");
      assertThat(rowsByIsin).containsKey("IE000O58J820");
      assertThat(rowsByIsin).containsKey("LU1291099718");

      BigDecimal totalAmount = ZERO;
      for (Row row : rowsByIsin.values()) {
        totalAmount = totalAmount.add(BigDecimal.valueOf(row.getCell(5).getNumericCellValue()));
      }
      assertThat(totalAmount).isGreaterThan(ZERO);
    }
  }

  private TransactionBatch runFullPipeline() {
    var command = createCommand(REBALANCE);
    var result = preparationService.processCommand(command);
    entityManager.flush();

    var batch = result.batch();
    batch.setStatus(BatchStatus.CONFIRMED);
    batchRepository.save(batch);
    entityManager.flush();

    preparationService.finalizeConfirmedBatch(batch);
    entityManager.flush();
    return batch;
  }

  private TransactionCommand createCommand(TransactionMode mode) {
    var command = TransactionCommand.builder().fund(TKF100).mode(mode).asOfDate(TEST_DATE).build();
    return commandRepository.save(command);
  }

  private void assertBuyOrder(
      Map<String, TransactionOrder> ordersByIsin,
      String isin,
      BigDecimal expectedAmount,
      InstrumentType expectedInstrumentType,
      OrderVenue expectedVenue,
      BigDecimal tolerance) {
    var order = ordersByIsin.get(isin);
    assertThat(order).isNotNull();
    assertThat(order.getTransactionType()).isEqualTo(TransactionType.BUY);
    assertThat(order.getInstrumentType()).isEqualTo(expectedInstrumentType);
    assertThat(order.getOrderVenue()).isEqualTo(expectedVenue);
    assertThat(order.getOrderAmount()).isCloseTo(expectedAmount, within(tolerance));
  }

  private void assertSellOrder(
      Map<String, TransactionOrder> ordersByIsin,
      String isin,
      BigDecimal expectedAmount,
      InstrumentType expectedInstrumentType,
      OrderVenue expectedVenue,
      BigDecimal tolerance) {
    var order = ordersByIsin.get(isin);
    assertThat(order).isNotNull();
    assertThat(order.getTransactionType()).isEqualTo(SELL);
    assertThat(order.getInstrumentType()).isEqualTo(expectedInstrumentType);
    assertThat(order.getOrderVenue()).isEqualTo(expectedVenue);
    assertThat(order.getOrderAmount()).isCloseTo(expectedAmount, within(tolerance));
  }

  private byte[] decodeExport(Map<String, Object> metadata, String key) {
    return Base64.getDecoder().decode((String) metadata.get(key));
  }
}
