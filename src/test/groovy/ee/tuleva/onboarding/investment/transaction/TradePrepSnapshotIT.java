package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.report.ReportProvider.EPIS;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.context.annotation.FilterType.REGEX;
import static org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.epis.EpisReportSummaryRepository;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.investment.epis.R16FlowCalculationService;
import ee.tuleva.onboarding.investment.epis.R16Phase;
import ee.tuleva.onboarding.investment.epis.R16PhaseCalculator;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.epis.R45Result;
import ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser;
import ee.tuleva.onboarding.investment.epis.parser.R45ReportParser;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
@Import({
  TradePrepSnapshotIT.EpisNavProvider.class,
  TradePrepInputGates.class,
  TradePrepInputGateNotifier.class,
  R16OutflowReader.class,
  R16FlowCalculationService.class,
  R16PhaseCalculator.class,
  R45ReportService.class,
  R45ReportParser.class,
  EpisCsvParser.class,
  PevaRavaPeriodService.class,
  PublicHolidays.class,
  Target2Calendar.class,
  EstonianCalendar.class
})
class TradePrepSnapshotIT {

  @TestConfiguration
  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.investment.epis",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = REGEX,
              pattern = "ee\\.tuleva\\.onboarding\\.investment\\.epis\\.OwnFundNavProvider"))
  static class EpisNavProvider {}

  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 11, 18);

  @MockitoBean FundNavQueryService fundNavQueryService;
  @MockitoBean InvestmentParameterRepository investmentParameterRepository;
  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean TradePrepPriceGate priceGate;
  @MockitoBean TradePrepPositionGate positionGate;

  @Autowired TradePrepInputGates gates;
  @Autowired R45ReportService r45ReportService;
  @Autowired R16FlowCalculationService r16FlowCalculationService;
  @Autowired R16PhaseCalculator r16PhaseCalculator;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired InvestmentReportRepository reportRepository;
  @Autowired EpisReportSummaryRepository summaryRepository;
  @Autowired PlatformTransactionManager transactionManager;

  private @Nullable Long positionId;
  private @Nullable Long reportId;

  @AfterEach
  void removeTheCommittedRows() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              if (reportId != null) {
                summaryRepository.deleteByReportId(reportId);
                reportRepository.deleteById(reportId);
              }
              if (positionId != null) {
                fundPositionRepository.deleteById(positionId);
              }
            });
  }

  @Test
  void aSameDayR45ReuploadDuringTradePrepDoesNotMixItsVersions() {
    committed(
        () -> {
          positionId = position(TUV100, LocalDate.of(2026, 11, 17));
          reportId = storedR45("RED;EE3600001707;0.90000;0;300.00;19.11.2026");
          r45ReportService.processAndStore(reportId);
        });
    TransactionTemplate tradePrep = new TransactionTemplate(transactionManager);
    tradePrep.setIsolationLevel(ISOLATION_REPEATABLE_READ);

    tradePrep.executeWithoutResult(
        status -> {
          assertThat(gates.failures(TUV100, TRADE_DATE)).isEmpty();

          reuploadInItsOwnTransaction("SUB;EE3600109435;0.80000;0;1500.00;19.11.2026");

          assertThat(r45ReportService.getFlowsOn(TRADE_DATE))
              .containsOnlyKeys(TUV100)
              .extractingByKey(TUV100)
              .extracting(R45Result::outflowEur)
              .satisfies(outflow -> assertThat(outflow).isEqualByComparingTo("300.00"));
          assertThat(
                  r16PhaseCalculator.tradePrepPhaseFor(
                      TUV100,
                      r16FlowCalculationService.paymentDates(YearMonth.of(2026, 11)),
                      TRADE_DATE))
              .isEqualTo(R16Phase.IGNORE);
        });

    assertThat(r45ReportService.getFlowsOn(TRADE_DATE)).doesNotContainKey(TUV100);
  }

  private void reuploadInItsOwnTransaction(String row) {
    TransactionTemplate upload = new TransactionTemplate(transactionManager);
    upload.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    upload.executeWithoutResult(
        status -> {
          InvestmentReport report = reportRepository.findById(reportId).orElseThrow();
          report.setRawData(List.of(Map.of("csv", csv(row))));
          reportRepository.save(report);
          r45ReportService.processAndStore(reportId);
        });
  }

  private void committed(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
  }

  private Long position(TulevaFund fund, LocalDate navDate) {
    return fundPositionRepository
        .save(
            FundPosition.builder()
                .navDate(navDate)
                .fund(fund)
                .accountType(CASH)
                .accountName("Cash account")
                .marketValue(new BigDecimal("1000.00"))
                .currency("EUR")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build())
        .getId();
  }

  private Long storedR45(String row) {
    return reportRepository
        .save(
            InvestmentReport.builder()
                .provider(EPIS)
                .reportType(R45)
                .reportDate(TRADE_DATE)
                .rawData(List.of(Map.of("csv", csv(row))))
                .createdAt(Instant.now())
                .build())
        .getId();
  }

  private static String csv(String row) {
    return Stream.of(
            "Tehtud: %s;;;;;".formatted(TRADE_DATE.format(ofPattern("dd.MM.yyyy"))),
            ";;;;;",
            "Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev",
            row)
        .collect(joining("\n", "", "\n"));
  }
}
