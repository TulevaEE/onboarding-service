package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.DRAFT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionBatch.TRADE_REGISTRY_SQL_IMPORT_SOURCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.BatchStatus;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  RebalanceWindows.class,
  RebalanceAnnouncements.class,
  RebalanceTrades.class,
  NavWeightsReader.class,
  InvestmentParameterRepository.class,
  EstonianCalendar.class,
  PublicHolidays.class
})
class ModelRebalanceCheckIT {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final String WORLD = "IE00BFG1TM61";
  private static final String NEW_WORLD = "IE000I9HGDZ3";
  private static final String EMERGING = "IE00BMDBMY19";
  private static final LocalDate PREVIOUS_MODEL = LocalDate.of(2026, 6, 1);
  private static final LocalDate NEW_MODEL = LocalDate.of(2026, 10, 1);
  private static final BigDecimal NAV = new BigDecimal("1000000");

  @Autowired private RebalanceWindows rebalanceWindows;
  @Autowired private RebalanceAnnouncements rebalanceAnnouncements;
  @Autowired private EstonianCalendar estonianCalendar;
  @Autowired private ModelPortfolioAllocationRepository allocationRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private OperationsNotificationService notificationService;

  @Test
  void firstMonthWithTheFundOutOfLine_reportsProgressAndDaysLeft() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 2), Map.of(WORLD, "790000", EMERGING, "200000"));

    runCheckOn(LocalDate.of(2026, 10, 5));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ℹ️ TUV100 mudelportfell alates 01.10.2026: jõustumisest 4 päeva, sisemine tähtaeg \
            01.12.2026 (57 päeva), seaduslik 04.01.2027
            Üle 5 pp (NAV 02.10.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp; \
            tehingud: pole""",
            INVESTMENT,
            INFO);
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aMonthWithoutTradesInTheChangedInstruments_warnsUntilATradeIsSent() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 30), Map.of(WORLD, "790000", EMERGING, "200000"));
    givenOrder(TUV100, EMERGING, BUY, SENT, LocalDate.of(2026, 10, 20));
    givenOrder(TUV100, WORLD, SELL, CANCELLED, LocalDate.of(2026, 10, 20));
    givenOrder(TUV100, WORLD, SELL, DRAFT, LocalDate.of(2026, 10, 21));
    givenOrder(TUV100, NEW_WORLD, BUY, SENT, LocalDate.of(2026, 9, 30));

    runCheckOn(LocalDate.of(2026, 11, 2));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ TUV100 mudelportfell alates 01.10.2026: tehingute tähtaeg oli 01.11.2026, \
            muutunud instrumentides tehinguid pole
            Üle 5 pp (NAV 30.10.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
            Tee tasakaalustamise tehingud; sisemine tähtaeg 01.12.2026, seaduslik 04.01.2027""",
            INVESTMENT,
            WARNING);
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void
      aSentOrderFromABatchFinalisedHereWithoutAnExecution_doesNotClearTheWarningButItsExecutionDoes() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 30), Map.of(WORLD, "790000", EMERGING, "200000"));
    givenOrderFromABatchFinalisedInThisService(NEW_WORLD, BUY, SENT, LocalDate.of(2026, 10, 30));

    runCheckOn(LocalDate.of(2026, 11, 2));

    then(notificationService)
        .should()
        .sendMessage(
            startsWith("⚠️ TUV100 mudelportfell alates 01.10.2026"), eq(INVESTMENT), eq(WARNING));

    givenOrderFromABatchFinalisedInThisService(WORLD, SELL, EXECUTED, LocalDate.of(2026, 10, 30));
    runCheckOn(LocalDate.of(2026, 11, 3));

    then(notificationService)
        .should()
        .sendMessage(endsWith("tehingud: IE00BFG1TM61"), eq(INVESTMENT), eq(INFO));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aSentTradeInAChangedInstrument_turnsTheWarningBackIntoProgress() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 30), Map.of(WORLD, "790000", EMERGING, "200000"));
    givenOrder(TUV100, NEW_WORLD, BUY, SENT, LocalDate.of(2026, 10, 30));

    runCheckOn(LocalDate.of(2026, 11, 2));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ℹ️ TUV100 mudelportfell alates 01.10.2026: jõustumisest 32 päeva, sisemine tähtaeg \
            01.12.2026 (29 päeva), seaduslik 04.01.2027
            Üle 5 pp (NAV 30.10.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp; \
            tehingud: IE000I9HGDZ3""",
            INVESTMENT,
            INFO);
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void theFirstNavWithinFivePoints_closesAnAnnouncedWindowOnceEvenWithoutANewerNav() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 29), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 30));
    then(notificationService)
        .should()
        .sendMessage(
            startsWith("ℹ️ TUV100 mudelportfell alates 01.10.2026"), eq(INVESTMENT), eq(INFO));

    givenOrder(TUV100, WORLD, SELL, EXECUTED, LocalDate.of(2026, 10, 30));
    givenOrder(TUV100, NEW_WORLD, BUY, EXECUTED, LocalDate.of(2026, 10, 30));
    givenNav(LocalDate.of(2026, 10, 30), Map.of(NEW_WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 11, 2));

    then(notificationService)
        .should()
        .sendMessage(
            "✅ TUV100 mudelportfell alates 01.10.2026: fond on mudeliga vastavuses (kõik"
                + " osakaalud 5 pp piires, NAV 30.10.2026), kontroll suletud",
            INVESTMENT,
            INFO);
    then(notificationService).shouldHaveNoMoreInteractions();

    runCheckOn(LocalDate.of(2026, 11, 3));
    givenNav(LocalDate.of(2026, 11, 3), Map.of(NEW_WORLD, "700000", EMERGING, "290000"));
    runCheckOn(LocalDate.of(2026, 11, 4));

    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aCloseThatCouldNotBePosted_isPostedOnTheNextRunEvenAfterANewerNav() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 29), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 30));
    givenNav(LocalDate.of(2026, 10, 30), Map.of(NEW_WORLD, "790000", EMERGING, "200000"));
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(startsWith("✅"), eq(INVESTMENT), eq(INFO));
    runCheckOn(LocalDate.of(2026, 11, 2));

    willDoNothing().given(notificationService).sendMessage(any(), any(), any());
    givenNav(LocalDate.of(2026, 11, 2), Map.of(NEW_WORLD, "780000", EMERGING, "210000"));
    runCheckOn(LocalDate.of(2026, 11, 3));
    runCheckOn(LocalDate.of(2026, 11, 4));

    then(notificationService)
        .should(times(2))
        .sendMessage(
            "✅ TUV100 mudelportfell alates 01.10.2026: fond on mudeliga vastavuses (kõik"
                + " osakaalud 5 pp piires, NAV 30.10.2026), kontroll suletud",
            INVESTMENT,
            INFO);
  }

  @Test
  void aCloseStillOwedWhenANewerModelTakesEffect_isPostedOnceAlongsideTheNewWindow() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 29), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 30));
    givenNav(LocalDate.of(2026, 10, 30), Map.of(NEW_WORLD, "790000", EMERGING, "200000"));
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(startsWith("✅"), eq(INVESTMENT), eq(INFO));
    runCheckOn(LocalDate.of(2026, 11, 2));

    willDoNothing().given(notificationService).sendMessage(any(), any(), any());
    givenModel(TUV100, LocalDate.of(2026, 11, 3), Map.of(NEW_WORLD, "0.70", EMERGING, "0.30"));
    runCheckOn(LocalDate.of(2026, 11, 3));
    runCheckOn(LocalDate.of(2026, 11, 4));

    then(notificationService)
        .should(times(2))
        .sendMessage(
            "✅ TUV100 mudelportfell alates 01.10.2026: fond on mudeliga vastavuses (kõik"
                + " osakaalud 5 pp piires, NAV 30.10.2026), kontroll suletud",
            INVESTMENT,
            INFO);
    then(notificationService)
        .should(times(2))
        .sendMessage(
            startsWith("ℹ️ TUV100 mudelportfell alates 03.11.2026"), eq(INVESTMENT), eq(INFO));
  }

  @Test
  void olderModelsWhoseDatesPredateTheParameters_doNotStopTheCurrentWindowsAlert() {
    givenModel(TUV100, LocalDate.of(2026, 6, 1), Map.of(WORLD, "1.00"));
    givenModel(TUV100, LocalDate.of(2026, 8, 1), Map.of(WORLD, "0.80", EMERGING, "0.20"));
    givenModel(TUV100, LocalDate.of(2026, 9, 1), Map.of(NEW_WORLD, "0.80", EMERGING, "0.20"));
    givenNav(LocalDate.of(2026, 10, 30), Map.of(WORLD, "790000", EMERGING, "200000"));

    runCheckOn(LocalDate.of(2026, 11, 2));

    then(notificationService)
        .should()
        .sendMessage(
            startsWith(
                "🛑 TUV100 mudelportfell alates 01.09.2026: sisemine tähtaeg oli 01.11.2026"),
            eq(INVESTMENT),
            eq(ERROR));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aCloseOwedAcrossTwoNewerModels_isPostedOnceWhenSlackRecovers() {
    var close =
        "✅ TUV100 mudelportfell alates 01.10.2026: fond on mudeliga vastavuses (kõik"
            + " osakaalud 5 pp piires, NAV 30.10.2026), kontroll suletud";
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 29), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 30));
    givenNav(LocalDate.of(2026, 10, 30), Map.of(NEW_WORLD, "790000", EMERGING, "200000"));
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(close, INVESTMENT, INFO);
    runCheckOn(LocalDate.of(2026, 11, 2));
    givenModel(TUV100, LocalDate.of(2026, 11, 3), Map.of(NEW_WORLD, "0.70", EMERGING, "0.30"));
    runCheckOn(LocalDate.of(2026, 11, 3));
    givenModel(TUV100, LocalDate.of(2026, 11, 4), Map.of(NEW_WORLD, "0.60", EMERGING, "0.40"));
    runCheckOn(LocalDate.of(2026, 11, 4));

    willDoNothing().given(notificationService).sendMessage(any(), any(), any());
    runCheckOn(LocalDate.of(2026, 11, 5));
    runCheckOn(LocalDate.of(2026, 11, 6));

    then(notificationService).should(times(4)).sendMessage(close, INVESTMENT, INFO);
  }

  @Test
  void aLaterModelVersionWithTheSameWeights_keepsTheUnresolvedWindowAndItsDeadlines() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenModel(TUV100, LocalDate.of(2026, 11, 1), Map.of(NEW_WORLD, "0.8", EMERGING, "0.2"));
    givenNav(LocalDate.of(2026, 11, 30), Map.of(WORLD, "790000", EMERGING, "200000"));

    runCheckOn(LocalDate.of(2026, 12, 1));

    then(notificationService)
        .should()
        .sendMessage(
            startsWith(
                "🛑 TUV100 mudelportfell alates 01.10.2026: sisemine tähtaeg oli 01.12.2026"),
            eq(INVESTMENT),
            eq(ERROR));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void twoMonthsWithoutTheFundInLine_failsUntilANavWithinFivePointsClosesIt() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenOrder(TUV100, WORLD, SELL, EXECUTED, LocalDate.of(2026, 10, 5));
    givenNav(LocalDate.of(2026, 11, 30), Map.of(WORLD, "300000", NEW_WORLD, "490000"));

    runCheckOn(LocalDate.of(2026, 12, 1));

    then(notificationService)
        .should()
        .sendMessage(
            """
            🛑 TUV100 mudelportfell alates 01.10.2026: sisemine tähtaeg oli 01.12.2026, fond pole \
            mudeliga vastavuses
            Üle 5 pp (NAV 30.11.2026): IE000I9HGDZ3 -31,00 pp, IE00BFG1TM61 +30,00 pp, \
            IE00BMDBMY19 -20,00 pp; tehingud: IE00BFG1TM61
            Vii fond 5 pp piiresse; seaduslik tähtaeg 04.01.2027 (sisekord 4 p 2.6.1)""",
            INVESTMENT,
            ERROR);
    then(notificationService).shouldHaveNoMoreInteractions();

    givenNav(
        LocalDate.of(2026, 12, 1), Map.of(NEW_WORLD, "780000", EMERGING, "210000", WORLD, "0"));
    runCheckOn(LocalDate.of(2026, 12, 2));

    then(notificationService)
        .should()
        .sendMessage(
            "✅ TUV100 mudelportfell alates 01.10.2026: fond on mudeliga vastavuses (kõik"
                + " osakaalud 5 pp piires, NAV 01.12.2026), kontroll suletud",
            INVESTMENT,
            INFO);
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void anOldChangeThatWasInLineOnAnyNavSinceItTookEffect_staysSilentEvenAfterTheFundDrifts() {
    givenTuv100ModelSwitchesFromWorldToNewWorldOn(LocalDate.of(2026, 8, 19));
    givenNav(LocalDate.of(2026, 8, 31), Map.of(WORLD, "790000", EMERGING, "200000"));
    givenNav(LocalDate.of(2026, 11, 10), Map.of(NEW_WORLD, "790000", EMERGING, "200000"));
    givenNav(LocalDate.of(2026, 11, 18), Map.of(NEW_WORLD, "900000", EMERGING, "90000"));

    runCheckOn(LocalDate.of(2026, 11, 19));

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aFundsFirstModel_anUnchangedNewVersionAndAFundWithNoModel_openNoWindow() {
    givenModel(TUK75, PREVIOUS_MODEL, Map.of(WORLD, "1.00"));
    givenModel(TUK00, PREVIOUS_MODEL, Map.of(EMERGING, "1.00"));
    givenModel(TUK00, NEW_MODEL, Map.of(EMERGING, "1.000000"));
    givenNav(TUK75, LocalDate.of(2026, 10, 30), Map.of(EMERGING, "990000"));
    givenNav(TUK00, LocalDate.of(2026, 10, 30), Map.of(WORLD, "990000"));

    runCheckOn(LocalDate.of(2026, 12, 1));

    assertThat(rebalanceWindows.current(TUK75, LocalDate.of(2026, 12, 1))).isEmpty();
    assertThat(rebalanceWindows.current(TUK00, LocalDate.of(2026, 12, 1))).isEmpty();
    assertThat(rebalanceWindows.current(TKF100, LocalDate.of(2026, 12, 1))).isEmpty();
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void exposesTheWindowStateForOtherComponents() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 30), Map.of(WORLD, "790000", EMERGING, "200000"));
    givenOrder(TUV100, NEW_WORLD, BUY, SENT, LocalDate.of(2026, 10, 30));

    var window = rebalanceWindows.current(TUV100, LocalDate.of(2026, 11, 2)).orElseThrow();

    assertThat(window.modelEffectiveDate()).isEqualTo(NEW_MODEL);
    assertThat(window.daysSinceModelEffectiveDate()).isEqualTo(32);
    assertThat(window.changedIsins()).containsExactlyInAnyOrder(WORLD, NEW_WORLD);
    assertThat(window.tradesDone()).isTrue();
    assertThat(window.inLine()).isFalse();
    assertThat(window.deadlines())
        .isEqualTo(
            new RebalanceDeadlines(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 4)));
    assertThat(window.state()).isEqualTo(RebalanceState.INFO);
  }

  private void runCheckOn(LocalDate date) {
    var clock = Clock.fixed(date.atTime(9, 15).atZone(TALLINN).toInstant(), TALLINN);
    new ModelRebalanceCheckJob(
            rebalanceWindows, rebalanceAnnouncements, notificationService, estonianCalendar, clock)
        .checkRebalanceWindows();
  }

  private void givenTuv100ModelSwitchesFromWorldToNewWorld() {
    givenTuv100ModelSwitchesFromWorldToNewWorldOn(NEW_MODEL);
  }

  private void givenTuv100ModelSwitchesFromWorldToNewWorldOn(LocalDate effectiveDate) {
    givenModel(TUV100, PREVIOUS_MODEL, Map.of(WORLD, "0.80", EMERGING, "0.20"));
    givenModel(TUV100, effectiveDate, Map.of(NEW_WORLD, "0.80", EMERGING, "0.20", WORLD, "0"));
  }

  private void givenModel(TulevaFund fund, LocalDate effectiveDate, Map<String, String> weights) {
    weights.forEach(
        (isin, weight) ->
            allocationRepository.save(
                ModelPortfolioAllocation.builder()
                    .fund(fund)
                    .effectiveDate(effectiveDate)
                    .isin(isin)
                    .weight(new BigDecimal(weight))
                    .build()));
  }

  private void givenNav(LocalDate navDate, Map<String, String> securities) {
    givenNav(TUV100, navDate, securities);
  }

  private void givenNav(TulevaFund fund, LocalDate navDate, Map<String, String> securities) {
    var published = UUID.randomUUID();
    var publishedAt = navDate.plusDays(1).atTime(11, 30).atZone(TALLINN).toInstant();
    insertNavRow(
        fund, navDate, published, publishedAt, "UNITS", "Total outstanding units:", null, NAV);
    securities.forEach(
        (isin, marketValue) ->
            insertNavRow(
                fund,
                navDate,
                published,
                publishedAt,
                "SECURITY",
                isin,
                isin,
                new BigDecimal(marketValue)));

    var unpublishedRecalculation = UUID.randomUUID();
    insertNavRow(
        fund,
        navDate,
        unpublishedRecalculation,
        null,
        "UNITS",
        "Total outstanding units:",
        null,
        NAV);
    insertNavRow(
        fund,
        navDate,
        unpublishedRecalculation,
        null,
        "SECURITY",
        EMERGING,
        EMERGING,
        new BigDecimal("400000"));
  }

  private void insertNavRow(
      TulevaFund fund,
      LocalDate navDate,
      UUID calculationId,
      Instant publishedAt,
      String accountType,
      String accountName,
      String accountId,
      BigDecimal marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, market_value,
               currency, calculation_id, published_at)
            VALUES (:navDate, :fundCode, :accountType, :accountName, :accountId, :marketValue,
                    'EUR', :calculationId, :publishedAt)
            """)
        .param("navDate", navDate)
        .param("fundCode", fund.getCode())
        .param("accountType", accountType)
        .param("accountName", accountName)
        .param("accountId", accountId)
        .param("marketValue", marketValue)
        .param("calculationId", calculationId)
        .param("publishedAt", publishedAt == null ? null : java.sql.Timestamp.from(publishedAt))
        .update();
  }

  private void givenOrder(
      TulevaFund fund, String isin, TransactionType side, OrderStatus status, LocalDate orderDate) {
    givenOrder(
        fund,
        isin,
        side,
        status,
        orderDate,
        Map.of(
            "source", TRADE_REGISTRY_SQL_IMPORT_SOURCE, "importKey", "GAS:" + UUID.randomUUID()));
  }

  private void givenOrderFromABatchFinalisedInThisService(
      String isin, TransactionType side, OrderStatus status, LocalDate orderDate) {
    givenOrder(TUV100, isin, side, status, orderDate, Map.of("commandId", 7L, "mode", "REBALANCE"));
  }

  private void givenOrder(
      TulevaFund fund,
      String isin,
      TransactionType side,
      OrderStatus status,
      LocalDate orderDate,
      Map<String, Object> batchMetadata) {
    var batch =
        batchRepository.save(
            TransactionBatch.builder()
                .fund(fund)
                .createdBy("operator")
                .status(BatchStatus.SENT)
                .metadata(batchMetadata)
                .build());
    orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(fund)
            .instrumentIsin(isin)
            .transactionType(side)
            .instrumentType(ETF)
            .orderVenue(SEB)
            .orderQuantity(new BigDecimal("100"))
            .orderStatus(status)
            .orderTimestamp(orderDate.atTime(15, 0).atZone(TALLINN).toInstant())
            .build());
  }
}
