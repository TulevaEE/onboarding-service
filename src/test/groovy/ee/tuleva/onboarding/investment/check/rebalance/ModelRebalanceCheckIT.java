package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.DRAFT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.BatchStatus;
import ee.tuleva.onboarding.investment.transaction.CommandStatus;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionCommand;
import ee.tuleva.onboarding.investment.transaction.TransactionCommandRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionMode;
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
  private static final String NOT_IN_ANY_MODEL = "LU0476289540";
  private static final String CANADA = "LU0476289540";
  private static final String TRADE_REGISTRY_SQL_EXPORT_SOURCE = "GAS_REGISTRY";
  private static final TransactionMode BUY_MODE = TransactionMode.BUY;
  private static final LocalDate PREVIOUS_MODEL = LocalDate.of(2026, 6, 1);
  private static final LocalDate NEW_MODEL = LocalDate.of(2026, 10, 1);
  private static final LocalDate SEPTEMBER_MODEL = LocalDate.of(2026, 9, 1);
  private static final BigDecimal NAV = new BigDecimal("1000000");

  @Autowired private RebalanceWindows rebalanceWindows;
  @Autowired private RebalanceAnnouncements rebalanceAnnouncements;
  @Autowired private EstonianCalendar estonianCalendar;
  @Autowired private ModelPortfolioAllocationRepository allocationRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionCommandRepository commandRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private OperationsNotificationService notificationService;

  @Test
  void aWindowThatOpens_postsItsInfoOnceAndStaysQuietUntilFifteenCalendarDaysBeforeTheDeadline() {
    givenTuv100ModelSwitchesFromWorldToNewWorldOn(SEPTEMBER_MODEL);
    givenNav(LocalDate.of(2026, 9, 30), Map.of(WORLD, "790000", EMERGING, "200000"));

    runCheckOn(LocalDate.of(2026, 10, 1));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ℹ️ TUV100 mudelportfell alates 01.09.2026: tähtaeg 01.12.2026 (sisekord 4 p 2.6.1), \
            jäänud 61 päeva
            Üle 5 pp (NAV 30.09.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp""",
            INVESTMENT,
            INFO);

    runCheckOn(LocalDate.of(2026, 10, 2));
    givenNav(LocalDate.of(2026, 11, 12), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 11, 13));

    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void
      fromFifteenCalendarDaysBeforeTheDeadlineAWarningRepeatsUpToTheDeadlineAndAFailEveryDayAfter() {
    givenTuv100ModelSwitchesFromWorldToNewWorldOn(SEPTEMBER_MODEL);
    givenNav(LocalDate.of(2026, 9, 30), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 1));
    givenNav(LocalDate.of(2026, 11, 13), Map.of(WORLD, "790000", EMERGING, "200000"));

    runCheckOn(LocalDate.of(2026, 11, 16));

    then(notificationService)
        .should()
        .sendMessage(
            """
            ⚠️ TUV100 mudelportfell alates 01.09.2026: tähtaeg 01.12.2026, jäänud 15 päeva; \
            tasakaalustamise tehinguid pole tehtud
            Üle 5 pp (NAV 13.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
            Tee tasakaalustamise tehingud või vii fond tähtajaks 5 pp piiresse""",
            INVESTMENT,
            WARNING);

    runCheckOn(LocalDate.of(2026, 11, 17));
    givenNav(LocalDate.of(2026, 11, 30), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 12, 1));
    runCheckOn(LocalDate.of(2026, 12, 2));

    then(notificationService)
        .should()
        .sendMessage(
            """
            🛑 TUV100 mudelportfell alates 01.09.2026: tähtaeg oli 01.12.2026, fond pole mudeliga \
            vastavuses ja tasakaalustamise tehinguid pole tehtud
            Üle 5 pp (NAV 30.11.2026): IE000I9HGDZ3 -80,00 pp, IE00BFG1TM61 +79,00 pp
            Vii fond 5 pp piiresse (sisekord 4 p 2.6.1)""",
            INVESTMENT,
            ERROR);

    runCheckOn(LocalDate.of(2026, 12, 3));

    then(notificationService)
        .should(times(3))
        .sendMessage(startsWith("⚠️ TUV100"), eq(INVESTMENT), eq(WARNING));
    then(notificationService)
        .should(times(2))
        .sendMessage(startsWith("🛑 TUV100"), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void anOpeningInfoWhosePostFails_isPostedOnTheNextWorkingDayAndThenNotAgain() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 2), Map.of(WORLD, "790000", EMERGING, "200000"));
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(startsWith("ℹ️"), eq(INVESTMENT), eq(INFO));
    runCheckOn(LocalDate.of(2026, 10, 5));

    willDoNothing().given(notificationService).sendMessage(any(), any(), any());
    runCheckOn(LocalDate.of(2026, 10, 6));
    runCheckOn(LocalDate.of(2026, 10, 7));

    then(notificationService)
        .should(times(2))
        .sendMessage(
            startsWith("ℹ️ TUV100 mudelportfell alates 01.10.2026"), eq(INVESTMENT), eq(INFO));
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
        .should()
        .sendMessage(
            startsWith("ℹ️ TUV100 mudelportfell alates 03.11.2026"), eq(INVESTMENT), eq(INFO));
  }

  @Test
  void olderModelsWhoseDatesPredateTheParameters_doNotStopTheCurrentWindowsAlert() {
    givenModel(TUV100, LocalDate.of(2026, 6, 1), Map.of(WORLD, "1.00"));
    givenModel(TUV100, LocalDate.of(2026, 8, 1), Map.of(WORLD, "0.80", EMERGING, "0.20"));
    givenModel(TUV100, LocalDate.of(2026, 9, 1), Map.of(NEW_WORLD, "0.80", EMERGING, "0.20"));
    givenNav(LocalDate.of(2026, 11, 30), Map.of(WORLD, "790000", EMERGING, "200000"));

    runCheckOn(LocalDate.of(2026, 12, 2));

    then(notificationService)
        .should()
        .sendMessage(
            startsWith("🛑 TUV100 mudelportfell alates 01.09.2026: tähtaeg oli 01.12.2026"),
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
  void
      aNavRevisionThatTakesBackTheClosingNav_reopensTheWindowWithAnInfoAndTheNextNavInLineClosesIt() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 29), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 30));
    givenNav(LocalDate.of(2026, 10, 30), Map.of(NEW_WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 11, 2));

    givenRevisedNav(LocalDate.of(2026, 10, 30), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 11, 3));
    givenNav(LocalDate.of(2026, 11, 3), Map.of(NEW_WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 11, 4));
    runCheckOn(LocalDate.of(2026, 11, 5));

    var inOrder = inOrder(notificationService);
    then(notificationService)
        .should(inOrder)
        .sendMessage(startsWith("ℹ️ TUV100"), eq(INVESTMENT), eq(INFO));
    then(notificationService)
        .should(inOrder)
        .sendMessage(endsWith("NAV 30.10.2026), kontroll suletud"), eq(INVESTMENT), eq(INFO));
    then(notificationService)
        .should(inOrder)
        .sendMessage(startsWith("ℹ️ TUV100"), eq(INVESTMENT), eq(INFO));
    then(notificationService)
        .should(inOrder)
        .sendMessage(endsWith("NAV 03.11.2026), kontroll suletud"), eq(INVESTMENT), eq(INFO));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aWindowANewerModelReplacesBeforeItCameIntoLine_isClosedOnceAndNoLongerMeasured() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 2), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 5));
    givenModel(TUV100, LocalDate.of(2026, 11, 3), Map.of(NEW_WORLD, "0.70", EMERGING, "0.30"));
    givenNav(LocalDate.of(2026, 11, 2), Map.of(WORLD, "790000", EMERGING, "200000"));

    runCheckOn(LocalDate.of(2026, 11, 3));
    runCheckOn(LocalDate.of(2026, 11, 4));

    then(notificationService)
        .should()
        .sendMessage(
            "⏹ TUV100 mudelportfell alates 01.10.2026: asendatud mudeliga alates 03.11.2026,"
                + " vastavusse ei jõudnud; kontroll suletud",
            INVESTMENT,
            INFO);
    then(notificationService)
        .should()
        .sendMessage(
            startsWith("ℹ️ TUV100 mudelportfell alates 03.11.2026"), eq(INVESTMENT), eq(INFO));
    then(notificationService)
        .should()
        .sendMessage(
            startsWith("ℹ️ TUV100 mudelportfell alates 01.10.2026"), eq(INVESTMENT), eq(INFO));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aLaterModelVersionWithTheSameWeights_keepsTheUnresolvedWindowAndItsDeadline() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenModel(TUV100, LocalDate.of(2026, 11, 1), Map.of(NEW_WORLD, "0.8", EMERGING, "0.2"));
    givenNav(LocalDate.of(2026, 11, 30), Map.of(WORLD, "790000", EMERGING, "200000"));

    runCheckOn(LocalDate.of(2027, 1, 5));

    then(notificationService)
        .should()
        .sendMessage(
            startsWith("🛑 TUV100 mudelportfell alates 01.10.2026: tähtaeg oli 04.01.2027"),
            eq(INVESTMENT),
            eq(ERROR));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void anExecutedOrderFromARebalanceRun_closesTheWindowOnItsTradeDate() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 2), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 5));
    givenExecutedOrderFromATradePrepRun(
        REBALANCE, NEW_WORLD, BUY, LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30));
    givenOrderFromATradePrepRun(REBALANCE, NEW_WORLD, BUY, CANCELLED, LocalDate.of(2026, 10, 20));
    givenOrderFromATradePrepRun(REBALANCE, WORLD, SELL, DRAFT, LocalDate.of(2026, 10, 21));
    givenOrderFromATradePrepRun(REBALANCE, WORLD, SELL, SENT, LocalDate.of(2026, 10, 26));
    givenExecutedOrderFromATradePrepRun(
        REBALANCE, NEW_WORLD, BUY, LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28));

    runCheckOn(LocalDate.of(2026, 10, 30));
    runCheckOn(LocalDate.of(2026, 11, 2));

    then(notificationService)
        .should()
        .sendMessage(
            "✅ TUV100 mudelportfell alates 01.10.2026: tasakaalustamise tehingud tehtud"
                + " 28.10.2026, kontroll suletud",
            INVESTMENT,
            INFO);
    then(notificationService)
        .should()
        .sendMessage(startsWith("ℹ️ TUV100"), eq(INVESTMENT), eq(INFO));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aSentOrderFromARebalanceRun_doesNotCloseTheWindowAndItsExecutionDoes() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 2), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 5));
    var order =
        givenOrderFromATradePrepRun(REBALANCE, WORLD, SELL, SENT, LocalDate.of(2026, 10, 28));

    runCheckOn(LocalDate.of(2026, 10, 29));

    then(notificationService)
        .should()
        .sendMessage(startsWith("ℹ️ TUV100"), eq(INVESTMENT), eq(INFO));
    then(notificationService).shouldHaveNoMoreInteractions();

    givenExecuted(order, LocalDate.of(2026, 10, 29));
    runCheckOn(LocalDate.of(2026, 10, 30));

    then(notificationService)
        .should()
        .sendMessage(
            "✅ TUV100 mudelportfell alates 01.10.2026: tasakaalustamise tehingud tehtud"
                + " 29.10.2026, kontroll suletud",
            INVESTMENT,
            INFO);
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void anOrderFromABuyRunInAChangedInstrument_doesNotCloseTheWindow() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 2), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 5));
    givenOrderFromATradePrepRun(BUY_MODE, NEW_WORLD, BUY, EXECUTED, LocalDate.of(2026, 10, 28));

    runCheckOn(LocalDate.of(2026, 10, 30));

    then(notificationService)
        .should()
        .sendMessage(startsWith("ℹ️ TUV100"), eq(INVESTMENT), eq(INFO));
    then(notificationService).shouldHaveNoMoreInteractions();
    assertThat(
            rebalanceWindows.current(TUV100, LocalDate.of(2026, 10, 30)).orElseThrow().closedOn())
        .isEmpty();
  }

  @Test
  void anOrderImportedFromTheTradeRegistry_doesNotCloseTheWindow() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 2), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 5));
    givenOrder(TUV100, WORLD, SELL, EXECUTED, LocalDate.of(2026, 10, 28));

    runCheckOn(LocalDate.of(2026, 10, 30));

    then(notificationService)
        .should()
        .sendMessage(startsWith("ℹ️ TUV100"), eq(INVESTMENT), eq(INFO));
    then(notificationService).shouldHaveNoMoreInteractions();
    assertThat(
            rebalanceWindows.current(TUV100, LocalDate.of(2026, 10, 30)).orElseThrow().closedOn())
        .isEmpty();
  }

  @Test
  void aZeroPercentRowAddedByALaterVersionWithTheSameWeights_isMeasuredAndKeepsTheWindowOpen() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 2), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 5));
    givenModel(
        TUV100,
        LocalDate.of(2026, 10, 15),
        Map.of(NEW_WORLD, "0.80", EMERGING, "0.20", WORLD, "0", CANADA, "0"));
    givenNav(
        LocalDate.of(2026, 10, 30),
        Map.of(NEW_WORLD, "760000", EMERGING, "180000", CANADA, "60000"));

    runCheckOn(LocalDate.of(2026, 11, 2));

    then(notificationService)
        .should()
        .sendMessage(startsWith("ℹ️ TUV100"), eq(INVESTMENT), eq(INFO));
    then(notificationService).shouldHaveNoMoreInteractions();
    var window = rebalanceWindows.current(TUV100, LocalDate.of(2026, 11, 2)).orElseThrow();
    assertThat(window.modelEffectiveDate()).isEqualTo(NEW_MODEL);
    assertThat(window.closed()).isFalse();
    assertThat(window.outsideToleranceAtLatestNav())
        .extracting(Deviation::isin)
        .containsExactly(CANADA);
  }

  @Test
  void aHeldInstrumentWithNoModelRow_doesNotKeepTheWindowOpen() {
    givenTuv100ModelSwitchesFromWorldToNewWorld();
    givenNav(LocalDate.of(2026, 10, 29), Map.of(WORLD, "790000", EMERGING, "200000"));
    runCheckOn(LocalDate.of(2026, 10, 30));
    givenNav(
        LocalDate.of(2026, 10, 30),
        Map.of(NEW_WORLD, "760000", EMERGING, "180000", NOT_IN_ANY_MODEL, "60000"));

    runCheckOn(LocalDate.of(2026, 11, 2));

    then(notificationService)
        .should()
        .sendMessage(
            "✅ TUV100 mudelportfell alates 01.10.2026: fond on mudeliga vastavuses (kõik"
                + " osakaalud 5 pp piires, NAV 30.10.2026), kontroll suletud",
            INVESTMENT,
            INFO);
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

    var window = rebalanceWindows.current(TUV100, LocalDate.of(2026, 11, 2)).orElseThrow();

    assertThat(window.modelEffectiveDate()).isEqualTo(NEW_MODEL);
    assertThat(window.daysUntilDeadline()).isEqualTo(63);
    assertThat(window.deadline()).isEqualTo(LocalDate.of(2027, 1, 4));
    assertThat(window.closedOn()).isEmpty();
    assertThat(window.closed()).isFalse();
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

  private void givenRevisedNav(LocalDate navDate, Map<String, String> securities) {
    insertPublishedCalculation(
        TUV100, navDate, navDate.plusDays(3).atTime(10, 0).atZone(TALLINN).toInstant(), securities);
  }

  private void givenNav(TulevaFund fund, LocalDate navDate, Map<String, String> securities) {
    insertPublishedCalculation(
        fund, navDate, navDate.plusDays(1).atTime(11, 30).atZone(TALLINN).toInstant(), securities);

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

  private void insertPublishedCalculation(
      TulevaFund fund, LocalDate navDate, Instant publishedAt, Map<String, String> securities) {
    var published = UUID.randomUUID();
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

  private TransactionOrder givenOrder(
      TulevaFund fund, String isin, TransactionType side, OrderStatus status, LocalDate orderDate) {
    return givenOrder(
        fund,
        isin,
        side,
        status,
        orderDate,
        Map.of(
            "source", TRADE_REGISTRY_SQL_EXPORT_SOURCE, "importKey", "GAS:" + UUID.randomUUID()));
  }

  private TransactionOrder givenOrderFromATradePrepRun(
      TransactionMode mode,
      String isin,
      TransactionType side,
      OrderStatus status,
      LocalDate orderDate) {
    var order = givenOrder(TUV100, isin, side, status, orderDate, Map.of("mode", mode.name()));
    commandRepository.save(
        TransactionCommand.builder()
            .fund(TUV100)
            .mode(mode)
            .asOfDate(orderDate)
            .status(CommandStatus.CALCULATED)
            .batchId(order.getBatch().getId())
            .build());
    return order;
  }

  private void givenExecutedOrderFromATradePrepRun(
      TransactionMode mode,
      String isin,
      TransactionType side,
      LocalDate orderDate,
      LocalDate tradeDate) {
    givenExecuted(givenOrderFromATradePrepRun(mode, isin, side, SENT, orderDate), tradeDate);
  }

  private void givenExecuted(TransactionOrder order, LocalDate tradeDate) {
    order.setOrderStatus(EXECUTED);
    orderRepository.save(order);
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .executionTimestamp(tradeDate.atTime(17, 30).atZone(TALLINN).toInstant())
            .executedQuantity(new BigDecimal("100"))
            .source("SEB_OOTEL")
            .build());
  }

  private TransactionOrder givenOrder(
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
    return orderRepository.save(
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
