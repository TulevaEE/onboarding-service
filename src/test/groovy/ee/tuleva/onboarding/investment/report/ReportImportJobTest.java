package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportImportJobTest {

  @Mock private ReportSource source;
  @Mock private InvestmentReportRepository reportRepository;

  private InvestmentReportService reportService;
  private ReportImportJob job;

  private static final String SAMPLE_CSV = "col1;col2\nval1;val2";

  @BeforeEach
  void setUp() {
    reportService = new InvestmentReportService(reportRepository, new CsvToJsonConverter());
    job = new ReportImportJob(List.of(source), reportService);
  }

  private void setupReportRepositoryMocks() {
    when(reportRepository.findByProviderAndReportTypeAndReportDate(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(reportRepository.save(any(InvestmentReport.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void importForDate_importsAllReportTypesFromAllSources() {
    setupReportRepositoryMocks();
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(source.getProvider()).thenReturn(SWEDBANK);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(source.fetch(eq(POSITIONS), eq(date)))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(source.getBucket()).thenReturn("tuleva-investment-reports");
    when(source.getKey(POSITIONS, date)).thenReturn("portfolio/2026-01-15.csv");
    when(source.extractCsvMetadata(any())).thenReturn(Map.of());

    job.importForDate(date);

    verify(reportRepository).save(any(InvestmentReport.class));
  }

  @Test
  void importForDate_skipsExistingReports() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(source.getProvider()).thenReturn(SWEDBANK);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(SWEDBANK, POSITIONS, date))
        .thenReturn(Optional.of(InvestmentReport.builder().build()));

    job.importForDate(date);

    verify(reportRepository, never()).save(any());
    verify(source, never()).fetch(any(), any());
  }

  @Test
  void importForDate_handlesNoFilesFound() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(source.getProvider()).thenReturn(SWEDBANK);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(source.fetch(any(), any())).thenReturn(Optional.empty());

    job.importForDate(date);

    verify(reportRepository, never()).save(any());
  }

  @Test
  void runImport_processesMultipleDays() {
    when(source.getProvider()).thenReturn(SWEDBANK);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(any(), any(), any()))
        .thenReturn(Optional.of(InvestmentReport.builder().build()));

    job.runImport();

    verify(reportRepository, times(7))
        .findByProviderAndReportTypeAndReportDate(any(), any(), any());
  }

  @Test
  void runImport_continuesOnError() {
    when(source.getProvider()).thenReturn(SWEDBANK);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(any(), any(), any()))
        .thenThrow(new RuntimeException("DB error"));

    job.runImport();

    verify(reportRepository, times(7))
        .findByProviderAndReportTypeAndReportDate(any(), any(), any());
  }
}
