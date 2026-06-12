package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.report.ReportProvider.EPIS;
import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.ReportType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class EpisReportSummaryRepositoryTest {

  @Autowired private EpisReportSummaryRepository repository;
  @Autowired private InvestmentReportRepository investmentReportRepository;

  @Test
  void save_persistsJsonbDataAndSetsCreatedAt() {
    Long reportId = saveInvestmentReport(R45, LocalDate.of(2026, 6, 10));

    EpisReportSummary saved =
        repository.save(
            EpisReportSummary.builder()
                .reportId(reportId)
                .reportType(R45)
                .reportDate(LocalDate.of(2026, 6, 10))
                .fund(TUK75)
                .fundIsin(TUK75.getIsin())
                .data(Map.of("inflowEur", 150000.00, "outflowEur", 230000.00, "netEur", -80000.00))
                .build());

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    Optional<EpisReportSummary> found = repository.findById(saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getData())
        .isEqualTo(Map.of("inflowEur", 150000.00, "outflowEur", 230000.00, "netEur", -80000.00));
    assertThat(found.get().getFund()).isEqualTo(TUK75);
    assertThat(found.get().getReportType()).isEqualTo(R45);
  }

  @Test
  void findLatestByTypeAndFund_returnsMostRecentReportDate() {
    Long olderReportId = saveInvestmentReport(R17_PEVA, LocalDate.of(2026, 4, 1));
    Long newerReportId = saveInvestmentReport(R17_PEVA, LocalDate.of(2026, 6, 1));
    repository.save(summary(olderReportId, R17_PEVA, LocalDate.of(2026, 4, 1)));
    EpisReportSummary newer =
        repository.save(summary(newerReportId, R17_PEVA, LocalDate.of(2026, 6, 1)));

    Optional<EpisReportSummary> latest =
        repository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R17_PEVA, TUK75);

    assertThat(latest).contains(newer);
  }

  @Test
  void findLatestByTypeAndFund_emptyWhenNoMatchingFund() {
    Long reportId = saveInvestmentReport(R17_PEVA, LocalDate.of(2026, 6, 1));
    repository.save(summary(reportId, R17_PEVA, LocalDate.of(2026, 6, 1)));

    Optional<EpisReportSummary> latest =
        repository.findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R17_PEVA, TUK00);

    assertThat(latest).isEmpty();
  }

  private EpisReportSummary summary(Long reportId, ReportType reportType, LocalDate reportDate) {
    return EpisReportSummary.builder()
        .reportId(reportId)
        .reportType(reportType)
        .reportDate(reportDate)
        .fund(TUK75)
        .fundIsin(TUK75.getIsin())
        .data(Map.of("pikUnits", 1234.567890))
        .build();
  }

  private Long saveInvestmentReport(ReportType reportType, LocalDate reportDate) {
    return investmentReportRepository
        .save(
            InvestmentReport.builder()
                .provider(EPIS)
                .reportType(reportType)
                .reportDate(reportDate)
                .createdAt(Instant.now())
                .build())
        .getId();
  }
}
