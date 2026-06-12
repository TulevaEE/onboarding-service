package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.EPIS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.R16_FORECASTED_PAYMENTS;
import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.epis.parser.EpisCsvParser;
import ee.tuleva.onboarding.investment.epis.parser.R16ReportParser;
import ee.tuleva.onboarding.investment.epis.parser.R17ReportParser;
import ee.tuleva.onboarding.investment.epis.parser.R21ReportParser;
import ee.tuleva.onboarding.investment.epis.parser.R45ReportParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@DataJpaTest
@RecordApplicationEvents
@Import({
  EpisReportIngestionService.class,
  R45ReportService.class,
  OwnFundNavProvider.class,
  PevaRavaPeriodService.class,
  Target2Calendar.class,
  EpisCsvParser.class,
  R16ReportParser.class,
  R17ReportParser.class,
  R21ReportParser.class,
  R45ReportParser.class,
  EpisReportIngestionServiceTest.FixedClockConfiguration.class
})
class EpisReportIngestionServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);

  @TestConfiguration
  static class FixedClockConfiguration {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }

  @Autowired private EpisReportIngestionService service;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private EpisReportSummaryRepository summaryRepository;
  @Autowired private PevaRavaCycleRepository cycleRepository;
  @Autowired private ApplicationEvents events;

  @MockitoBean private FundNavQueryService fundNavQueryService;

  private static final String R17_CSV =
      """
      Seisuga: 01.08.2026;;;;;;;
      ;;;;;;;
      Väärtpaber;NAV;Toiming;PF valitseja/PIK;Hind;Osakud (teenustasuta);Osakud (teenustasuga);Summa
      Tuleva Maailma Aktsiate Pensionifond;0,80;Tagasivõtt;PIK;0,80;100,000;100,000;80,00
      Tuleva Maailma Aktsiate Pensionifond;0,80;Väljalase;Teine PF valitseja;0,80;200,000;200,000;160,00
      Tuleva Maailma Võlakirjade Pensionifond;0,70;Tagasivõtt;Teine PF valitseja;0,70;50,000;50,000;35,00
      """;

  @Test
  void ingestsR17StoringUnitSummariesAndLinkingActiveCycle() {
    EpisReportIngestionResult result = service.ingestReport(R17_PEVA, R17_CSV);

    assertThat(result.reportType()).isEqualTo(R17_PEVA);
    assertThat(result.reportDate()).isEqualTo(TODAY);
    assertThat(result.fundSummaries()).containsOnlyKeys(TUK75, TUK00);
    assertThat(number(result.fundSummaries().get(TUK75), "pikUnits")).isEqualByComparingTo("100");
    assertThat(number(result.fundSummaries().get(TUK75), "switchingNetUnits"))
        .isEqualByComparingTo("200");
    assertThat(number(result.fundSummaries().get(TUK00), "switchingNetUnits"))
        .isEqualByComparingTo("-50");

    InvestmentReport report = reportRepository.findById(result.reportId()).orElseThrow();
    assertThat(report.getProvider()).isEqualTo(EPIS);
    assertThat(report.getRawData()).isEqualTo(List.of(Map.of("csv", R17_CSV)));

    PevaRavaCycleEntity cycle =
        cycleRepository.findByExecDate(LocalDate.of(2026, 9, 1)).orElseThrow();
    assertThat(cycle.getLockDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    assertThat(cycle.getPhase()).isEqualTo(PevaRavaPhase.TUK00_ACTIVE);
    assertThat(cycle.getR17ReportId()).isEqualTo(result.reportId());

    assertThat(events.stream(EpisReportProcessed.class))
        .containsExactly(new EpisReportProcessed(result.reportId(), R17_PEVA, TODAY));
  }

  @Test
  void ingestsR21LinkingSameCycleAsR17() {
    service.ingestReport(R17_PEVA, R17_CSV);
    String r21Csv =
        """
        Maksete kuu: 202609;;;;
        ;;;;
        Väärtpaber;Jooksev NAV;Osakud;Summa;Valuuta
        Tuleva Maailma Aktsiate Pensionifond;0,80;150,000;120,00;EUR
        """;

    EpisReportIngestionResult result = service.ingestReport(R21_RAVA, r21Csv);

    assertThat(number(result.fundSummaries().get(TUK75), "ravaUnits")).isEqualByComparingTo("150");
    assertThat(cycleRepository.findAll()).hasSize(1);
    PevaRavaCycleEntity cycle =
        cycleRepository.findByExecDate(LocalDate.of(2026, 9, 1)).orElseThrow();
    assertThat(cycle.getR17ReportId()).isNotNull();
    assertThat(cycle.getR21ReportId()).isEqualTo(result.reportId());
  }

  @Test
  void reuploadingR16ForSameMonthReplacesSummaries() {
    String firstCsv = r16Csv("1000,000");
    String secondCsv = r16Csv("2000,000");

    service.ingestReport(R16_FORECASTED_PAYMENTS, firstCsv);
    EpisReportIngestionResult result = service.ingestReport(R16_FORECASTED_PAYMENTS, secondCsv);

    List<EpisReportSummary> summaries =
        summaryRepository.findAll().stream()
            .filter(summary -> summary.getReportType() == R16_FORECASTED_PAYMENTS)
            .toList();
    assertThat(summaries).hasSize(1);
    assertThat(summaries.getFirst().getReportDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(number(summaries.getFirst().getData(), "fondimaksedUnits"))
        .isEqualByComparingTo("2000");
    assertThat(number(result.fundSummaries().get(TUK75), "fondimaksedUnits"))
        .isEqualByComparingTo("2000");
  }

  @Test
  void reuploadingSameReportTypeOnSameDayReplacesRawDataAndSummaries() {
    EpisReportIngestionResult first = service.ingestReport(R17_PEVA, R17_CSV);
    EpisReportIngestionResult second = service.ingestReport(R17_PEVA, R17_CSV);

    assertThat(second.reportId()).isEqualTo(first.reportId());
    assertThat(reportRepository.findAllByProviderAndReportType(EPIS, R17_PEVA)).hasSize(1);
    assertThat(summaryRepository.findByReportId(first.reportId())).hasSize(2);
  }

  @Test
  void ingestsR45StoringFlowsAndRedRowSettlementDates() {
    String r45Csv =
        """
        Tehtud: 14.08.2026;;;;;
        ;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109435;0,80000;0;1500,00;17.08.2026
        RED;EE3600001707;0,90000;0;300,00;18.08.2026
        """;

    EpisReportIngestionResult result = service.ingestReport(R45, r45Csv);

    assertThat(result.fundSummaries()).containsOnlyKeys(TUK75, TUV100);
    assertThat(number(result.fundSummaries().get(TUV100), "outflowEur"))
        .isEqualByComparingTo("300.00");
    assertThat(result.fundSummaries().get(TUV100).get("redRowSettlementDates"))
        .isEqualTo(List.of("2026-08-18"));
  }

  @Test
  void rejectsUnsupportedReportType() {
    assertThatThrownBy(() -> service.ingestReport(POSITIONS, "csv"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private String r16Csv(String fondimaksedUnits) {
    return """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 08;;;;;;
        Väärtpaber;Jooksev NAV;Fondimaksed Osakud;Fondimaksed Summa;Ühekordsed maksed Osakud;Ühekordsed maksed Summa;Valuuta
        EE3600109435;0,80;%s;800,00;0;0;EUR
        """
        .formatted(fondimaksedUnits);
  }

  private BigDecimal number(Map<String, Object> data, String key) {
    return new BigDecimal(String.valueOf(data.get(key)));
  }
}
