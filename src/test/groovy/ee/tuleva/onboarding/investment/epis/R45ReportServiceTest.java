package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.EPIS;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser;
import ee.tuleva.onboarding.investment.epis.parser.R45ReportParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class R45ReportServiceTest {

  private static final long REPORT_ID = 42L;
  private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);

  private final InvestmentReportRepository reportRepository =
      mock(InvestmentReportRepository.class);
  private final EpisReportSummaryRepository summaryRepository =
      mock(EpisReportSummaryRepository.class);
  private final FundNavQueryService fundNavQueryService = mock(FundNavQueryService.class);

  private final R45ReportService service =
      new R45ReportService(
          reportRepository,
          summaryRepository,
          new OwnFundNavProvider(fundNavQueryService),
          new R45ReportParser(new EpisCsvParser()),
          new EpisCsvParser());

  @Test
  void processAndStoreReplacesSummariesWithFlowsAndRedRowSettlementDates() {
    String csv =
        """
        Tehtud: 14.08.2026;;;;;
        ;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109435;0,80000;0;1500,00;17.08.2026
        RED;EE3600109435;0,80000;0;500,00;18.08.2026
        RED;EE3600001707;0,90000;0;300,00;18.08.2026
        RED;EE3600001707;0,90000;0;100,00;25.08.2026
        """;
    givenStoredReport(csv);

    Map<TulevaFund, R45Result> results = service.processAndStore(REPORT_ID);

    assertThat(results).containsOnlyKeys(TUK75, TUV100);
    assertThat(results.get(TUK75))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new R45Result(
                new BigDecimal("1500.00"), new BigDecimal("500.00"), new BigDecimal("1000.00")));
    assertThat(results.get(TUV100))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new R45Result(
                new BigDecimal("0"), new BigDecimal("400.00"), new BigDecimal("-400.00")));

    verify(summaryRepository).deleteByReportId(REPORT_ID);
    verify(summaryRepository)
        .saveAll(
            List.of(
                summary(
                    TUK75,
                    Map.of(
                        "inflowEur", new BigDecimal("1500.00"),
                        "outflowEur", new BigDecimal("500.00"),
                        "netEur", new BigDecimal("1000.00"),
                        "redRowSettlementDates", List.of("2026-08-18"))),
                summary(
                    TUV100,
                    Map.of(
                        "inflowEur",
                        BigDecimal.ZERO,
                        "outflowEur",
                        new BigDecimal("400.00"),
                        "netEur",
                        new BigDecimal("-400.00"),
                        "redRowSettlementDates",
                        List.of("2026-08-18", "2026-08-25")))));
  }

  @Test
  void usesOwnFundNavAsFallbackWhenReportRowHasNoNavOrAmount() {
    String csv =
        """
        Tehtud: 14.08.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        RED;EE3600109443;0;1000,000;0;18.08.2026
        """;
    givenStoredReport(csv);
    given(fundNavQueryService.findLatestNavDateOnOrBefore(TUK00.getCode(), TODAY))
        .willReturn(Optional.of(LocalDate.of(2026, 8, 13)));
    given(fundNavQueryService.findNavPerUnit(TUK00.getCode(), LocalDate.of(2026, 8, 13)))
        .willReturn(Optional.of(new BigDecimal("0.65")));

    Map<TulevaFund, R45Result> results = service.processAndStore(REPORT_ID);

    assertThat(results.get(TUK00).outflowEur()).isEqualByComparingTo("650.00");
  }

  @Test
  void throwsWhenReportNotFound() {
    given(reportRepository.findById(REPORT_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.processAndStore(REPORT_ID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenReportIsNotR45() {
    given(reportRepository.findById(REPORT_ID))
        .willReturn(
            Optional.of(
                InvestmentReport.builder()
                    .id(REPORT_ID)
                    .provider(EPIS)
                    .reportType(ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA)
                    .reportDate(TODAY)
                    .build()));

    assertThatThrownBy(() -> service.processAndStore(REPORT_ID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getLatestFlowsReturnsLatestPerFund() {
    given(summaryRepository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R45, TUK75))
        .willReturn(
            Optional.of(
                summary(
                    TUK75, Map.of("inflowEur", 1500.00, "outflowEur", 500.00, "netEur", 1000.00))));

    Map<TulevaFund, R45Result> flows = service.getLatestFlows();

    assertThat(flows).containsOnlyKeys(TUK75);
    assertThat(flows.get(TUK75))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new R45Result(
                new BigDecimal("1500.0"), new BigDecimal("500.0"), new BigDecimal("1000.0")));
  }

  @Test
  void getLatestRedRowSettlementDatesReturnsDatesForFund() {
    given(summaryRepository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R45, TUV100))
        .willReturn(
            Optional.of(
                summary(
                    TUV100,
                    Map.of(
                        "inflowEur",
                        0,
                        "outflowEur",
                        400.00,
                        "netEur",
                        -400.00,
                        "redRowSettlementDates",
                        List.of("2026-08-18", "2026-08-25")))));

    assertThat(service.getLatestRedRowSettlementDates(TUV100))
        .containsExactly(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 25));
  }

  @Test
  void getLatestRedRowSettlementDatesIsEmptyWithoutR45Summary() {
    assertThat(service.getLatestRedRowSettlementDates(TUV100)).isEmpty();
  }

  private void givenStoredReport(String csv) {
    given(reportRepository.findById(REPORT_ID))
        .willReturn(
            Optional.of(
                InvestmentReport.builder()
                    .id(REPORT_ID)
                    .provider(EPIS)
                    .reportType(R45)
                    .reportDate(TODAY)
                    .rawData(List.of(Map.of("csv", csv)))
                    .build()));
  }

  private EpisReportSummary summary(TulevaFund fund, Map<String, Object> data) {
    return EpisReportSummary.builder()
        .reportId(REPORT_ID)
        .reportType(R45)
        .reportDate(TODAY)
        .fund(fund)
        .fundIsin(fund.getIsin())
        .data(data)
        .build();
  }
}
