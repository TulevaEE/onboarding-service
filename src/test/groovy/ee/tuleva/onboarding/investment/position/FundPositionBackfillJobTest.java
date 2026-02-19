package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.position.parser.SwedbankFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundPositionBackfillJobTest {

  @Mock private InvestmentReportRepository reportRepository;
  @Mock private FundPositionRepository positionRepository;
  @Mock private SebFundPositionParser sebParser;
  @Mock private SwedbankFundPositionParser swedbankParser;

  @InjectMocks private FundPositionBackfillJob job;

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 1, 25);
  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 1, 26);

  @Test
  void backfill_updatesNavDateAndReportDateForSebPositions() {
    InvestmentReport sebReport =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(POSITIONS)
            .reportDate(REPORT_DATE)
            .rawData(List.of(Map.of("key", "value")))
            .build();

    FundPosition parsedPosition =
        FundPosition.builder()
            .navDate(NAV_DATE)
            .reportDate(REPORT_DATE)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Cash account in SEB Pank")
            .marketValue(new BigDecimal("1000"))
            .build();

    FundPosition existingPosition =
        FundPosition.builder()
            .id(1L)
            .navDate(REPORT_DATE)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Cash account in SEB Pank")
            .marketValue(new BigDecimal("1000"))
            .createdAt(Instant.now())
            .build();

    when(reportRepository.findAllByProviderAndReportType(SEB, POSITIONS))
        .thenReturn(List.of(sebReport));
    when(reportRepository.findAllByProviderAndReportType(SWEDBANK, POSITIONS))
        .thenReturn(List.of());
    when(sebParser.parse(
            sebReport.getRawData(), sebReport.getReportDate(), sebReport.getMetadata()))
        .thenReturn(List.of(parsedPosition));
    when(positionRepository.findByNavDateAndFundAndAccountName(
            REPORT_DATE, TKF100, "Cash account in SEB Pank"))
        .thenReturn(existingPosition);

    job.backfillDates();

    assertThat(existingPosition.getNavDate()).isEqualTo(NAV_DATE);
    assertThat(existingPosition.getReportDate()).isEqualTo(REPORT_DATE);
    verify(positionRepository).save(existingPosition);
  }

  @Test
  void backfill_updatesReportDateForSwedbankPositions() {
    InvestmentReport swedbankReport =
        InvestmentReport.builder()
            .provider(SWEDBANK)
            .reportType(POSITIONS)
            .reportDate(REPORT_DATE)
            .rawData(List.of(Map.of("key", "value")))
            .build();

    FundPosition parsedPosition =
        FundPosition.builder()
            .navDate(NAV_DATE)
            .reportDate(REPORT_DATE)
            .fund(TUK75)
            .accountType(SECURITY)
            .accountName("iShares ETF")
            .marketValue(new BigDecimal("5000"))
            .build();

    FundPosition existingPosition =
        FundPosition.builder()
            .id(2L)
            .navDate(NAV_DATE)
            .fund(TUK75)
            .accountType(SECURITY)
            .accountName("iShares ETF")
            .marketValue(new BigDecimal("5000"))
            .createdAt(Instant.now())
            .build();

    when(reportRepository.findAllByProviderAndReportType(SEB, POSITIONS)).thenReturn(List.of());
    when(reportRepository.findAllByProviderAndReportType(SWEDBANK, POSITIONS))
        .thenReturn(List.of(swedbankReport));
    when(swedbankParser.parse(swedbankReport.getRawData(), swedbankReport.getReportDate()))
        .thenReturn(List.of(parsedPosition));
    when(positionRepository.findByNavDateAndFundAndAccountName(NAV_DATE, TUK75, "iShares ETF"))
        .thenReturn(existingPosition);

    job.backfillDates();

    assertThat(existingPosition.getReportDate()).isEqualTo(REPORT_DATE);
    verify(positionRepository).save(existingPosition);
  }

  @Test
  void backfill_skipsWhenNoMatchingPosition() {
    InvestmentReport sebReport =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(POSITIONS)
            .reportDate(REPORT_DATE)
            .rawData(List.of(Map.of("key", "value")))
            .build();

    FundPosition parsedPosition =
        FundPosition.builder()
            .navDate(NAV_DATE)
            .reportDate(REPORT_DATE)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Cash account")
            .marketValue(new BigDecimal("1000"))
            .build();

    when(reportRepository.findAllByProviderAndReportType(SEB, POSITIONS))
        .thenReturn(List.of(sebReport));
    when(reportRepository.findAllByProviderAndReportType(SWEDBANK, POSITIONS))
        .thenReturn(List.of());
    when(sebParser.parse(
            sebReport.getRawData(), sebReport.getReportDate(), sebReport.getMetadata()))
        .thenReturn(List.of(parsedPosition));
    when(positionRepository.findByNavDateAndFundAndAccountName(any(), any(), any()))
        .thenReturn(null);

    job.backfillDates();

    verify(positionRepository, never()).save(any());
  }

  @Test
  void backfill_sebLooksUpByOldNavDateEqualToReportDate() {
    InvestmentReport sebReport =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(POSITIONS)
            .reportDate(REPORT_DATE)
            .rawData(List.of(Map.of("key", "value")))
            .build();

    FundPosition parsedPosition =
        FundPosition.builder()
            .navDate(NAV_DATE)
            .reportDate(REPORT_DATE)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Cash account")
            .marketValue(new BigDecimal("1000"))
            .build();

    when(reportRepository.findAllByProviderAndReportType(SEB, POSITIONS))
        .thenReturn(List.of(sebReport));
    when(reportRepository.findAllByProviderAndReportType(SWEDBANK, POSITIONS))
        .thenReturn(List.of());
    when(sebParser.parse(
            sebReport.getRawData(), sebReport.getReportDate(), sebReport.getMetadata()))
        .thenReturn(List.of(parsedPosition));
    when(positionRepository.findByNavDateAndFundAndAccountName(REPORT_DATE, TKF100, "Cash account"))
        .thenReturn(null);

    job.backfillDates();

    verify(positionRepository)
        .findByNavDateAndFundAndAccountName(REPORT_DATE, TKF100, "Cash account");
  }
}
