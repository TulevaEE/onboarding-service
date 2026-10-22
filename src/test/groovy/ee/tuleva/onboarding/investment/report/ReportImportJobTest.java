package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.pipeline.PipelineNotifier;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ReportImportJobTest {

  @Mock private ReportSource source;
  @Mock private InvestmentReportRepository reportRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PipelineTracker pipelineTracker;
  @Mock private PipelineNotifier pipelineNotifier;
  @Mock private OperationsNotificationService notificationService;

  private InvestmentReportService reportService;
  private ReportImportJob job;

  private static final String SAMPLE_CSV = "col1;col2\nval1;val2";

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T09:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @BeforeEach
  void setUp() {
    reportService =
        new InvestmentReportService(reportRepository, new CsvToJsonConverter(), FIXED_CLOCK);
    job =
        new ReportImportJob(
            List.of(source),
            reportService,
            FIXED_CLOCK,
            eventPublisher,
            pipelineTracker,
            pipelineNotifier,
            notificationService);
  }

  @Test
  void aResentFileIdenticalToTheStoredOneSaysNothingChanges() {
    var date = LocalDate.now(FIXED_CLOCK).minusDays(1);
    sebResends(date, storedWithFingerprintOf(SAMPLE_CSV));

    job.importForDate(date);

    verify(notificationService)
        .sendMessage(
            "SEB re-sent seb/" + date + "_positions.csv: identical to the stored file", INVESTMENT);
  }

  @Test
  void theResendNoticeComesBeforeTheReImportItIntroduces() {
    var date = LocalDate.now(FIXED_CLOCK).minusDays(1);
    sebResends(date, storedWithFingerprintOf("col1;col2\nval1;other"));

    job.importForDate(date);

    var inOrder = inOrder(notificationService, eventPublisher);
    inOrder.verify(notificationService).sendMessage(any(), eq(INVESTMENT));
    inOrder.verify(eventPublisher).publishEvent(any(Object.class));
  }

  @Test
  void aSlackFailureOnTheResendNoticeDoesNotStopTheImport() {
    var date = LocalDate.now(FIXED_CLOCK).minusDays(1);
    sebResends(date, storedWithFingerprintOf(SAMPLE_CSV));
    doThrow(new IllegalStateException("Slack is down"))
        .when(notificationService)
        .sendMessage(any(), eq(INVESTMENT));

    job.importForDate(date);

    verify(reportRepository).save(any(InvestmentReport.class));
    verify(eventPublisher)
        .publishEvent(
            new ee.tuleva.onboarding.investment.event.ReportImportCompleted(
                SEB, POSITIONS, date, 1));
  }

  @Test
  void aResentFileThatDiffersFromTheStoredOneSaysSo() {
    var date = LocalDate.now(FIXED_CLOCK).minusDays(1);
    sebResends(date, storedWithFingerprintOf("col1;col2\nval1;other"));

    job.importForDate(date);

    verify(notificationService)
        .sendMessage(
            "SEB re-sent seb/" + date + "_positions.csv: it differs from the stored file",
            INVESTMENT);
  }

  @Test
  void aResendOfAFileStoredBeforeFingerprintsSaysItCannotCompare() {
    var date = LocalDate.now(FIXED_CLOCK).minusDays(1);
    sebResends(date, new HashMap<>(Map.of("s3LastModified", "2026-02-05T08:00:00Z")));

    job.importForDate(date);

    verify(notificationService)
        .sendMessage(
            "SEB re-sent seb/"
                + date
                + "_positions.csv: the stored file has no fingerprint to"
                + " compare with",
            INVESTMENT);
  }

  @Test
  void theFirstImportOfAFileIsNotAResendButStoresItsFingerprint() {
    setupReportRepositoryMocks();
    var date = LocalDate.now(FIXED_CLOCK).minusDays(1);
    sebBucketHolds(date, Instant.parse("2026-02-05T14:00:00Z"));

    job.importForDate(date);

    verifyNoInteractions(notificationService);
    verify(reportRepository)
        .save(
            argThat(
                (InvestmentReport report) ->
                    fingerprintOf(SAMPLE_CSV).equals(report.getMetadata().get("csvSha256"))));
  }

  private void sebResends(LocalDate date, Map<String, Object> storedMetadata) {
    when(reportRepository.save(any(InvestmentReport.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(SEB, POSITIONS, date))
        .thenReturn(Optional.of(InvestmentReport.builder().metadata(storedMetadata).build()));
    sebBucketHolds(date, Instant.parse("2026-02-05T14:00:00Z"));
  }

  private void sebBucketHolds(LocalDate date, Instant lastModified) {
    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(source.getLastModified(POSITIONS, date)).thenReturn(Optional.of(lastModified));
    when(source.fetch(POSITIONS, date))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(source.getBucket()).thenReturn("tuleva-investment-reports");
    when(source.getKey(POSITIONS, date)).thenReturn("seb/" + date + "_positions.csv");
    when(source.extractCsvMetadata(any())).thenReturn(Map.of());
  }

  private Map<String, Object> storedWithFingerprintOf(String csv) {
    return new HashMap<>(
        Map.of("s3LastModified", "2026-02-05T08:00:00Z", "csvSha256", fingerprintOf(csv)));
  }

  private static String fingerprintOf(String csv) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(csv.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
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
    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(source.fetch(eq(POSITIONS), eq(date)))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(source.getBucket()).thenReturn("tuleva-investment-reports");
    when(source.getKey(POSITIONS, date)).thenReturn("seb/2026-01-15_positions.csv");
    when(source.extractCsvMetadata(any())).thenReturn(Map.of());

    job.importForDate(date);

    verify(reportRepository).save(any(InvestmentReport.class));
  }

  @Test
  void importForDate_skipsExistingReports() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(SEB, POSITIONS, date))
        .thenReturn(Optional.of(InvestmentReport.builder().build()));

    job.importForDate(date);

    verify(reportRepository, never()).save(any());
    verify(source, never()).fetch(any(), any());
  }

  @Test
  void importForDate_refetchesModifiedReport_forRecentDate() {
    setupReportRepositoryMocks();
    LocalDate date = LocalDate.now(FIXED_CLOCK).minusDays(1);
    Instant oldModified = Instant.parse("2026-02-05T08:00:00Z");
    Instant newModified = Instant.parse("2026-02-05T14:00:00Z");

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("s3LastModified", oldModified.toString());
    InvestmentReport existing = InvestmentReport.builder().metadata(metadata).build();

    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(SEB, POSITIONS, date))
        .thenReturn(Optional.of(existing));
    when(source.getLastModified(POSITIONS, date)).thenReturn(Optional.of(newModified));
    when(source.fetch(eq(POSITIONS), eq(date)))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(source.getBucket()).thenReturn("tuleva-investment-reports");
    when(source.getKey(POSITIONS, date)).thenReturn("seb/" + date + "_positions.csv");
    when(source.extractCsvMetadata(any())).thenReturn(Map.of());

    job.importForDate(date);

    verify(source).fetch(POSITIONS, date);
    verify(reportRepository).save(any(InvestmentReport.class));
  }

  @Test
  void importForDate_skipsUnmodifiedReport_forRecentDate() {
    LocalDate date = LocalDate.now(FIXED_CLOCK).minusDays(1);
    Instant lastModified = Instant.parse("2026-02-05T08:00:00Z");

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("s3LastModified", lastModified.toString());
    InvestmentReport existing = InvestmentReport.builder().metadata(metadata).build();

    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(SEB, POSITIONS, date))
        .thenReturn(Optional.of(existing));
    when(source.getLastModified(POSITIONS, date)).thenReturn(Optional.of(lastModified));

    job.importForDate(date);

    verify(source, never()).fetch(any(), any());
    verify(reportRepository, never()).save(any());
  }

  @Test
  void importForDate_handlesNoFilesFound() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(source.fetch(any(), any())).thenReturn(Optional.empty());

    job.importForDate(date);

    verify(reportRepository, never()).save(any());
  }

  @Test
  void importForProviderAndDate_importsOnlyMatchingProvider() {
    setupReportRepositoryMocks();
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(source.fetch(eq(POSITIONS), eq(date)))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(source.getBucket()).thenReturn("tuleva-investment-reports");
    when(source.getKey(POSITIONS, date)).thenReturn("seb/2026-01-15_positions.csv");
    when(source.extractCsvMetadata(any())).thenReturn(Map.of());

    job.importForProviderAndDate(SEB, date);

    verify(reportRepository).save(any(InvestmentReport.class));
  }

  @Test
  void importForProviderAndDate_skipsNonMatchingProvider() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(source.getProvider()).thenReturn(SEB);

    job.importForProviderAndDate(ReportProvider.SEB, date);

    verify(source, never()).fetch(any(), any());
    verify(reportRepository, never()).save(any());
  }

  @Test
  void runImport_processesMultipleDays() {
    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(source.getLastModified(any(), any())).thenReturn(Optional.empty());
    when(reportRepository.findByProviderAndReportTypeAndReportDate(any(), any(), any()))
        .thenReturn(Optional.of(InvestmentReport.builder().build()));

    job.runImport();

    verify(reportRepository, times(7))
        .findByProviderAndReportTypeAndReportDate(any(), any(), any());
  }

  @Test
  void aTradeListThatFailsToImportDoesNotStopThatDaysPositions() {
    setupReportRepositoryMocks();
    var date = LocalDate.now(FIXED_CLOCK).minusDays(1);
    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes())
        .thenReturn(List.of(ReportType.PENDING_TRANSACTIONS, POSITIONS));
    when(source.fetch(ReportType.PENDING_TRANSACTIONS, date))
        .thenThrow(new RuntimeException("S3 error"));
    when(source.fetch(POSITIONS, date))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(source.getBucket()).thenReturn("tuleva-investment-reports");
    when(source.getKey(POSITIONS, date)).thenReturn("seb/" + date + "_positions.csv");
    when(source.getLastModified(POSITIONS, date)).thenReturn(Optional.empty());
    when(source.extractCsvMetadata(any())).thenReturn(Map.of());

    job.importForDate(date);

    verify(reportRepository).save(any(InvestmentReport.class));
  }

  @Test
  void runImport_continuesOnError() {
    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(any(), any(), any()))
        .thenThrow(new RuntimeException("DB error"));

    job.runImport();

    verify(reportRepository, times(7))
        .findByProviderAndReportTypeAndReportDate(any(), any(), any());
  }

  @Test
  void forceImportForProviderAndDate_alwaysRefetches_regardlessOfAge() {
    setupReportRepositoryMocks();
    LocalDate oldDate = LocalDate.now(FIXED_CLOCK).minusDays(5);
    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(source.fetch(eq(POSITIONS), eq(oldDate)))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(source.getBucket()).thenReturn("tuleva-investment-reports");
    when(source.getKey(POSITIONS, oldDate)).thenReturn("seb/" + oldDate + "_positions.csv");
    when(source.extractCsvMetadata(any())).thenReturn(Map.of());

    job.forceImportForProviderAndDate(SEB, oldDate);

    verify(source).fetch(POSITIONS, oldDate);
    verify(reportRepository).save(any(InvestmentReport.class));
  }

  @Test
  void shouldRefresh_returnsTrueForModifiedReport_withinLookbackWindow() {
    setupReportRepositoryMocks();
    LocalDate date = LocalDate.now(FIXED_CLOCK).minusDays(5);
    Instant oldModified = Instant.parse("2026-04-01T08:00:00Z");
    Instant newModified = Instant.parse("2026-04-07T11:00:00Z");

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("s3LastModified", oldModified.toString());
    InvestmentReport existing = InvestmentReport.builder().metadata(metadata).build();

    when(source.getProvider()).thenReturn(SEB);
    when(source.getSupportedReportTypes()).thenReturn(List.of(POSITIONS));
    when(reportRepository.findByProviderAndReportTypeAndReportDate(SEB, POSITIONS, date))
        .thenReturn(Optional.of(existing));
    when(source.getLastModified(POSITIONS, date)).thenReturn(Optional.of(newModified));
    when(source.fetch(eq(POSITIONS), eq(date)))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(source.getBucket()).thenReturn("tuleva-investment-reports");
    when(source.getKey(POSITIONS, date)).thenReturn("seb/" + date + "_positions.csv");
    when(source.extractCsvMetadata(any())).thenReturn(Map.of());

    job.importForDate(date);

    verify(source).fetch(POSITIONS, date);
    verify(reportRepository).save(any(InvestmentReport.class));
  }
}
