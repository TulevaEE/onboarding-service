package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.position.FundPositionBackfillJob;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebReportReimportJobTest {

  @Mock private InvestmentReportRepository reportRepository;
  @Mock private SebReportSource sebReportSource;
  @Mock private InvestmentReportService reportService;
  @Mock private FundPositionBackfillJob backfillJob;

  @InjectMocks private SebReportReimportJob job;

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 1, 26);

  @Test
  void reimportAndBackfill_extractsMetadataAndReimports() {
    byte[] csvBytes = "Sent:;2026-01-26\nAs of:;2026-01-25\ndata".getBytes();
    InvestmentReport existingReport =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(POSITIONS)
            .reportDate(REPORT_DATE)
            .rawData(List.of(Map.of("key", "value")))
            .metadata(Map.of("s3Bucket", "tuleva-investment-reports"))
            .createdAt(Instant.now())
            .build();

    when(reportRepository.findAllByProviderAndReportType(SEB, POSITIONS))
        .thenReturn(List.of(existingReport));
    when(sebReportSource.fetch(POSITIONS, REPORT_DATE))
        .thenReturn(Optional.of(new ByteArrayInputStream(csvBytes)));
    when(sebReportSource.extractCsvMetadata(csvBytes))
        .thenReturn(Map.of("sentDate", "2026-01-26", "asOfDate", "2026-01-25"));
    when(sebReportSource.getCsvDelimiter()).thenReturn(';');
    when(sebReportSource.getHeaderRowIndex()).thenReturn(5);

    job.reimportAndBackfill();

    verify(reportService)
        .saveReport(
            eq(SEB),
            eq(POSITIONS),
            eq(REPORT_DATE),
            any(InputStream.class),
            eq(';'),
            eq(5),
            eq(
                Map.of(
                    "s3Bucket",
                    "tuleva-investment-reports",
                    "sentDate",
                    "2026-01-26",
                    "asOfDate",
                    "2026-01-25")));
    verify(backfillJob).backfillDates();
  }

  @Test
  void reimportAndBackfill_skipsWhenS3FileNotFound() {
    InvestmentReport existingReport =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(POSITIONS)
            .reportDate(REPORT_DATE)
            .rawData(List.of(Map.of("key", "value")))
            .metadata(Map.of())
            .createdAt(Instant.now())
            .build();

    when(reportRepository.findAllByProviderAndReportType(SEB, POSITIONS))
        .thenReturn(List.of(existingReport));
    when(sebReportSource.fetch(POSITIONS, REPORT_DATE)).thenReturn(Optional.empty());

    job.reimportAndBackfill();

    verify(reportService, never())
        .saveReport(any(), any(), any(), any(), anyChar(), anyInt(), any());
    verify(backfillJob).backfillDates();
  }

  @Test
  void reimportAndBackfill_handlesNoReports() {
    when(reportRepository.findAllByProviderAndReportType(SEB, POSITIONS)).thenReturn(List.of());

    job.reimportAndBackfill();

    verify(sebReportSource, never()).fetch(any(), any());
    verify(backfillJob).backfillDates();
  }
}
