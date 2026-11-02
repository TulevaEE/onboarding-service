package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient.UploadResult;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressPageReader;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressSlug;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentReportPublisherTest {

  @Mock private InvestmentReportDataService dataService;
  @Mock private InvestmentReportPdfGenerator pdfGenerator;
  @Mock private WordPressMediaClient wordPressClient;
  @Mock private WordPressPageReader pageReader;
  @Mock private EmailService emailService;
  @Mock private LockProvider lockProvider;
  @Mock private SimpleLock lock;
  private InvestmentReportPublisher publisher;

  private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);
  private static final List<TulevaFund> PENSION_FUNDS = List.of(TUK75, TUK00, TUV100);

  @BeforeEach
  void setUp() {
    publisher = publisherFor(PENSION_FUNDS);
    lenient().when(dataService.validateQuantities(any(), eq(MARCH_2026))).thenReturn(List.of());
    lenient().when(pageReader.investmentReportUrl(any())).thenReturn(Optional.empty());
    lenient().when(lockProvider.lock(any())).thenReturn(Optional.of(lock));
  }

  private InvestmentReportPublisher publisherFor(List<TulevaFund> funds) {
    return new InvestmentReportPublisher(
        dataService,
        pdfGenerator,
        wordPressClient,
        pageReader,
        emailService,
        lockProvider,
        Clock.fixed(Instant.parse("2026-04-10T07:00:00Z"), ZoneOffset.UTC),
        new InvestmentReportPublishingProperties(funds));
  }

  private static final Map<String, LocalDate> CONSISTENT_NAV_DATES =
      Map.of(
          "TUK75", LocalDate.of(2026, 3, 31),
          "TUK00", LocalDate.of(2026, 3, 31),
          "TUV100", LocalDate.of(2026, 3, 31));

  private static final UploadResult SAMPLE_UPLOAD =
      new UploadResult(42, "https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");

  @Test
  void publishRunsFullPipeline() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.wordPressUrls()).hasSize(3);
    assertThat(result.emailSent()).isTrue();
    assertThat(result.errors()).isEmpty();

    verify(wordPressClient, times(3)).upload(any(), any());
    verify(wordPressClient, times(3)).updateAcfReportField(any(), eq(42));
    verify(emailService).sendSystemEmail(any());
  }

  @Test
  void aFundLeftOutOfTheConfigurationIsNeitherGeneratedNorPublished() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};
    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.wordPressUrls()).containsOnlyKeys("TUK75", "TUK00", "TUV100");
    verify(dataService, never()).getReportData(eq(TKF100), any());
    verify(wordPressClient, never())
        .upload(eq(FundReportMapping.TKF100.buildPdfFilename(MARCH_2026)), any());
    verify(wordPressClient, never())
        .updateAcfReportField(eq(FundReportMapping.TKF100.pageSlug()), anyInt());
  }

  @Test
  void tkf100IsPublishedOnceConfiguredButNotEmailed() {
    var allFunds = List.of(TUK75, TUK00, TUV100, TKF100);
    publisher = publisherFor(allFunds);
    given(dataService.findNavDates(allFunds, MARCH_2026))
        .willReturn(
            Map.of(
                "TUK75", LocalDate.of(2026, 3, 31),
                "TUK00", LocalDate.of(2026, 3, 31),
                "TUV100", LocalDate.of(2026, 3, 31),
                "TKF100", LocalDate.of(2026, 3, 31)));
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    publisher.publish(MARCH_2026);

    verify(emailService)
        .sendSystemEmail(
            argThat(
                message -> {
                  assertThat(message.getAttachments()).hasSize(3);
                  return true;
                }));

    verify(wordPressClient, times(4)).upload(any(), any());
    verify(wordPressClient, times(4)).updateAcfReportField(any(), eq(42));
  }

  @Test
  void publishAbortsWithoutTouchingAnyPageWhenAFundReportCannotBeProduced() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(FundReportMapping.TUK75.fund(), MARCH_2026))
        .willReturn(context);
    given(dataService.getReportData(FundReportMapping.TUK00.fund(), MARCH_2026))
        .willThrow(new IllegalStateException("No NAV data"));
    given(dataService.getReportData(FundReportMapping.TUV100.fund(), MARCH_2026))
        .willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.wordPressUrls()).isEmpty();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst()).contains("TUK00");
    assertThat(result.emailSent()).isFalse();
    verify(wordPressClient, never()).upload(any(), any());
    verify(wordPressClient, never()).updateAcfReportField(any(), anyInt());
    verify(emailService, never()).sendSystemEmail(any());
  }

  @Test
  void publishAlertsAndSkipsEmailWhenAPageUpdateFailsAfterAllUploads() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund", new BigDecimal("0.998000"));
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), any())).willReturn(SAMPLE_UPLOAD);
    lenient()
        .doThrow(new RuntimeException("ACF down"))
        .when(wordPressClient)
        .updateAcfReportField(eq(FundReportMapping.TUK00.pageSlug()), anyInt());

    var result = publisher.publish(MARCH_2026);

    verify(wordPressClient, times(3)).upload(any(), any());
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst()).contains("TUK00");
    assertThat(result.wordPressUrls()).hasSize(2).doesNotContainKey("TUK00");
    assertThat(result.emailSent()).isFalse();
    verify(emailService)
        .sendSystemEmail(argThat(message -> message.getSubject().contains("pooleli")));
  }

  @Test
  void publishRejectsInconsistentNavDates() {
    var navDates =
        Map.of(
            "TUK75", LocalDate.of(2026, 3, 31),
            "TUK00", LocalDate.of(2026, 3, 30),
            "TUV100", LocalDate.of(2026, 3, 31));
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(navDates);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).hasSize(1);
    assertThat(result.wordPressUrls()).isEmpty();
    assertThat(result.emailSent()).isFalse();
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void publishRejectsMidMonthNavDate() {
    var navDates =
        Map.of(
            "TUK75", LocalDate.of(2026, 3, 15),
            "TUK00", LocalDate.of(2026, 3, 15),
            "TUV100", LocalDate.of(2026, 3, 15));
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(navDates);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).hasSize(1);
    assertThat(result.wordPressUrls()).isEmpty();
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void publishRejectsAllocationOutOfRange() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);

    var badContext = sampleContext("Test Fund", new BigDecimal("0.950000"));
    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(badContext);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).isNotEmpty();
    assertThat(result.errors().getFirst()).contains("allocation");
    verifyNoInteractions(wordPressClient, emailService);
  }

  @Test
  void publishPassesValidationWithGoodData() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);

    var context = sampleContext("Test Fund", new BigDecimal("0.998000"));
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).isEmpty();
    assertThat(result.wordPressUrls()).hasSize(3);
  }

  @Test
  void publishHandlesWordPressUploadFailure() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), any())).willThrow(new RuntimeException("WP unavailable"));

    var result = publisher.publish(MARCH_2026);

    assertThat(result.wordPressUrls()).isEmpty();
    assertThat(result.errors()).hasSize(3);
    assertThat(result.emailSent()).isFalse();
    verify(wordPressClient, never()).updateAcfReportField(any(), anyInt());
    verify(emailService, never()).sendSystemEmail(any());
  }

  @Test
  void publishRejectsMissingNavDateForFund() {
    var navDates =
        Map.of(
            "TUK75", LocalDate.of(2026, 3, 31),
            "TUV100", LocalDate.of(2026, 3, 31));
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(navDates);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst()).contains("TUK00");
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void nothingIsPublishedWhileAFundPageLinksANewerReport() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    given(pageReader.investmentReportUrl(FundReportMapping.TUK00.pageSlug()))
        .willReturn(Optional.of(reportUrl(FundReportMapping.TUK00, YearMonth.of(2026, 4))));

    var result = publisher.publish(MARCH_2026);

    assertThat(result)
        .isEqualTo(
            new InvestmentReportPublishingResult(
                Map.of(),
                false,
                List.of(
                    "Fund page already links a newer report: fund=TUK00, linked=2026-04,"
                        + " publishing=2026-03")));
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void aCorrectedReportForTheMonthAlreadyLinkedIsPublished() {
    givenEveryReportCanBeProduced();
    given(pageReader.investmentReportUrl(FundReportMapping.TUK75.pageSlug()))
        .willReturn(Optional.of(reportUrl(FundReportMapping.TUK75, MARCH_2026)));

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).isEmpty();
    assertThat(result.wordPressUrls()).containsOnlyKeys("TUK75", "TUK00", "TUV100");
  }

  @Test
  void aPageLinkingAnOlderReportOrSomeOtherFileGetsThisMonthsReport() {
    givenEveryReportCanBeProduced();
    given(pageReader.investmentReportUrl(FundReportMapping.TUK75.pageSlug()))
        .willReturn(Optional.of(reportUrl(FundReportMapping.TUK75, YearMonth.of(2026, 2))));
    given(pageReader.investmentReportUrl(FundReportMapping.TUK00.pageSlug()))
        .willReturn(Optional.of(reportUrl(FundReportMapping.TUK75, YearMonth.of(2026, 4))));

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).isEmpty();
    assertThat(result.wordPressUrls()).containsOnlyKeys("TUK75", "TUK00", "TUV100");
  }

  @Test
  void nothingIsPublishedWhenAFundPageCannotBeReadToCheckForANewerReport() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    given(pageReader.investmentReportUrl(FundReportMapping.TUV100.pageSlug()))
        .willThrow(new IllegalStateException("page unreadable"));

    var result = publisher.publish(MARCH_2026);

    assertThat(result)
        .isEqualTo(
            new InvestmentReportPublishingResult(
                Map.of(),
                false,
                List.of(
                    "Could not read the fund page to check it links no newer report: fund=TUV100,"
                        + " error=page unreadable")));
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void aSecondPublishWhileOneIsRunningPublishesNothing() {
    given(lockProvider.lock(any())).willReturn(Optional.empty());

    var result = publisher.publish(MARCH_2026);

    assertThat(result)
        .isEqualTo(
            new InvestmentReportPublishingResult(
                Map.of(),
                false,
                List.of("Another investment report publish is running: month=2026-03")));
    verifyNoInteractions(dataService, pageReader, pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void everyPublishTakesTheSameLockAndReleasesIt() {
    givenEveryReportCanBeProduced();

    publisher.publish(MARCH_2026);
    publisher.publish(MARCH_2026.plusMonths(1));

    verify(lockProvider, times(2))
        .lock(
            argThat(configuration -> configuration.getName().equals("investmentReportPublishing")));
    verify(lock, times(2)).unlock();
  }

  @Test
  void theLockIsReleasedWhenAPublishFails() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026))
        .willThrow(new IllegalStateException("database down"));

    assertThatThrownBy(() -> publisher.publish(MARCH_2026))
        .isInstanceOf(IllegalStateException.class);
    verify(lock).unlock();
  }

  private void givenEveryReportCanBeProduced() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};
    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);
  }

  private static String reportUrl(FundReportMapping mapping, YearMonth month) {
    return "https://tuleva.ee/wp-content/uploads/2026/05/"
        + WordPressSlug.of(mapping.buildPdfFilename(month));
  }

  private static InvestmentReportContext sampleContext(String fundTitle) {
    return sampleContext(fundTitle, new BigDecimal("0.998000"));
  }

  private static InvestmentReportContext sampleContext(
      String fundTitle, BigDecimal totalAssetsNavPercent) {
    return new InvestmentReportContext(
        fundTitle,
        "31.03.2026",
        List.of(),
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        BigDecimal.ZERO,
        null,
        totalAssetsNavPercent,
        new BigDecimal("10000000"));
  }
}
