package ee.tuleva.onboarding.investment.report.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient.UploadResult;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentReportPublisherTest {

  @Mock private InvestmentReportDataService dataService;
  @Mock private InvestmentReportPdfGenerator pdfGenerator;
  @Mock private WordPressMediaClient wordPressClient;
  @Mock private EmailService emailService;
  @InjectMocks private InvestmentReportPublisher publisher;

  private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);

  @BeforeEach
  void setUp() {
    lenient().when(dataService.validateQuantities(any(), eq(MARCH_2026))).thenReturn(List.of());
  }

  private static final Map<String, LocalDate> CONSISTENT_NAV_DATES =
      Map.of(
          "TUK75", LocalDate.of(2026, 3, 31),
          "TUK00", LocalDate.of(2026, 3, 31),
          "TUV100", LocalDate.of(2026, 3, 31),
          "TKF100", LocalDate.of(2026, 3, 31));

  private static final UploadResult SAMPLE_UPLOAD =
      new UploadResult(42, "https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");

  @Test
  void publishRunsFullPipeline() {
    given(dataService.findNavDatesForAllFunds(MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.wordPressUrls()).hasSize(4);
    assertThat(result.emailSent()).isTrue();
    assertThat(result.errors()).isEmpty();

    verify(wordPressClient, times(4)).upload(any(), any());
    verify(wordPressClient, times(4)).updateAcfReportField(any(), eq(42));
    verify(emailService).sendSystemEmail(any());
  }

  @Test
  void publishExcludesTkf100FromEmail() {
    given(dataService.findNavDatesForAllFunds(MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    publisher.publish(MARCH_2026);

    // Email should have 3 PDF attachments (TKF100 excluded)
    verify(emailService)
        .sendSystemEmail(
            argThat(
                message -> {
                  assertThat(message.getAttachments()).hasSize(3);
                  return true;
                }));

    // All 4 funds get uploaded and ACF updated
    verify(wordPressClient, times(4)).upload(any(), any());
    verify(wordPressClient, times(4)).updateAcfReportField(any(), eq(42));
  }

  @Test
  void publishUploadsSucceedingFundsButSkipsEmailWhenAFundFails() {
    given(dataService.findNavDatesForAllFunds(MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(FundReportMapping.TUK75.fund(), MARCH_2026))
        .willReturn(context);
    given(dataService.getReportData(FundReportMapping.TUK00.fund(), MARCH_2026))
        .willThrow(new IllegalStateException("No NAV data"));
    given(dataService.getReportData(FundReportMapping.TUV100.fund(), MARCH_2026))
        .willReturn(context);
    given(dataService.getReportData(FundReportMapping.TKF100.fund(), MARCH_2026))
        .willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), any())).willReturn(SAMPLE_UPLOAD);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.wordPressUrls()).hasSize(3);
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst()).contains("TUK00");
    assertThat(result.emailSent()).isFalse();
    verify(emailService, never()).sendSystemEmail(any());
  }

  @Test
  void publishRejectsInconsistentNavDates() {
    var navDates =
        Map.of(
            "TUK75", LocalDate.of(2026, 3, 31),
            "TUK00", LocalDate.of(2026, 3, 30),
            "TUV100", LocalDate.of(2026, 3, 31),
            "TKF100", LocalDate.of(2026, 3, 31));
    given(dataService.findNavDatesForAllFunds(MARCH_2026)).willReturn(navDates);

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
            "TUV100", LocalDate.of(2026, 3, 15),
            "TKF100", LocalDate.of(2026, 3, 15));
    given(dataService.findNavDatesForAllFunds(MARCH_2026)).willReturn(navDates);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).hasSize(1);
    assertThat(result.wordPressUrls()).isEmpty();
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void publishRejectsAllocationOutOfRange() {
    given(dataService.findNavDatesForAllFunds(MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);

    var badContext = sampleContext("Test Fund", new BigDecimal("0.950000"));
    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(badContext);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).isNotEmpty();
    assertThat(result.errors().getFirst()).contains("allocation");
    verifyNoInteractions(wordPressClient, emailService);
  }

  @Test
  void publishPassesValidationWithGoodData() {
    given(dataService.findNavDatesForAllFunds(MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);

    var context = sampleContext("Test Fund", new BigDecimal("0.998000"));
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).isEmpty();
    assertThat(result.wordPressUrls()).hasSize(4);
  }

  @Test
  void publishHandlesWordPressUploadFailure() {
    given(dataService.findNavDatesForAllFunds(MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), any())).willThrow(new RuntimeException("WP unavailable"));

    var result = publisher.publish(MARCH_2026);

    assertThat(result.wordPressUrls()).isEmpty();
    assertThat(result.errors()).hasSize(4);
    assertThat(result.emailSent()).isFalse();
    verify(wordPressClient, never()).updateAcfReportField(any(), anyInt());
    verify(emailService, never()).sendSystemEmail(any());
  }

  @Test
  void publishRejectsMissingNavDateForFund() {
    var navDates =
        Map.of(
            "TUK75", LocalDate.of(2026, 3, 31),
            "TUV100", LocalDate.of(2026, 3, 31),
            "TKF100", LocalDate.of(2026, 3, 31));
    given(dataService.findNavDatesForAllFunds(MARCH_2026)).willReturn(navDates);

    var result = publisher.publish(MARCH_2026);

    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst()).contains("TUK00");
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
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
