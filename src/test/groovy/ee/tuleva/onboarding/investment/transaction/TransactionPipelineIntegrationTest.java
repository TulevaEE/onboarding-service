package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.DRAFT;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.CALCULATED;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.FAILED;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.SebStatementFixture.currentDayReport;
import static ee.tuleva.onboarding.investment.transaction.SebStatementFixture.endOfDayStatement;
import static ee.tuleva.onboarding.investment.transaction.SebStatementFixture.entry;
import static ee.tuleva.onboarding.investment.transaction.TradePrepPricingFixture.noResolvedPrices;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toMap;
import static org.apache.poi.ss.usermodel.CellType.NUMERIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankEntryReference;
import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.TradeSettlementDetails;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql({
  "classpath:db/snapshots/transaction-pipeline/TKF100_2026-02-10.sql",
  "classpath:db/snapshots/transaction-pipeline/TKF100_2026-02-10_registry.sql"
})
class TransactionPipelineIntegrationTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TEST_DATE = LocalDate.of(2026, 2, 10);
  private static final Instant FIXED_INSTANT =
      TEST_DATE.atStartOfDay(TALLINN).toInstant().plusSeconds(3600);
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, TALLINN);

  private static final BigDecimal EXPECTED_SELL_IE000F60HVH9 = new BigDecimal("118307.04");
  private static final BigDecimal EXPECTED_SELL_IE00BJZ2DC62 = new BigDecimal("127784.21");
  private static final BigDecimal EXPECTED_BUY_IE000O58J820 = new BigDecimal("124132.35");
  private static final BigDecimal EXPECTED_BUY_IE00BFG1TM61 = new BigDecimal("116686.43");
  private static final BigDecimal EXPECTED_BUY_IE00BMDBMY19 = new BigDecimal("85163.40");
  private static final BigDecimal EXPECTED_BUY_LU1291099718 = new BigDecimal("269402.16");
  private static final BigDecimal EXPECTED_FT_TOTAL = new BigDecimal("639625.76");
  private static final BigDecimal ESGM_PRICE = new BigDecimal("45.00");
  private static final BigDecimal SNAPSHOT_RESERVED_UNITS = new BigDecimal("500.90162");
  private static final BigDecimal SNAPSHOT_TKF100_NAV = new BigDecimal("1.00320");
  private static final String EXCLUDED_ISIN = "LU1291099718";

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    Clock testClock() {
      return FIXED_CLOCK;
    }
  }

  @Autowired private PositionPriceResolver positionPriceResolver;
  @Autowired private TransactionPreparationService preparationService;
  @Autowired private TransactionBatchFinalizer batchFinalizer;
  @Autowired private TransactionCommandRepository commandRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionInputService transactionInputService;
  @Autowired private SavingsFundLedger savingsFundLedger;
  @Autowired private TransactionCommandRunner commandRunner;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private BankAccounts bankAccounts;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private TransactionExecutionRepository executionRepository;
  @MockitoBean private OperationsNotificationService notificationService;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(FIXED_CLOCK);
    storeInvestmentAccountMessage(
        "INTRA_DAY_REPORT",
        TEST_DATE,
        FIXED_INSTANT,
        currentDayReport(investmentIban(), TEST_DATE, "500000.00"));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void processCommand_rebalanceMode_createsExpectedOrders() {
    var command = createCommand(REBALANCE);

    var result = (ProcessCommandResult) preparationService.processCommand(command);

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

    var result = (ProcessCommandResult) preparationService.processCommand(command);

    assertThat(result).isNotNull();
    List<TransactionOrder> orders = result.orders();
    assertThat(orders).isEmpty();
  }

  @Test
  void gatherInput_readsTheUnitRegisterBalancesTheSavingsFundLedgerWrites() {
    var saver = new PartyRef(PERSON, "38888888888");
    savingsFundLedger.recordPaymentReceived(saver, new BigDecimal("1000.00"), randomUUID());
    savingsFundLedger.reservePaymentForSubscription(saver, new BigDecimal("1000.00"), randomUUID());
    savingsFundLedger.issueFundUnitsFromReserved(
        saver,
        new BigDecimal("1000.00"),
        new BigDecimal("1000.00000"),
        ONE,
        TEST_DATE,
        randomUUID());
    savingsFundLedger.reserveFundUnitsForRedemption(
        saver, new BigDecimal("400.00000"), randomUUID());
    savingsFundLedger.recordPaymentReceived(saver, new BigDecimal("2500.00"), randomUUID());
    savingsFundLedger.recordUnattributedPayment(new BigDecimal("700.00"), randomUUID());

    var breakdown =
        transactionInputService
            .gatherInput(TKF100, TEST_DATE, Map.of(), noResolvedPrices())
            .liabilityBreakdown();

    assertThat(breakdown.incomingPaymentsClearing()).isEqualByComparingTo("4200.00");
    assertThat(breakdown.unreconciledBankReceipts()).isEqualByComparingTo("700.00");
    assertThat(breakdown.fundUnitsReservedValue())
        .isEqualByComparingTo(
            SNAPSHOT_RESERVED_UNITS.add(new BigDecimal("400.00000")).multiply(SNAPSHOT_TKF100_NAV));
  }

  @Test
  void finalizeConfirmedBatch_generatesExportsAndSetsStatusToSent() {
    var command = createCommand(REBALANCE);
    var result = (ProcessCommandResult) preparationService.processCommand(command);
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
                assertThat(order.getExpectedSettlementDate()).isEqualTo(LocalDate.of(2026, 2, 16)));

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
        if (row != null && row.getCell(1) != null) {
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

      Row totalRow = sheet.getRow(sheet.getLastRowNum());
      assertThat(totalRow.getCell(6).getStringCellValue())
          .isEqualTo("Approximate total order size:");
      assertThat(BigDecimal.valueOf(totalRow.getCell(7).getNumericCellValue()))
          .isCloseTo(EXPECTED_FT_TOTAL, within(BigDecimal.ONE));
    }
  }

  @Test
  void finalizeConfirmedBatch_etfExportsAskForWholeUnits() throws Exception {
    var batch = runFullPipeline();

    assertThat(quantitiesIn(decodeExport(batch.getMetadata(), "ftEtfXlsx"), 6, 2, 8, 9, 10))
        .isNotEmpty()
        .allSatisfy(
            quantity -> {
              assertThat(quantity).isNotNegative();
              assertThat(quantity.remainder(BigDecimal.ONE)).isZero();
            });
    assertThat(quantitiesIn(decodeExport(batch.getMetadata(), "sebEtfXlsx"), 2, 6))
        .isNotEmpty()
        .allSatisfy(
            quantity -> {
              assertThat(quantity).isNotNegative();
              assertThat(quantity.remainder(BigDecimal.ONE)).isZero();
            });
  }

  private static List<BigDecimal> quantitiesIn(byte[] xlsx, int isinColumn, int... quantityColumns)
      throws Exception {
    try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = workbook.getSheetAt(0);
      List<BigDecimal> quantities = new ArrayList<>();
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null
            || row.getCell(isinColumn) == null
            || row.getCell(isinColumn).getStringCellValue().isEmpty()) {
          continue;
        }
        for (int column : quantityColumns) {
          var cell = row.getCell(column);
          if (cell != null && cell.getCellType() == NUMERIC) {
            quantities.add(BigDecimal.valueOf(cell.getNumericCellValue()));
          }
        }
      }
      return quantities;
    }
  }

  private TransactionBatch runFullPipeline() {
    var command = createCommand(REBALANCE);
    var result = (ProcessCommandResult) preparationService.processCommand(command);
    entityManager.flush();

    var batch = result.batch();
    batch.setStatus(BatchStatus.CONFIRMED);
    batchRepository.save(batch);
    entityManager.flush();

    batchFinalizer.finalizeConfirmedBatch(batch);
    entityManager.flush();
    return batch;
  }

  @Test
  void processCommand_withACashOverride_storesItsCommentOnTheCommandAndInTheCompletedAuditEvent() {
    var command = createCommand(BUY, new BigDecimal("120000.00"));

    var result = (ProcessCommandResult) preparationService.processCommand(command);
    entityManager.flush();
    entityManager.clear();

    assertThat(result).isNotNull();
    assertThat(commandRepository.findById(command.getId()))
        .get()
        .extracting(TransactionCommand::getCashComment)
        .isEqualTo("Cash line confirmed with the custodian");
    assertThat(auditEventRepository.findByBatchIdOrderByCreatedAt(result.batch().getId()))
        .filteredOn(event -> "CALCULATION_COMPLETED".equals(event.getEventType()))
        .extracting(event -> event.getPayload().get("cashComment"))
        .containsExactly("Cash line confirmed with the custodian");
  }

  @Test
  void
      processCommand_rebalanceWithAnInstrumentExcluded_givesItsShareToTheOthersAndTradesTheSameNet() {
    var withoutExclusion =
        (ProcessCommandResult) preparationService.processCommand(createCommand(REBALANCE));
    var withExclusion =
        (ProcessCommandResult)
            preparationService.processCommand(createCommand(REBALANCE, Set.of(EXCLUDED_ISIN)));

    assertThat(withoutExclusion).isNotNull();
    assertThat(withExclusion).isNotNull();
    Map<String, BigDecimal> before = signedOrderAmounts(withoutExclusion.orders());
    Map<String, BigDecimal> after = signedOrderAmounts(withExclusion.orders());
    assertThat(before).containsKey(EXCLUDED_ISIN);
    assertThat(after).doesNotContainKey(EXCLUDED_ISIN);
    assertThat(sum(after)).isCloseTo(sum(before), within(ONE));
    assertThat(before.keySet())
        .filteredOn(isin -> !isin.equals(EXCLUDED_ISIN))
        .allSatisfy(
            isin -> assertThat(after.getOrDefault(isin, ZERO)).isGreaterThan(before.get(isin)));
  }

  @Test
  void processCommand_withAnInstrumentExcluded_storesItOnTheCommandAndInTheCompletedAuditEvent() {
    var command = createCommand(REBALANCE, Set.of(EXCLUDED_ISIN));

    var result = (ProcessCommandResult) preparationService.processCommand(command);
    entityManager.flush();
    entityManager.clear();

    assertThat(result).isNotNull();
    assertThat(commandRepository.findById(command.getId()))
        .get()
        .extracting(TransactionCommand::getExcludeIsins)
        .isEqualTo(Set.of(EXCLUDED_ISIN));
    Map<String, Object> payload =
        auditEventRepository.findByBatchIdOrderByCreatedAt(result.batch().getId()).stream()
            .filter(event -> "CALCULATION_COMPLETED".equals(event.getEventType()))
            .findFirst()
            .orElseThrow()
            .getPayload();
    assertThat(payload.get("excludeIsins")).isEqualTo(List.of(EXCLUDED_ISIN));
    assertThat(payload.get("input"))
        .asInstanceOf(MAP)
        .extractingByKey("excludedPositions")
        .asInstanceOf(LIST)
        .singleElement()
        .asInstanceOf(MAP)
        .containsEntry("isin", EXCLUDED_ISIN);
  }

  @Test
  void processCommand_excludingAnInstrumentTheFundDoesNotHave_failsTheCommand() {
    var command = createCommand(REBALANCE, Set.of("IE0000000000"));

    var result = preparationService.processCommand(command);

    assertThat(result).isEqualTo(new CommandOutcome.Failed());
    assertThat(command.getStatus()).isEqualTo(FAILED);
    assertThat(command.getBatchId()).isNull();
    assertThat(auditEventRepository.findAll())
        .filteredOn(event -> String.valueOf(command.getId()).equals(event.getDedupKey()))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getEventType()).isEqualTo("CALCULATION_FAILED");
              assertThat(event.getPayload())
                  .containsKey("input")
                  .containsEntry("excludeIsins", List.of("IE0000000000"));
            });
  }

  @Test
  void aCommandRowWrittenWithoutTheJsonColumns_loadsWithNoAdjustmentsAndNoExclusions() {
    entityManager
        .createNativeQuery(
            "INSERT INTO investment_transaction_command (fund_code, mode, as_of_date, status)"
                + " VALUES ('TKF100', 'BUY', DATE '2026-02-10', 'PENDING')")
        .executeUpdate();

    assertThat(commandRepository.findByStatus(CommandStatus.PENDING))
        .singleElement()
        .satisfies(
            command -> {
              assertThat(command.getManualAdjustments()).isEmpty();
              assertThat(command.getExcludeIsins()).isEmpty();
            });
  }

  @Test
  void savingACashFigureWithoutAComment_isRejectedByTheDatabase() {
    var command =
        TransactionCommand.builder()
            .fund(TKF100)
            .mode(BUY)
            .asOfDate(TEST_DATE)
            .cash(new BigDecimal("120000.00"))
            .build();

    assertThatThrownBy(() -> commandRepository.saveAndFlush(command))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void savingACashCommentWithoutACashFigure_isRejectedByTheDatabase() {
    var command =
        TransactionCommand.builder()
            .fund(TKF100)
            .mode(BUY)
            .asOfDate(TEST_DATE)
            .cashComment("Cash line confirmed with the custodian")
            .build();

    assertThatThrownBy(() -> commandRepository.saveAndFlush(command))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aHeldModelInstrumentWithNoPriceInIndexValuesStopsTheFundAndSaysWhichOne() {
    jdbcClient.sql("DELETE FROM index_values WHERE key = 'PAC.DE'").update();

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    assertThat(response.batchId()).isNull();
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            Hind puudub: LU1291106356: kontrolli hinnaallikaid ja instrument_reference'it\
            """,
            INVESTMENT);
  }

  @Test
  void aHoldingTheRegistryAndTheSebReportDisagreeOnStopsTheFundAndShowsBothQuantities() {
    registryQuantity("IE00BMDBMY19", "10010.0000");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    assertThat(response.errorMessage())
        .isEqualTo(
            "Trade prep inputs missing or stale, no DRAFT made: fund=TKF100, asOfDate=2026-02-10:"
                + " IE00BMDBMY19: registris 10010, SEB raportis 10000 (2026-02-10): kontrolli"
                + " registri tehinguid");
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            IE00BMDBMY19: registris 10010, SEB raportis 10000 (2026-02-10): kontrolli registri tehinguid\
            """,
            INVESTMENT);
  }

  @Test
  void aRegistryWithoutTheHoldingsOfTheReportTradePrepUsesStopsTheFundEvenWhenThatReportIsNewer() {
    jdbcClient
        .sql("DELETE FROM investment_portfolio_cost_basis WHERE fund_isin = :fundIsin")
        .param("fundIsin", TKF100.getIsin())
        .update();

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            Registris pole 2026-02-10 positsioone: kontrolli portfelli baseline'i ja cost-basis tööd\
            """,
            INVESTMENT);
  }

  @Test
  void aSecurityRowWithoutAQuantityStopsTheFundInsteadOfBeingLeftOutOfTheComparison() {
    jdbcClient
        .sql(
            "UPDATE investment_fund_position SET quantity = NULL"
                + " WHERE fund_code = 'TKF100' AND account_id = 'LU0476289540'")
        .update();

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            SEB raportis pole kogust (2026-02-10): LU0476289540: kontrolli raporti importi\
            """,
            INVESTMENT);
  }

  @Test
  void anInstrumentSoldOutOfTheFundAtZeroInOneSourceAndAbsentFromTheOtherMakesADraft() {
    registryRow("IE00BFNM3G45", "0.0000");
    sebSecurityRow("IE0009FT4LX4", "0.000000");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
  }

  @Test
  void aHoldingTheSebReportDoesNotListSaysItIsMissingThereInsteadOfShowingZero() {
    registryRow("IE00BFNM3G45", "5.0000");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            IE00BFNM3G45: registris 5, SEB raportis puudub (2026-02-10): kontrolli registri tehinguid\
            """,
            INVESTMENT);
  }

  @Test
  void anExcludedInstrumentWithNoPriceAndARegistryMismatchStillLetsTheFundMakeADraft() {
    jdbcClient.sql("DELETE FROM index_values WHERE key = 'PAC.DE'").update();
    registryQuantity("LU1291106356", "10010.0000");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                Set.of("LU1291106356"),
                null,
                null,
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.excludeIsins()).containsExactly("LU1291106356");
  }

  @Test
  void aManualPriceForAnExcludedInstrumentIsRecordedAsNotMeasured() {
    var manual = new PriceOverride("LU1291106356", new BigDecimal("60.12"), "Issuer's NAV");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                Set.of("LU1291106356"),
                List.of(manual),
                null,
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.calculationSnapshot().get("priceOverrides"))
        .asInstanceOf(LIST)
        .singleElement()
        .asInstanceOf(MAP)
        .containsEntry("neededByGate", false)
        .containsEntry("deviationCheck", "SKIPPED_EXCLUDED");
  }

  @Test
  void aManualPriceForAnInstrumentWithNoPriceInIndexValuesLetsTheFundMakeADraftAndIsRecorded() {
    jdbcClient.sql("DELETE FROM index_values WHERE key = 'PAC.DE'").update();
    var manual =
        new PriceOverride("LU1291106356", new BigDecimal("60.12"), "Price from the issuer's site");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, List.of(manual), null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.prices()).containsExactly(manual);
    assertThat(response.calculationSnapshot())
        .containsEntry(
            "priceOverrides",
            List.of(
                Map.of(
                    "isin", "LU1291106356",
                    "price", "60.12",
                    "comment", "Price from the issuer's site",
                    "neededByGate", true,
                    "deviationCheck", "NO_INDEX_PRICE_HISTORY")));
  }

  @Test
  void aManualPriceSizesTheOrderAndIsRecordedBesideTheIndexPriceItReplaced() {
    var indexPrice = positionPriceResolver.resolve("IE000O58J820", TEST_DATE).orElseThrow();
    var manual = new PriceOverride("IE000O58J820", new BigDecimal("5.00"), "Index price is stale");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(manual),
                null,
                null,
                List.of(new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("5.00")))),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    var order =
        response.orders().stream()
            .filter(candidate -> candidate.instrumentIsin().equals("IE000O58J820"))
            .findFirst()
            .orElseThrow();
    assertThat(order.orderQuantity())
        .isEqualByComparingTo(order.orderAmount().divide(new BigDecimal("5.00"), 6, HALF_UP));
    assertThat(response.confirmedPriceDeviations())
        .containsExactly(new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("5.00")));
    assertThat(response.calculationSnapshot())
        .containsEntry(
            "confirmedPriceDeviations",
            List.of(
                Map.of(
                    "isin", "IE000O58J820",
                    "manualPrice", "5.00",
                    "lastIndexPrice", "50.00000",
                    "lastIndexPriceDate", "2026-02-10",
                    "gapPercent", "-90.0")));
    assertThat(response.calculationSnapshot())
        .containsEntry(
            "priceOverrides",
            List.of(
                Map.of(
                    "isin",
                    "IE000O58J820",
                    "price",
                    "5.00",
                    "comment",
                    "Index price is stale",
                    "neededByGate",
                    false,
                    "deviationCheck",
                    "MEASURED",
                    "indexPrice",
                    indexPrice.usedPrice().toPlainString(),
                    "indexPriceDate",
                    indexPrice.priceDate().toString())));
  }

  @Test
  void aManualPriceShowsAsManualInThePriceResolutionsAndTheOutput() {
    var manual = new PriceOverride("IE000O58J820", new BigDecimal("5.00"), "Index price is stale");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(manual),
                null,
                null,
                List.of(new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("5.00")))),
            "operator");

    assertThat(response.calculationSnapshot().get("priceResolutions"))
        .asInstanceOf(LIST)
        .contains(
            Map.of(
                "isin", "IE000O58J820",
                "price", "5.00",
                "priceSource", "MANUAL",
                "comment", "Index price is stale"));
    assertThat(response.calculationSnapshot().get("output"))
        .asInstanceOf(LIST)
        .filteredOn(trade -> "IE000O58J820".equals(((Map<?, ?>) trade).get("isin")))
        .singleElement()
        .asInstanceOf(MAP)
        .containsEntry("price", "5.00")
        .containsEntry("priceSource", "MANUAL");
  }

  @Test
  void aManualPriceForAnInstrumentTheFundNeitherHoldsNorHasInItsModelFailsTheCommand() {
    var stranger = new PriceOverride("IE0000000000", new BigDecimal("1"), "Wrong instrument");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(stranger),
                null,
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    assertThat(auditEventRepository.findAll())
        .filteredOn(event -> String.valueOf(response.id()).equals(event.getDedupKey()))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getPayload())
                    .containsEntry(
                        "prices",
                        List.of(
                            Map.of(
                                "isin",
                                "IE0000000000",
                                "price",
                                "1",
                                "comment",
                                "Wrong instrument"))));
  }

  @Test
  void anAcceptedHoldingDifferenceLetsTheFundMakeADraftAndIsRecordedWithBothQuantities() {
    registryQuantity("IE00BMDBMY19", "10010.0000");
    var accepted =
        new AcceptedHoldingDifferences(
            Set.of("IE00BMDBMY19", "LU1291099718"), "Fill booked in the registry a day early");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, accepted, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.acceptedHoldingDifferences()).isEqualTo(accepted);
    assertThat(response.acceptedHoldingDifferences().isins())
        .containsExactly("IE00BMDBMY19", "LU1291099718");
    assertThat(response.calculationSnapshot())
        .containsEntry(
            "acceptedHoldingDifferences",
            Map.of(
                "comment",
                "Fill booked in the registry a day early",
                "instruments",
                List.of(
                    Map.of(
                        "isin", "IE00BMDBMY19",
                        "neededByGate", true,
                        "registryQuantity", "10010.0000",
                        "reportQuantity", "10000.00000000"),
                    Map.of("isin", "LU1291099718", "neededByGate", false))));
  }

  @Test
  void aDifferenceInAnInstrumentTheOverrideDoesNotNameStillStopsTheFund() {
    registryQuantity("IE00BMDBMY19", "10010.0000");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                null,
                new AcceptedHoldingDifferences(Set.of("LU1291099718"), "Yesterday's difference"),
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            IE00BMDBMY19: registris 10010, SEB raportis 10000 (2026-02-10): kontrolli registri tehinguid            """,
            INVESTMENT);
  }

  @Test
  void anEmptyRegistryPassesOnlyWhenTheOverrideNamesEveryHoldingOfTheReport() {
    jdbcClient
        .sql("DELETE FROM investment_portfolio_cost_basis WHERE fund_isin = :fundIsin")
        .param("fundIsin", TKF100.getIsin())
        .update();
    Set<String> reportHoldings =
        Set.copyOf(
            jdbcClient
                .sql(
                    """
                    SELECT account_id FROM investment_fund_position
                    WHERE fund_code = 'TKF100' AND nav_date = :navDate
                      AND account_type = 'SECURITY' AND quantity <> 0
                    """)
                .param("navDate", TEST_DATE)
                .query(String.class)
                .list());

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                null,
                new AcceptedHoldingDifferences(reportHoldings, "Baseline loads tonight"),
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
  }

  @Test
  void theCommonDateRuleValuesEveryInstrumentAtTheLatestDayTheyAllHaveAPriceOn() {
    jdbcClient
        .sql(
            """
            INSERT INTO index_values (key, date, value, provider, updated_at)
            SELECT key, DATE '2026-02-09', value * 2, provider, updated_at
            FROM index_values WHERE date = DATE '2026-02-10' AND provider = 'EODHD'
            """)
        .update();
    jdbcClient
        .sql("DELETE FROM index_values WHERE key = 'ESGM.DE' AND date = DATE '2026-02-10'")
        .update();

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                null,
                null,
                PriceDateRule.LATEST_COMMON_DATE,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.priceDateRule()).isEqualTo(PriceDateRule.LATEST_COMMON_DATE);
    assertThat(response.calculationSnapshot()).containsEntry("commonPriceDate", "2026-02-09");
    assertThat(response.calculationSnapshot().get("priceResolutions"))
        .asInstanceOf(LIST)
        .isNotEmpty()
        .allSatisfy(
            resolution ->
                assertThat(resolution).asInstanceOf(MAP).containsEntry("priceDate", "2026-02-09"));
  }

  @Test
  void theCommonDateRuleStopsTheFundWhenNoDayHasEveryPrice() {
    jdbcClient
        .sql(
            "UPDATE index_values SET date = DATE '2026-02-09'"
                + " WHERE key = 'ESGM.DE' AND date = DATE '2026-02-10'")
        .update();

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                null,
                null,
                PriceDateRule.LATEST_COMMON_DATE,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            Ühist hinnapäeva pole (2026-01-27–2026-02-10): kontrolli hinnaallikaid või kasuta igaühe värskeimat hinda\
            """,
            INVESTMENT);
  }

  @Test
  void aManualPriceStillWinsUnderTheCommonDateRuleAndIsLeftOutOfTheSearch() {
    jdbcClient
        .sql(
            "UPDATE index_values SET date = DATE '2026-02-09'"
                + " WHERE key = 'ESGM.DE' AND date = DATE '2026-02-10'")
        .update();

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(new PriceOverride("IE00BMDBMY19", new BigDecimal("44"), "Issuer's NAV")),
                null,
                PriceDateRule.LATEST_COMMON_DATE,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.calculationSnapshot()).containsEntry("commonPriceDate", "2026-02-10");
    var order =
        response.orders().stream()
            .filter(candidate -> candidate.instrumentIsin().equals("IE00BMDBMY19"))
            .findFirst()
            .orElseThrow();
    assertThat(order.orderQuantity())
        .isEqualByComparingTo(order.orderAmount().divide(new BigDecimal("44"), 6, HALF_UP));
  }

  @Test
  void aBalanceSebReportedTodayIsTheRunsCashAndEachForecastShowsWhatItWasBeforeNetting() {
    gatewayRunTheDayAfterTheReport(
        entry("SETTLEMENT-1", "120000.00", "DBIT", "BOOK"),
        entry("SETTLEMENT-2", "15000.00", "DBIT", "BOOK"),
        entry("SETTLEMENT-3", "30000.00", "CRDT", "BOOK"));
    tradeShownInTheReport("SYNTHETIC-1", "IE00BFG1TM61");
    bankBooksTradeSettlement("SETTLEMENT-1", "SYNTHETIC-1", "-120000.00");
    bankBooksTradeSettlement("SETTLEMENT-2", "SYNTHETIC-2", "-15000.00");
    bankBooksTradeSettlement("SETTLEMENT-3", "SYNTHETIC-3", "30000.00");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.calculationSnapshot().get("cash"))
        .isEqualTo(
            Map.of(
                "source",
                "GATEWAY_BALANCE",
                "reportCash",
                "500000.00",
                "gateway",
                Map.of(
                    "status", "CURRENT_DAY_BALANCE",
                    "balance", "520000.00",
                    "fetchedAt", "2026-02-09T23:00:00Z"),
                "gatewayMinusReportCash",
                "20000.00",
                "balanceUsed",
                "490000.00",
                "forecasts",
                List.of(
                    Map.of(
                        "name", "unsettledTradePayables",
                        "before", "300000.00",
                        "after", "180000.00",
                        "treatment", "NETTED_AGAINST_THE_BALANCE")),
                "unmatchedTradeSettlements",
                List.of(
                    Map.of(
                        "brokerReference", "SYNTHETIC-2",
                        "isin", "IE00BFG1TM61",
                        "bookingDate", "2026-02-10",
                        "cash", "-15000.00",
                        "treatment", "NOT_NETTED"),
                    Map.of(
                        "brokerReference", "SYNTHETIC-3",
                        "isin", "IE00BFG1TM61",
                        "bookingDate", "2026-02-10",
                        "cash", "30000.00",
                        "treatment", "EXCLUDED_FROM_THE_BALANCE"))));
    assertThat(response.calculationSnapshot().get("input"))
        .asInstanceOf(MAP)
        .containsEntry("appliedCash", "490000.00");
    assertThat(response.calculationSnapshot().get("calculationWarnings"))
        .asInstanceOf(LIST)
        .contains(
            Map.of(
                "type",
                "UNMATCHED_TRADE_SETTLEMENT",
                "message",
                "⚠️ SEB booked a trade settlement no registered trade explains, its cash is left"
                    + " out of the balance: fund=TKF100, isin=IE00BFG1TM61,"
                    + " bookingDate=2026-02-10, amount=30000.00"));
  }

  @Test
  void anUnexplainedTradeSettlementOutflowLeavesTheBalanceAsItIsButIsWarnedAbout() {
    gatewayRunTheDayAfterTheReport(entry("SETTLEMENT-2", "15000.00", "DBIT", "BOOK"));
    bankBooksTradeSettlement("SETTLEMENT-2", "SYNTHETIC-2", "-15000.00");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.calculationSnapshot().get("input"))
        .asInstanceOf(MAP)
        .containsEntry("appliedCash", "520000.00");
    assertThat(response.calculationSnapshot().get("cash"))
        .asInstanceOf(MAP)
        .doesNotContainKey("balanceUsed");
    assertThat(response.calculationSnapshot().get("calculationWarnings"))
        .asInstanceOf(LIST)
        .contains(
            Map.of(
                "type",
                "UNMATCHED_TRADE_SETTLEMENT",
                "message",
                "⚠️ SEB booked a trade settlement no registered trade explains, the report's"
                    + " payable for it stays reserved: fund=TKF100, isin=IE00BFG1TM61,"
                    + " bookingDate=2026-02-10, amount=-15000.00"));
  }

  private void gatewayRunTheDayAfterTheReport(String... entries) {
    jdbcClient.sql("DELETE FROM banking_message").update();
    storeInvestmentAccountMessage(
        "INTRA_DAY_REPORT",
        TEST_DATE,
        FIXED_INSTANT,
        currentDayReport(investmentIban(), TEST_DATE, "520000.00", entries));
    jdbcClient
        .sql(
            "UPDATE investment_fund_position SET nav_date = DATE '2026-02-09'"
                + " WHERE fund_code = 'TKF100' AND nav_date = DATE '2026-02-10'")
        .update();
    jdbcClient
        .sql(
            "UPDATE investment_portfolio_cost_basis SET as_of_date = DATE '2026-02-09'"
                + " WHERE as_of_date = DATE '2026-02-10'")
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_position
              (nav_date, fund_code, account_type, account_name, account_id, quantity,
               market_price, market_value, currency, created_at)
            VALUES (DATE '2026-02-09', 'TKF100', 'LIABILITY',
               'Total payables of unsettled transactions', 'PAYABLES', 0, 0, -300000.00, 'EUR',
               CURRENT_TIMESTAMP)
            """)
        .update();
  }

  private void bankBooksTradeSettlement(String externalId, String brokerReference, String cash) {
    fundBankLedger.recordTradeSettlement(
        TKF100,
        new BigDecimal(cash),
        new BigDecimal("100"),
        BankEntryReference.of(investmentIban(), externalId),
        FUND_INVESTMENT_CASH_CLEARING,
        new TradeSettlementDetails(
            brokerReference, "IE00BFG1TM61", "TICK", "test instrument", BigDecimal.ONE),
        TEST_DATE);
  }

  @Test
  void aGatewayBalanceFarFromTheReportCashIsShownWithItsDifferenceAndAWarning() {
    jdbcClient.sql("DELETE FROM banking_message").update();
    storeInvestmentAccountMessage(
        "INTRA_DAY_REPORT",
        TEST_DATE,
        FIXED_INSTANT,
        currentDayReport(investmentIban(), TEST_DATE, "600000.00"));
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, quantity,
               market_value, currency, calculation_id, published_at)
            VALUES (DATE '2026-02-09', 'TKF100', 'UNITS', 'Fund units', 'TKF100', 1,
               5000000.00, 'EUR', :calculationId, CURRENT_TIMESTAMP)
            """)
        .param("calculationId", randomUUID())
        .update();

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.calculationSnapshot().get("cash"))
        .asInstanceOf(MAP)
        .containsEntry("reportCash", "500000.00")
        .containsEntry("gatewayMinusReportCash", "100000.00");
    assertThat(response.calculationSnapshot().get("calculationWarnings"))
        .asInstanceOf(LIST)
        .contains(
            Map.of(
                "type",
                "CASH_SOURCES_DIFFER",
                "message",
                "⚠️ SEB current-day balance differs from the position report's cash by more than"
                    + " 1% of NAV: fund=TKF100, gatewayBalance=600000.00, reportCash=500000.00,"
                    + " difference=100000.00, nav=5000000.00"));
  }

  @Test
  void withoutACurrentDayReportForTodayTheRunUsesReportCashAndSaysSo() {
    jdbcClient.sql("DELETE FROM banking_message").update();
    storeInvestmentAccountMessage(
        "HISTORIC_STATEMENT",
        TEST_DATE.minusDays(1),
        FIXED_INSTANT,
        endOfDayStatement(investmentIban(), TEST_DATE.minusDays(1), "500000.00"));

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.calculationSnapshot())
        .containsEntry(
            "cash",
            Map.of(
                "source",
                "POSITION_REPORT",
                "reportCash",
                "500000.00",
                "gateway",
                Map.of("status", "NO_CURRENT_DAY_REPORT_TODAY")));
  }

  @Test
  void aCurrentDayReportThatCannotBeReadLeavesTheRunOnReportCashAndSaysSo() {
    jdbcClient.sql("DELETE FROM banking_message").update();
    storeInvestmentAccountMessage("INTRA_DAY_REPORT", TEST_DATE, FIXED_INSTANT, "<not-a-report/>");

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.calculationSnapshot())
        .containsEntry(
            "cash",
            Map.of(
                "source",
                "POSITION_REPORT",
                "reportCash",
                "500000.00",
                "gateway",
                Map.of("status", "CURRENT_DAY_REPORT_UNREADABLE")));
  }

  @Test
  void aCashAmountWithItsCommentLetsTheFundThroughWithoutABalanceFetchedToday() {
    jdbcClient.sql("DELETE FROM banking_message").update();

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                new CashOverride(
                    new BigDecimal("500000.00"), "Balance read from SEB's internet bank"),
                null,
                null,
                null,
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.calculationSnapshot().get("cash"))
        .asInstanceOf(MAP)
        .containsEntry("source", "OPERATOR_AMOUNT");
  }

  private void tradeShownInTheReport(String brokerReference, String isin) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin(isin)
                .transactionType(TransactionType.BUY)
                .instrumentType(InstrumentType.ETF)
                .orderQuantity(new BigDecimal("1000"))
                .orderVenue(OrderVenue.SEB)
                .orderStatus(OrderStatus.SETTLED)
                .orderTimestamp(Instant.parse("2026-02-09T09:00:00Z"))
                .expectedSettlementDate(TEST_DATE)
                .build());
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId(brokerReference)
            .executionTimestamp(Instant.parse("2026-02-09T10:00:00Z"))
            .executedQuantity(new BigDecimal("1000"))
            .totalConsideration(new BigDecimal("120000.00"))
            .source("SEB_OOTEL")
            .reportedDate(LocalDate.of(2026, 2, 9))
            .build());
  }

  private String investmentIban() {
    return bankAccounts.getIban(TKF100, FUND_INVESTMENT_EUR);
  }

  private void storeInvestmentAccountMessage(
      String messageType, LocalDate statementDate, Instant receivedAt, String rawResponse) {
    jdbcClient
        .sql(
            """
            insert into banking_message (id, bank_type, request_id, tracking_id, raw_response,
              timezone, message_type, account_iban, statement_from, statement_to, processed_at,
              received_at)
            values (:id, 'SEB', 'request', 'tracking', :rawResponse, 'Europe/Tallinn',
              :messageType, :iban, :day, :day, :receivedAt, :receivedAt)
            """)
        .param("id", randomUUID())
        .param("rawResponse", rawResponse)
        .param("messageType", messageType)
        .param("iban", investmentIban())
        .param("day", statementDate)
        .param("receivedAt", Timestamp.from(receivedAt))
        .update();
  }

  @Test
  void aSebReportWithoutSecuritiesStopsTheFundEvenWhenEveryHoldingIsAccepted() {
    jdbcClient
        .sql(
            "DELETE FROM investment_fund_position WHERE fund_code = 'TKF100'"
                + " AND nav_date = :navDate AND account_type = 'SECURITY'")
        .param("navDate", TEST_DATE)
        .update();
    Set<String> registryHoldings =
        Set.copyOf(
            jdbcClient
                .sql(
                    "SELECT instrument_isin FROM investment_portfolio_cost_basis"
                        + " WHERE fund_isin = :fundIsin AND quantity <> 0")
                .param("fundIsin", TKF100.getIsin())
                .query(String.class)
                .list());

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                null,
                new AcceptedHoldingDifferences(registryHoldings, "Report is late"),
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            SEB raportis pole 2026-02-10 väärtpabereid: kontrolli raporti importi\
            """,
            INVESTMENT);
  }

  @Test
  void excludingTheOnlyInstrumentSebListsLeavesTheOtherDifferencesOpenToAcceptance() {
    String onlyListed = "IE00BMDBMY19";
    jdbcClient
        .sql(
            "DELETE FROM investment_fund_position WHERE fund_code = 'TKF100'"
                + " AND nav_date = :navDate AND account_type = 'SECURITY'"
                + " AND account_id <> :onlyListed")
        .param("navDate", TEST_DATE)
        .param("onlyListed", onlyListed)
        .update();
    Set<String> unlisted =
        Set.copyOf(
            jdbcClient
                .sql(
                    "SELECT instrument_isin FROM investment_portfolio_cost_basis"
                        + " WHERE fund_isin = :fundIsin AND quantity <> 0"
                        + " AND instrument_isin <> :onlyListed")
                .param("fundIsin", TKF100.getIsin())
                .param("onlyListed", onlyListed)
                .query(String.class)
                .list());

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                Set.of(onlyListed),
                null,
                new AcceptedHoldingDifferences(unlisted, "Report sent one row"),
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
  }

  @Test
  void aManualPriceFarFromTheLastIndexPriceStopsTheFundAndSaysByHowMuch() {
    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(
                    new PriceOverride(
                        "IE000O58J820", new BigDecimal("5.00"), "Index price is stale")),
                null,
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            ⚠️ IE000O58J820: käsitsi hind 5, viimane indeksihind 50 (2026-02-10), erinevus -90.0%: kontrolli hinda või kinnita see (confirmedPriceDeviations)\
            """,
            INVESTMENT);
  }

  @Test
  void aManualPriceIsComparedWithTheLastIndexPriceHoweverOldItIs() {
    jdbcClient
        .sql("UPDATE index_values SET date = DATE '2025-12-01' WHERE key = 'PAC.DE'")
        .update();

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(new PriceOverride("LU1291106356", new BigDecimal("60.12"), "Issuer's NAV")),
                null,
                null,
                null),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            ⚠️ LU1291106356: käsitsi hind 60.12, viimane indeksihind 10 (2025-12-01), erinevus 501.2%: kontrolli hinda või kinnita see (confirmedPriceDeviations)\
            """,
            INVESTMENT);
  }

  @Test
  void
      aConfirmationForAnotherManualPriceDoesNotLetTheNewPriceThroughAndTheStopSaysWhichPriceItConfirmed() {
    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(
                    new PriceOverride(
                        "IE000O58J820", new BigDecimal("5.00"), "Index price is stale")),
                null,
                null,
                List.of(new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("6.00")))),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            ⚠️ IE000O58J820: käsitsi hind 5, viimane indeksihind 50 (2026-02-10), erinevus -90.0%, kinnitus on hinnale 6: kontrolli hinda või kinnita see (confirmedPriceDeviations)\
            """,
            INVESTMENT);
  }

  @Test
  void aStaleConfirmationBesideOneForTheCurrentManualPriceLetsThePriceThrough() {
    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(
                    new PriceOverride(
                        "IE000O58J820", new BigDecimal("5.00"), "Index price is stale")),
                null,
                null,
                List.of(
                    new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("10.00")),
                    new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("5.00")))),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
  }

  @Test
  void whenNoConfirmationMatchesTheStopNamesEveryPriceThatWasConfirmed() {
    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(
                    new PriceOverride(
                        "IE000O58J820", new BigDecimal("5.00"), "Index price is stale")),
                null,
                null,
                List.of(
                    new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("7.00")),
                    new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("6.00")))),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    verify(notificationService)
        .sendMessage(
            """
            🔴 TKF100 tehingute mustandit ei tehtud (2026-02-10). Käivita ettevalmistus uuesti, kui sisend on korras:
            ⚠️ IE000O58J820: käsitsi hind 5, viimane indeksihind 50 (2026-02-10), erinevus -90.0%, kinnitused on hindadele 6, 7: kontrolli hinda või kinnita see (confirmedPriceDeviations)\
            """,
            INVESTMENT);
  }

  @Test
  void anOverdueSentSaleIsTakenOffThePositionBeforeTheCalculationSoTheEngineSellsOnlyWhatIsLeft() {
    sellTheFundInstrumentOut();
    sentSaleOfTheFundInstrument(LocalDate.of(2026, 2, 9));

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100, REBALANCE, TEST_DATE, null, null, null, null, null, null, null),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(engineTradeAmount(response.calculationSnapshot(), "IE00BFG1TM61"))
        .isEqualByComparingTo("-300000");
    var sale = saleOfTheFundInstrument(response);
    assertThat(sale.orderQuantity()).isEqualByComparingTo("15000");
    assertThat(sale.orderAmount()).isEqualByComparingTo("300000");
    assertThat(((Map<?, ?>) response.calculationSnapshot().get("input")).get("deductedSales"))
        .isEqualTo(deductedSaleOfTheFundInstrument());
    assertThat(response.calculationSnapshot().get("sizedSales"))
        .isEqualTo(List.of(sizedSaleOfTheFundInstrument("20", "300000", "300000")));
  }

  @Test
  void aSentSaleInItsWindowComesOffAtTheReportsUnitValueAndTheRestIsValuedAtTheManualPrice() {
    sellTheFundInstrumentOut();
    sentSaleOfTheFundInstrument(LocalDate.of(2026, 2, 12));

    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                List.of(new PriceOverride("IE00BFG1TM61", new BigDecimal("15.00"), "Issuer's NAV")),
                null,
                null,
                List.of(new PriceDeviationConfirmation("IE00BFG1TM61", new BigDecimal("15.00")))),
            "operator");

    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(engineTradeAmount(response.calculationSnapshot(), "IE00BFG1TM61"))
        .isEqualByComparingTo("-225000");
    var sale = saleOfTheFundInstrument(response);
    assertThat(sale.orderQuantity()).isEqualByComparingTo("15000");
    assertThat(sale.orderAmount()).isEqualByComparingTo("225000");
    assertThat(((Map<?, ?>) response.calculationSnapshot().get("input")).get("deductedSales"))
        .isEqualTo(deductedSaleOfTheFundInstrument());
    assertThat(
            ((Map<?, ?>) response.calculationSnapshot().get("input")).get("manualPriceValuations"))
        .isEqualTo(
            List.of(
                Map.of(
                    "isin", "IE00BFG1TM61",
                    "units", "15000",
                    "valueBeforeManualPrice", "300000",
                    "manualPrice", "15",
                    "valueUsed", "225000",
                    "valuedAt", "MANUAL_PRICE")));
    assertThat(response.calculationSnapshot().get("sizedSales"))
        .isEqualTo(List.of(sizedSaleOfTheFundInstrument("15", "225000", "225000")));
  }

  private void sellTheFundInstrumentOut() {
    jdbcClient
        .sql(
            "UPDATE investment_model_portfolio_allocation SET weight = 0"
                + " WHERE fund_code = 'TKF100' AND isin = 'IE00BFG1TM61'")
        .update();
    jdbcClient
        .sql(
            "UPDATE investment_model_portfolio_allocation SET weight = 0.29000000"
                + " WHERE fund_code = 'TKF100' AND isin = 'IE00BMDBMY19'")
        .update();
  }

  private void sentSaleOfTheFundInstrument(LocalDate expectedSettlementDate) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(TKF100)
            .instrumentIsin("IE00BFG1TM61")
            .transactionType(TransactionType.SELL)
            .instrumentType(InstrumentType.FUND)
            .orderQuantity(new BigDecimal("30000"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.SENT)
            .orderTimestamp(Instant.parse("2026-02-06T08:00:00Z"))
            .expectedSettlementDate(expectedSettlementDate)
            .build());
  }

  private static BigDecimal engineTradeAmount(Map<String, Object> snapshot, String isin) {
    return ((List<?>) snapshot.get("output"))
        .stream()
            .map(Map.class::cast)
            .filter(trade -> isin.equals(trade.get("isin")))
            .map(trade -> new BigDecimal((String) trade.get("tradeAmount")))
            .findFirst()
            .orElseThrow();
  }

  private static TransactionOrderResponse saleOfTheFundInstrument(
      TransactionCommandResponse response) {
    return response.orders().stream()
        .filter(order -> order.instrumentIsin().equals("IE00BFG1TM61"))
        .findFirst()
        .orElseThrow();
  }

  private static List<Map<String, Object>> deductedSaleOfTheFundInstrument() {
    return List.of(
        Map.of(
            "isin", "IE00BFG1TM61",
            "reportUnits", "45000",
            "reportValue", "900000",
            "unitsInSentSales", "30000",
            "valueDeducted", "600000",
            "deductedBy", "UNITS"));
  }

  private static Map<String, Object> sizedSaleOfTheFundInstrument(
      String price, String engineAmount, String orderAmount) {
    return Map.of(
        "isin",
        "IE00BFG1TM61",
        "reportUnits",
        "45000",
        "unitsInSentSales",
        "30000",
        "quantitySold",
        "15000",
        "price",
        price,
        "engineAmount",
        engineAmount,
        "orderAmount",
        orderAmount,
        "fullSale",
        true);
  }

  @Test
  void aConfirmationForAnInstrumentWithoutAManualPriceFailsTheCommand() {
    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("5.00")))),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
    assertThat(response.confirmedPriceDeviations())
        .containsExactly(new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("5.00")));
  }

  @Test
  void aConfirmationForAnExcludedInstrumentFailsTheCommandLikeOneWithoutAManualPrice() {
    var response =
        commandRunner.createAndProcess(
            new CreateTransactionCommandRequest(
                TKF100,
                REBALANCE,
                TEST_DATE,
                null,
                null,
                Set.of("IE000O58J820"),
                List.of(
                    new PriceOverride(
                        "IE000O58J820", new BigDecimal("5.00"), "Index price is stale")),
                null,
                null,
                List.of(new PriceDeviationConfirmation("IE000O58J820", new BigDecimal("5.00")))),
            "operator");

    assertThat(response.status()).isEqualTo(FAILED);
  }

  private void registryRow(String isin, String quantity) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_portfolio_cost_basis
              (fund_isin, instrument_isin, as_of_date, quantity, avg_unit_cost, total_cost, source)
            VALUES (:fundIsin, :isin, :asOfDate, :quantity, 0, 0, 'TEST')
            """)
        .param("fundIsin", TKF100.getIsin())
        .param("isin", isin)
        .param("asOfDate", TEST_DATE)
        .param("quantity", new BigDecimal(quantity))
        .update();
  }

  private void sebSecurityRow(String isin, String quantity) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_position
              (nav_date, fund_code, account_type, account_name, account_id, quantity,
               market_price, market_value, currency, created_at)
            VALUES (:navDate, 'TKF100', 'SECURITY', :isin, :isin, :quantity, 0, 0, 'EUR',
               CURRENT_TIMESTAMP)
            """)
        .param("navDate", TEST_DATE)
        .param("isin", isin)
        .param("quantity", new BigDecimal(quantity))
        .update();
  }

  private void registryQuantity(String isin, String quantity) {
    jdbcClient
        .sql(
            """
            UPDATE investment_portfolio_cost_basis SET quantity = :quantity
            WHERE fund_isin = :fundIsin AND instrument_isin = :isin AND as_of_date = :asOfDate
            """)
        .param("quantity", new BigDecimal(quantity))
        .param("fundIsin", TKF100.getIsin())
        .param("isin", isin)
        .param("asOfDate", TEST_DATE)
        .update();
  }

  private TransactionCommand createCommand(TransactionMode mode) {
    var command = TransactionCommand.builder().fund(TKF100).mode(mode).asOfDate(TEST_DATE).build();
    return commandRepository.save(command);
  }

  private TransactionCommand createCommand(TransactionMode mode, BigDecimal cash) {
    var command =
        TransactionCommand.builder()
            .fund(TKF100)
            .mode(mode)
            .asOfDate(TEST_DATE)
            .cash(cash)
            .cashComment("Cash line confirmed with the custodian")
            .build();
    return commandRepository.save(command);
  }

  private TransactionCommand createCommand(TransactionMode mode, Set<String> excludeIsins) {
    var command =
        TransactionCommand.builder()
            .fund(TKF100)
            .mode(mode)
            .asOfDate(TEST_DATE)
            .excludeIsins(excludeIsins)
            .build();
    return commandRepository.save(command);
  }

  private static Map<String, BigDecimal> signedOrderAmounts(List<TransactionOrder> orders) {
    return orders.stream()
        .collect(
            toMap(
                TransactionOrder::getInstrumentIsin,
                order ->
                    order.getTransactionType() == SELL
                        ? order.getOrderAmount().negate()
                        : order.getOrderAmount()));
  }

  private static BigDecimal sum(Map<String, BigDecimal> amounts) {
    return amounts.values().stream().reduce(ZERO, BigDecimal::add);
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
