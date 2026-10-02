package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

@ExtendWith(MockitoExtension.class)
class MissingReportAsOfDateAlertListenerTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 1, 26);

  @Mock private InvestmentReportService reportService;
  @Mock private OperationsNotificationService notificationService;

  private final Clock clock =
      Clock.fixed(REPORT_DATE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

  private MissingReportAsOfDateAlertListener listener() {
    return new MissingReportAsOfDateAlertListener(reportService, notificationService, clock);
  }

  @Test
  void saysTheReportWasImportedAnyway_whenTheMarkerIsAbsent() {
    storedWithoutAnAsOfDate(POSITIONS, REPORT_DATE, Map.of());

    listener().onReportImportCompleted(imported(POSITIONS, REPORT_DATE));

    then(notificationService)
        .should()
        .sendMessage(contains("Raport imporditi sellegipoolest"), eq(INVESTMENT));
  }

  @Test
  void startsRedAsksForAReviewedResendBeforeEachFundsNavTimeAndPingsTheChannelLast() {
    storedWithoutAnAsOfDate(PENDING_TRANSACTIONS, REPORT_DATE, Map.of());

    listener().onReportImportCompleted(imported(PENDING_TRANSACTIONS, REPORT_DATE));

    then(notificationService)
        .should()
        .sendMessage(
            """
            🔴 SEB PENDING_TRANSACTIONS raportis puudub kasutatav „As of“ kuupäev – 2026-01-26
            Raporti päise esimesest viiest reast ei leitud „As of“ välja – kas see puudub või on \
            päise kuju muutunud.
            Raport imporditi sellegipoolest ja read on dateeritud faili nime kuupäeva järgi. Kui \
            faili nime kuupäev ei ole ridade äripäev, on NAV-i kuupäev ja tehingute reported_date \
            ühe päeva võrra nihkes.
            Helista SEB-le kohe ja palu uus raport, mis on enne saatmist üle vaadatud – kui päis \
            on vigane, võib ka ülejäänud sisu olla vigane. Kui selle kuupäeva NAV on veel \
            arvutamata, peab parandatud fail jõudma enne NAV-arvutust, mis toimub järgmisel \
            tööpäeval: TUK75, TUK00 kell 11:00; TUV100, TKF100 kell 15:20. Uus fail \
            imporditakse automaatselt. <!channel>""",
            INVESTMENT);
  }

  @Test
  void pingsTheChannelInThePlainMessageTextLikeTheOtherActNowAlertsSoSomeonePhonesSeb() {
    storedWithoutAnAsOfDate(POSITIONS, REPORT_DATE, Map.of());

    listener().onReportImportCompleted(imported(POSITIONS, REPORT_DATE));

    then(notificationService).should().sendMessage(contains("<!channel>"), eq(INVESTMENT));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void saysTheHeaderWasNotFound_whenTheMarkerIsAbsent() {
    storedWithoutAnAsOfDate(POSITIONS, REPORT_DATE, Map.of());

    listener().onReportImportCompleted(imported(POSITIONS, REPORT_DATE));

    then(notificationService)
        .should()
        .sendMessage(
            contains("ei leitud „As of“ välja – kas see puudub või on päise kuju muutunud"),
            eq(INVESTMENT));
  }

  @Test
  void namesTheUnreadableValue_whenTheMarkerIsPresentButNotADate() {
    storedWithoutAnAsOfDate(POSITIONS, REPORT_DATE, Map.of("asOfDate", "25.01.2026"));

    listener().onReportImportCompleted(imported(POSITIONS, REPORT_DATE));

    then(notificationService)
        .should()
        .sendMessage(contains("ei õnnestunud lugeda: \"25.01.2026\""), eq(INVESTMENT));
  }

  @Test
  void shortensAnUnreadableValueThatIsTooLongToQuote() {
    storedWithoutAnAsOfDate(POSITIONS, REPORT_DATE, Map.of("asOfDate", "x".repeat(250)));

    listener().onReportImportCompleted(imported(POSITIONS, REPORT_DATE));

    then(notificationService)
        .should()
        .sendMessage(contains("\"" + "x".repeat(100) + "…\""), eq(INVESTMENT));
  }

  @Test
  void staysSilent_whenTheStoredReportCarriesItsAsOfDate() {
    given(reportService.getReport(SEB, POSITIONS, REPORT_DATE))
        .willReturn(Optional.of(report(POSITIONS, REPORT_DATE, Map.of("asOfDate", "2026-01-23"))));

    listener().onReportImportCompleted(imported(POSITIONS, REPORT_DATE));

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void staysSilent_forAReportTypeItsParsersDoNotDateByTheAsOfHeader() {
    listener().onReportImportCompleted(imported(R45, REPORT_DATE));

    then(reportService).shouldHaveNoInteractions();
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void staysSilent_forAnotherProvidersReport() {
    listener()
        .onReportImportCompleted(new ReportImportCompleted(SWEDBANK, POSITIONS, REPORT_DATE, 1));

    then(reportService).shouldHaveNoInteractions();
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void staysSilent_forAFileOlderThanTheImportLooksBackEvenWhenAnAdminReimportStoresIt() {
    storedWithoutAnAsOfDate(POSITIONS, REPORT_DATE.minusDays(8), Map.of());

    listener().onReportImportCompleted(imported(POSITIONS, REPORT_DATE.minusDays(8)));

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void alerts_forANewFileAnywhereInTheImportLookback() {
    storedWithoutAnAsOfDate(POSITIONS, REPORT_DATE.minusDays(7), Map.of());

    listener().onReportImportCompleted(imported(POSITIONS, REPORT_DATE.minusDays(7)));

    then(notificationService).should().sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void theHeaderAlertRunsBeforeTheListenersThatProcessTheReportSoNoneOfThemCanSkipIt()
      throws NoSuchMethodException {
    var listenerMethod =
        MissingReportAsOfDateAlertListener.class.getMethod(
            "onReportImportCompleted", ReportImportCompleted.class);

    assertThat(AnnotationUtils.findAnnotation(listenerMethod, Order.class))
        .extracting(Order::value)
        .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }

  @Test
  void doesNotPropagateANotificationFailure() {
    storedWithoutAnAsOfDate(POSITIONS, REPORT_DATE, Map.of());
    willThrow(new RuntimeException("slack down"))
        .given(notificationService)
        .sendMessage(any(), any());

    assertThatCode(() -> listener().onReportImportCompleted(imported(POSITIONS, REPORT_DATE)))
        .doesNotThrowAnyException();
  }

  private void storedWithoutAnAsOfDate(
      ReportType reportType, LocalDate reportDate, Map<String, Object> metadata) {
    given(reportService.getReport(SEB, reportType, reportDate))
        .willReturn(Optional.of(report(reportType, reportDate, metadata)));
  }

  private static InvestmentReport report(
      ReportType reportType, LocalDate reportDate, Map<String, Object> metadata) {
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(reportType)
        .reportDate(reportDate)
        .rawData(List.of())
        .metadata(metadata)
        .build();
  }

  private static ReportImportCompleted imported(ReportType reportType, LocalDate reportDate) {
    return new ReportImportCompleted(SEB, reportType, reportDate, 1);
  }
}
