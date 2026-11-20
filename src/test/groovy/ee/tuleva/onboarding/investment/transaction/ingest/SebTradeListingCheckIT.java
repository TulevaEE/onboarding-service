package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.BANK_STATEMENT;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.PENDING_REPORT_ABSENCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.UUID.randomUUID;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.SettlementSource;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.TradeSettlementBookings;
import ee.tuleva.onboarding.ledger.TradeSettlementDetails;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SebIntegrationTest
class SebTradeListingCheckIT {

  private static final String EJAP = "LU1291102447";
  private static final String REFERENCE = "DLA0553690";
  private static final LocalDate SETTLEMENT_DAY = LocalDate.of(2026, 2, 12);
  private static final Instant ORDERED = Instant.parse("2026-02-10T10:00:00Z");
  private static final BooleanSupplier LOCK_HELD = () -> true;
  private static final SebTradeListingCheckProperties ALERTS_FROM_FEBRUARY =
      new SebTradeListingCheckProperties(LocalDate.of(2026, 2, 1));

  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionSettlementRepository settlementRepository;
  @Autowired private TransactionSettlementService settlementService;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private LatestPendingReport latestPendingReport;
  @Autowired private OverdueSettlementDetector overdueSettlementDetector;
  @Autowired private TradeSettlementBookings tradeSettlementBookings;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private BankAccounts bankAccounts;
  @Autowired private PublicHolidays publicHolidays;
  @Autowired private EntityManager entityManager;
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private LocalDate latestAsOfDate = SETTLEMENT_DAY;

  @Test
  void
      aTradeGoneFromSebsListWithNoPaymentOnItsSettlementDay_isAlertedOnce_andARunWithNothingNewPostsNothing() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY, List.of(anotherOrdersRow()));
    SebTradeListingCheck check = checkWithStatementsFor(SETTLEMENT_DAY);

    check.run(LOCK_HELD);
    check.run(LOCK_HELD);

    then(notificationService)
        .should(times(1))
        .sendMessage(
            "⚠️ SEB tehing kadus ootel-tehingutest, pangas pole selle viitega makset: 1\n"
                + "TKF100 · DLA0553690 · ost LU1291102447 · SEB arvelduspäev 12.02.2026\n"
                + "Küsi SEB-lt, miks tehing nimekirjast kadus ja kas see arveldub.",
            INVESTMENT,
            WARNING);
  }

  @Test
  void runsAfterTheSebPendingImport() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY, List.of(anotherOrdersRow()));
    Clock clock = morningOf(SETTLEMENT_DAY.plusDays(1));
    SebTradeListingCheckListener listener =
        new SebTradeListingCheckListener(
            clock,
            check(
                ALERTS_FROM_FEBRUARY,
                bankAccounts,
                new StatementsIn(Set.of(SETTLEMENT_DAY)),
                clock),
            RecordingLockProvider.free().reconciliationLockAt(clock));

    listener.onReportImportCompleted(
        new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, SETTLEMENT_DAY.plusDays(1), 1));

    then(notificationService)
        .should()
        .sendMessage(
            "⚠️ SEB tehing kadus ootel-tehingutest, pangas pole selle viitega makset: 1\n"
                + "TKF100 · DLA0553690 · ost LU1291102447 · SEB arvelduspäev 12.02.2026\n"
                + "Küsi SEB-lt, miks tehing nimekirjast kadus ja kas see arveldub.",
            INVESTMENT,
            WARNING);
  }

  @Test
  void aTradeSettledByAbsenceWithNoPayment_isAlerted() {
    TransactionOrder order = order(SETTLED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    settled(order, PENDING_REPORT_ABSENCE);
    pendingReport(SETTLEMENT_DAY, List.of(anotherOrdersRow()));

    checkWithStatementsFor(SETTLEMENT_DAY).run(LOCK_HELD);

    then(notificationService)
        .should()
        .sendMessage(
            "⚠️ SEB tehing kadus ootel-tehingutest, pangas pole selle viitega makset: 1\n"
                + "TKF100 · DLA0553690 · ost LU1291102447 · SEB arvelduspäev 12.02.2026\n"
                + "Küsi SEB-lt, miks tehing nimekirjast kadus ja kas see arveldub.",
            INVESTMENT,
            WARNING);
  }

  @Test
  void anUnpaidPieceGoneFromTheList_isAlertedWhileItsSiblingIsStillListed() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    piece(order, "DLA0553691", SETTLEMENT_DAY.plusDays(4));
    pendingReport(
        SETTLEMENT_DAY,
        List.of(Map.of("Client ref", order.getOrderUuid().toString(), "Our ref", "DLA0553691")));

    checkWithStatementsFor(SETTLEMENT_DAY).run(LOCK_HELD);

    then(notificationService)
        .should()
        .sendMessage(
            "⚠️ SEB tehing kadus ootel-tehingutest, pangas pole selle viitega makset: 1\n"
                + "TKF100 · DLA0553690 · ost LU1291102447 · SEB arvelduspäev 12.02.2026\n"
                + "Küsi SEB-lt, miks tehing nimekirjast kadus ja kas see arveldub.",
            INVESTMENT,
            WARNING);
  }

  @Test
  void aRowWithTheOrdersClientRefButNoReference_holdsEveryPieceBack() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY, List.of(Map.of("Client ref", order.getOrderUuid().toString())));

    checkWithStatementsFor(SETTLEMENT_DAY).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aClientRefRowWithoutAReference_holdsAPieceBackEvenBesideAListedSibling() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    piece(order, "DLA0553691", SETTLEMENT_DAY.plusDays(4));
    pendingReport(
        SETTLEMENT_DAY,
        List.of(
            Map.of("Client ref", order.getOrderUuid().toString(), "Our ref", "DLA0553691"),
            Map.of("Client ref", order.getOrderUuid().toString())));

    checkWithStatementsFor(SETTLEMENT_DAY).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aPaymentBookedWhileTheStatementIsChecked_isNotCalledMissing() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY, List.of(anotherOrdersRow()));
    BankStatementBalances statementLandingWithThePayment =
        new StatementsIn(Set.of(SETTLEMENT_DAY)) {
          @Override
          public Optional<BigDecimal> closingBalance(BankAccount account, LocalDate day) {
            bankBooks(REFERENCE, SETTLEMENT_DAY);
            return super.closingBalance(account, day);
          }
        };

    check(ALERTS_FROM_FEBRUARY, bankAccounts, statementLandingWithThePayment).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aTradeSebStillLists_isLateNotDropped_andRaisesNothing() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY, List.of(Map.of("Our ref", REFERENCE)));

    checkWithStatementsFor(SETTLEMENT_DAY).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aTradeTheBankPaidFor_raisesNothing() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    bankBooks(REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY, List.of(anotherOrdersRow()));

    checkWithStatementsFor(SETTLEMENT_DAY).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aTradeTheBankDated_raisesNothing() {
    TransactionOrder order = order(SETTLED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    settled(order, BANK_STATEMENT);
    pendingReport(SETTLEMENT_DAY, List.of(anotherOrdersRow()));

    checkWithStatementsFor(SETTLEMENT_DAY).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aTradeWhoseSettlementDayStatementIsNotInYet_waits() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY, List.of(anotherOrdersRow()));

    checkWithStatementsFor(SETTLEMENT_DAY.minusDays(1)).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aSettlementDayWhoseStatementIsMissing_waitsEvenWhenALaterStatementIsIn() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY.plusDays(1), List.of(anotherOrdersRow()));

    checkWithStatementsFor(SETTLEMENT_DAY.plusDays(1)).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aTradeWithEveryStatementFromItsSettlementDayIn_andNoPayment_isAlerted() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY.plusDays(1), List.of(anotherOrdersRow()));

    check(ALERTS_FROM_FEBRUARY, Set.of(SETTLEMENT_DAY, SETTLEMENT_DAY.plusDays(1))).run(LOCK_HELD);

    then(notificationService)
        .should()
        .sendMessage(
            "⚠️ SEB tehing kadus ootel-tehingutest, pangas pole selle viitega makset: 1\n"
                + "TKF100 · DLA0553690 · ost LU1291102447 · SEB arvelduspäev 12.02.2026\n"
                + "Küsi SEB-lt, miks tehing nimekirjast kadus ja kas see arveldub.",
            INVESTMENT,
            WARNING);
  }

  @Test
  void aTradeSebListsUntilItsSettlementDay_waitsForAListAsOfThatDay() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY.minusDays(1), List.of(anotherOrdersRow()));

    checkWithStatementsFor(SETTLEMENT_DAY.minusDays(1)).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aSettlementDayOnAnEstonianHoliday_waitsForTheBanksNextWorkingDay() {
    LocalDate independenceDay = LocalDate.of(2026, 2, 24);
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, independenceDay);
    pendingReport(independenceDay, List.of(anotherOrdersRow()));

    checkWithStatementsFor(independenceDay).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aTradeDueBeforeAlertsFrom_raisesNothing() {
    TransactionOrder order = order(EXECUTED);
    piece(order, REFERENCE, SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY, List.of(anotherOrdersRow()));

    check(new SebTradeListingCheckProperties(SETTLEMENT_DAY.plusDays(1)), Set.of(SETTLEMENT_DAY))
        .run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void anOrderSebNeverListedByItsDeadline_isAlertedOnce() {
    TransactionOrder order = order(SENT);
    LocalDate deadline = LocalDate.of(2026, 2, 13);
    pendingReport(deadline, List.of(anotherOrdersRow()));
    SebTradeListingCheck check = checkWithStatementsFor(deadline);

    check.run(LOCK_HELD);
    check.run(LOCK_HELD);

    then(notificationService)
        .should(times(1))
        .sendMessage(
            "⚠️ SEB ootel-tehingutes pole tehingukorraldust: 1\n"
                + "TKF100 · tehingukorraldus "
                + order.getId()
                + " · ost LU1291102447 · saadetud 10.02.2026\n"
                + "Kontrolli SEB-lt, kas tehingukorraldus jõudis kohale (või jäi SEB rida os-is"
                + " sidumata).",
            INVESTMENT,
            WARNING);
  }

  @Test
  void anOrderStillInsideItsDeadline_raisesNothing() {
    order(SENT);
    pendingReport(LocalDate.of(2026, 2, 12), List.of(anotherOrdersRow()));

    checkWithStatementsFor(LocalDate.of(2026, 2, 12)).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void anOrderSebListsButOsHasNotMatched_isNotCalledNeverListed() {
    TransactionOrder order = order(SENT);
    LocalDate deadline = LocalDate.of(2026, 2, 13);
    pendingReport(deadline, List.of(Map.of("Client ref", order.getOrderUuid().toString())));

    checkWithStatementsFor(deadline).run(LOCK_HELD);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aLatestReportWithoutAnAsOfDate_saysTheCheckDidNotRun_onceADay() {
    order(EXECUTED);
    reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(SETTLEMENT_DAY.plusDays(1))
            .rawData(List.of(anotherOrdersRow()))
            .metadata(Map.of())
            .createdAt(Instant.now())
            .build());
    entityManager.flush();
    SebTradeListingCheck check = checkWithStatementsFor(SETTLEMENT_DAY);

    check.run(LOCK_HELD);
    check.run(LOCK_HELD);

    then(notificationService)
        .should(times(1))
        .sendMessage(
            "⚠️ SEB ootel-tehingute kontroll jäi tegemata: viimast ootel-tehingute raportit ei"
                + " saanud lugeda (raport puudub, read on vigased või As-of kuupäev puudub).\n"
                + "Kontrolli, kas SEB ootel-tehingute raport tuli kohale ja on loetav. Kontroll"
                + " kordub järgmisel tööpäeval.",
            INVESTMENT,
            WARNING);
  }

  @Test
  void aFundWhoseStatementCannotBeRead_isNamedAsNotChecked_andTheOtherFundsTradeIsStillAlerted() {
    TransactionOrder unreadable = order(TKF100, EXECUTED);
    piece(unreadable, REFERENCE, SETTLEMENT_DAY);
    TransactionOrder readable = order(TUK75, EXECUTED);
    piece(readable, "DLA0553691", SETTLEMENT_DAY);
    pendingReport(SETTLEMENT_DAY, List.of(anotherOrdersRow()));
    BankAccounts twoFunds = mock(BankAccounts.class);
    given(twoFunds.findAll(TKF100)).willReturn(List.of(investmentAccount(TKF100)));
    given(twoFunds.findAll(TUK75)).willReturn(List.of(investmentAccount(TUK75)));
    BankStatementBalances statements = mock(BankStatementBalances.class);
    given(statements.closingBalance(investmentAccount(TKF100), SETTLEMENT_DAY))
        .willThrow(new IllegalStateException("unparseable statement"));
    given(statements.closingBalance(investmentAccount(TUK75), SETTLEMENT_DAY))
        .willReturn(Optional.of(BigDecimal.ZERO));

    check(ALERTS_FROM_FEBRUARY, twoFunds, statements).run(LOCK_HELD);

    then(notificationService)
        .should()
        .sendMessage(
            "⚠️ SEB tehing kadus ootel-tehingutest, pangas pole selle viitega makset: 1\n"
                + "TUK75 · DLA0553691 · ost LU1291102447 · SEB arvelduspäev 12.02.2026\n"
                + "Küsi SEB-lt, miks tehing nimekirjast kadus ja kas see arveldub.\n\n"
                + "⚠️ SEB ootel-tehingute kontroll jäi tegemata: fondi TKF100 pangaväljavõtet ei"
                + " saanud lugeda.\n"
                + "Teata IT-le, viga on logis. Kontroll kordub järgmisel tööpäeval.",
            INVESTMENT,
            WARNING);
  }

  private static BankAccount investmentAccount(TulevaFund fund) {
    return new BankAccount("EE00" + fund.name(), FUND_INVESTMENT_EUR, fund, "client");
  }

  private SebTradeListingCheck checkWithStatementsFor(LocalDate statementDay) {
    return check(ALERTS_FROM_FEBRUARY, Set.of(statementDay));
  }

  private SebTradeListingCheck check(
      SebTradeListingCheckProperties properties, Set<LocalDate> statementDays) {
    return check(properties, bankAccounts, new StatementsIn(statementDays));
  }

  private SebTradeListingCheck check(
      SebTradeListingCheckProperties properties,
      BankAccounts accounts,
      BankStatementBalances statements) {
    return check(
        properties, accounts, statements, morningOf(publicHolidays.nextWorkingDay(latestAsOfDate)));
  }

  private SebTradeListingCheck check(
      SebTradeListingCheckProperties properties,
      BankAccounts accounts,
      BankStatementBalances statements,
      Clock clock) {
    return new SebTradeListingCheck(
        latestPendingReport,
        new DroppedTradeFinder(
            orderRepository,
            executionRepository,
            settlementService,
            tradeSettlementBookings,
            new BankStatementArrival(accounts, statements, publicHolidays),
            properties),
        new NeverListedOrderFinder(orderRepository, overdueSettlementDetector, properties),
        new SebTradeListingAlerts(notificationService, auditEventRepository, clock));
  }

  private TransactionOrder order(OrderStatus status) {
    return order(TKF100, status);
  }

  private TransactionOrder order(TulevaFund fund, OrderStatus status) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(fund).createdBy("test").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(fund)
                .instrumentIsin(EJAP)
                .transactionType(BUY)
                .instrumentType(ETF)
                .orderQuantity(new BigDecimal("11704"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(UUID.randomUUID())
                .orderStatus(status)
                .orderTimestamp(ORDERED)
                .build());
    entityManager.flush();
    return order;
  }

  private void piece(TransactionOrder order, String reference, LocalDate settlementDay) {
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId(reference)
            .executionTimestamp(ORDERED.plusSeconds(3600))
            .executedQuantity(new BigDecimal("11704"))
            .scheduledSettlementDate(settlementDay)
            .source("SEB_OOTEL")
            .reportedDate(ORDERED.atZone(ZoneId.of("Europe/Tallinn")).toLocalDate())
            .build());
    entityManager.flush();
  }

  private void settled(TransactionOrder order, SettlementSource source) {
    settlementRepository.save(
        TransactionSettlement.builder()
            .orderId(order.getId())
            .settledAt(Instant.parse("2026-02-13T09:00:00Z"))
            .reportDate(SETTLEMENT_DAY)
            .source(source)
            .build());
    entityManager.flush();
  }

  private void bankBooks(String reference, LocalDate bookingDate) {
    fundBankLedger.recordTradeSettlement(
        TKF100,
        new BigDecimal("-209025.86"),
        new BigDecimal("11704"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        new TradeSettlementDetails(reference, EJAP, "EJAP", "test instrument", BigDecimal.ONE),
        bookingDate);
    entityManager.flush();
  }

  private void pendingReport(LocalDate asOfDate, List<Map<String, Object>> rows) {
    reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(asOfDate.plusDays(1))
            .rawData(rows)
            .metadata(Map.of("asOfDate", asOfDate.toString()))
            .createdAt(Instant.now())
            .build());
    entityManager.flush();
    latestAsOfDate = asOfDate;
  }

  private static Map<String, Object> anotherOrdersRow() {
    return Map.of("Client ref", UUID.randomUUID().toString());
  }

  private static Clock morningOf(LocalDate day) {
    return Clock.fixed(
        day.atTime(10, 15).atZone(ZoneId.of("Europe/Tallinn")).toInstant(),
        ZoneId.of("Europe/Tallinn"));
  }

  private static class StatementsIn implements BankStatementBalances {

    private final Set<LocalDate> days;

    StatementsIn(Set<LocalDate> days) {
      this.days = days;
    }

    @Override
    public Optional<BigDecimal> closingBalance(BankAccount account, LocalDate day) {
      return days.contains(day) ? Optional.of(BigDecimal.ZERO) : Optional.empty();
    }

    @Override
    public boolean isInsideAStatementClosingLater(BankAccount account, LocalDate day) {
      return false;
    }
  }
}
