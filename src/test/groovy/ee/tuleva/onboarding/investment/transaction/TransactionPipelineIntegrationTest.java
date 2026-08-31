package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.DRAFT;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.CALCULATED;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.nio.charset.StandardCharsets.UTF_8;
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
class TransactionPipelineIntegrationTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TEST_DATE = LocalDate.of(2026, 2, 10);
  private static final Instant FIXED_INSTANT =
      TEST_DATE.atStartOfDay(TALLINN).toInstant().plusSeconds(3600);
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, TALLINN);

  private static final BigDecimal EXPECTED_SELL_IE000F60HVH9 = new BigDecimal("118094.61");
  private static final BigDecimal EXPECTED_SELL_IE00BJZ2DC62 = new BigDecimal("127582.57");
  private static final BigDecimal EXPECTED_BUY_IE000O58J820 = new BigDecimal("124291.20");
  private static final BigDecimal EXPECTED_BUY_IE00BFG1TM61 = new BigDecimal("116881.35");
  private static final BigDecimal EXPECTED_BUY_IE00BMDBMY19 = new BigDecimal("85266.68");
  private static final BigDecimal EXPECTED_BUY_LU1291099718 = new BigDecimal("269536.01");
  private static final BigDecimal EXPECTED_FT_TOTAL = new BigDecimal("639504.39");
  private static final BigDecimal ESGM_PRICE = new BigDecimal("45.00");

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    Clock testClock() {
      return FIXED_CLOCK;
    }
  }

  @Autowired private TransactionPreparationService preparationService;
  @Autowired private TransactionBatchFinalizer batchFinalizer;
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
    assertThat(result.batch().getStatus()).isEqualTo(DRAFT);
    assertThat(command.getStatus()).isEqualTo(CALCULATED);

    List<TransactionOrder> orders = result.orders();
    assertThat(orders).hasSize(6);

    Map<String, TransactionOrder> ordersByIsin =
        orders.stream().collect(toMap(TransactionOrder::getInstrumentIsin, order -> order));

    BigDecimal tolerance = BigDecimal.ONE;

    assertSellOrder(ordersByIsin, "IE000F60HVH9", EXPECTED_SELL_IE000F60HVH9, ETF, FT, tolerance);
    assertSellOrder(ordersByIsin, "IE00BJZ2DC62", EXPECTED_SELL_IE00BJZ2DC62, ETF, FT, tolerance);
    assertBuyOrder(ordersByIsin, "IE000O58J820", EXPECTED_BUY_IE000O58J820, ETF, FT, tolerance);
    assertBuyOrder(ordersByIsin, "IE00BFG1TM61", EXPECTED_BUY_IE00BFG1TM61, FUND, SEB, tolerance);
    assertBuyOrder(ordersByIsin, "IE00BMDBMY19", EXPECTED_BUY_IE00BMDBMY19, ETF, SEB, tolerance);
    assertBuyOrder(ordersByIsin, "LU1291099718", EXPECTED_BUY_LU1291099718, ETF, FT, tolerance);

    entityManager.flush();
    var auditEvents = auditEventRepository.findByBatchIdOrderByCreatedAt(result.batch().getId());
    assertThat(auditEvents)
        .extracting(TransactionAuditEvent::getEventType)
        .contains("CALCULATION_COMPLETED");
  }

  @Test
  void processCommand_buyMode_producesNoOrdersWhenCashBelowReserve() {
    var command = createCommand(BUY, new BigDecimal("120000.00"));

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

    batchFinalizer.finalizeConfirmedBatch(batch);
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

    byte[] sebFundCsv = decodeExport(batch.getMetadata(), "sebFundXlsx");
    List<String> lines = List.of(new String(sebFundCsv, UTF_8).split("\n", -1));

    var fundOrder =
        orderRepository.findByBatchId(batch.getId()).stream()
            .filter(order -> order.getInstrumentIsin().equals("IE00BFG1TM61"))
            .findFirst()
            .orElseThrow();

    assertThat(lines).anyMatch(line -> line.startsWith("Original reference;"));

    String dataLine =
        lines.stream().filter(line -> line.contains("IE00BFG1TM61")).findFirst().orElseThrow();
    List<String> cells = List.of(dataLine.split(";", -1));
    assertThat(cells.get(0)).isEqualTo(fundOrder.getOrderUuid().toString());
    assertThat(cells.get(5)).isEqualTo("SUBS");
    assertThat(cells.get(9)).isEqualTo("IE00BFG1TM61");
    assertThat(new BigDecimal(cells.get(12)))
        .isCloseTo(EXPECTED_BUY_IE00BFG1TM61, within(BigDecimal.ONE));
    assertThat(fundOrder.getOrderAmount())
        .isCloseTo(EXPECTED_BUY_IE00BFG1TM61, within(BigDecimal.ONE));
  }

  @Test
  void finalizeConfirmedBatch_sebEtfExportContainsCorrectData() throws Exception {
    var batch = runFullPipeline();

    byte[] sebEtfXlsx = decodeExport(batch.getMetadata(), "sebEtfXlsx");
    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(sebEtfXlsx))) {
      Sheet sheet = workbook.getSheetAt(0);

      Map<String, Row> rowsByIsin = new java.util.HashMap<>();
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row != null && row.getCell(2) != null) {
          rowsByIsin.put(row.getCell(2).getStringCellValue(), row);
        }
      }

      assertThat(rowsByIsin).containsKey("IE00BMDBMY19");
      assertThat(rowsByIsin.get("IE00BMDBMY19").getCell(3).getStringCellValue())
          .isEqualTo("ESGM.DE");
      assertThat(rowsByIsin.get("IE00BMDBMY19").getCell(7).getStringCellValue()).isEqualTo("Buy");
      assertThat(
              BigDecimal.valueOf(rowsByIsin.get("IE00BMDBMY19").getCell(6).getNumericCellValue()))
          .isCloseTo(
              EXPECTED_BUY_IE00BMDBMY19.divide(ESGM_PRICE, 6, HALF_UP),
              within(new BigDecimal("1")));

      assertThat(rowsByIsin).containsOnlyKeys("IE00BMDBMY19");
    }
  }

  @Test
  void finalizeConfirmedBatch_ftEtfExportContainsCorrectData() throws Exception {
    var batch = runFullPipeline();

    byte[] ftEtfXlsx = decodeExport(batch.getMetadata(), "ftEtfXlsx");
    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(ftEtfXlsx))) {
      Sheet sheet = workbook.getSheetAt(0);

      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Symbol");
      assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("TD");
      assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("SD");

      Map<String, Row> rowsByIsin = new java.util.HashMap<>();
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row != null
            && row.getCell(6) != null
            && !row.getCell(6).getStringCellValue().isEmpty()) {
          rowsByIsin.put(row.getCell(6).getStringCellValue(), row);
        }
      }

      assertThat(rowsByIsin)
          .containsOnlyKeys("IE00BJZ2DC62", "IE000F60HVH9", "IE000O58J820", "LU1291099718");
      assertThat(rowsByIsin.get("IE00BJZ2DC62").getCell(0).getStringCellValue())
          .isEqualTo("XRSM GY");
      assertThat(rowsByIsin.get("IE00BJZ2DC62").getCell(1).getStringCellValue()).isEqualTo("SELL");
      assertThat(rowsByIsin.get("LU1291099718").getCell(1).getStringCellValue()).isEqualTo("BUY");

      assertThat(
              BigDecimal.valueOf(rowsByIsin.get("IE000F60HVH9").getCell(7).getNumericCellValue()))
          .isCloseTo(EXPECTED_SELL_IE000F60HVH9, within(BigDecimal.ONE));
      assertThat(
              BigDecimal.valueOf(rowsByIsin.get("IE00BJZ2DC62").getCell(7).getNumericCellValue()))
          .isCloseTo(EXPECTED_SELL_IE00BJZ2DC62, within(BigDecimal.ONE));
      assertThat(
              BigDecimal.valueOf(rowsByIsin.get("IE000O58J820").getCell(7).getNumericCellValue()))
          .isCloseTo(EXPECTED_BUY_IE000O58J820, within(BigDecimal.ONE));
      assertThat(
              BigDecimal.valueOf(rowsByIsin.get("LU1291099718").getCell(7).getNumericCellValue()))
          .isCloseTo(EXPECTED_BUY_LU1291099718, within(BigDecimal.ONE));

      BigDecimal totalAmount = ZERO;
      for (Row row : rowsByIsin.values()) {
        totalAmount = totalAmount.add(BigDecimal.valueOf(row.getCell(7).getNumericCellValue()));
      }
      assertThat(totalAmount).isCloseTo(EXPECTED_FT_TOTAL, within(BigDecimal.ONE));
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

    batchFinalizer.finalizeConfirmedBatch(batch);
    entityManager.flush();
    return batch;
  }

  private TransactionCommand createCommand(TransactionMode mode) {
    var command = TransactionCommand.builder().fund(TKF100).mode(mode).asOfDate(TEST_DATE).build();
    return commandRepository.save(command);
  }

  private TransactionCommand createCommand(TransactionMode mode, BigDecimal cash) {
    var command =
        TransactionCommand.builder().fund(TKF100).mode(mode).asOfDate(TEST_DATE).cash(cash).build();
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
