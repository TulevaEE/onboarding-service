package ee.tuleva.onboarding.investment.report.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.github.GitHubPrClient;
import ee.tuleva.onboarding.investment.report.publishing.gmail.GmailDraftClient;
import ee.tuleva.onboarding.investment.report.publishing.gmail.GmailProperties;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
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
  @Mock private GitHubPrClient gitHubPrClient;
  @Mock private GmailDraftClient gmailDraftClient;
  @Mock private GmailProperties gmailProperties;
  @InjectMocks private InvestmentReportPublisher publisher;

  private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);

  @Test
  void publishRunsFullPipeline() {
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes)))
        .willReturn("https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");
    given(gitHubPrClient.createReportPr(any(), eq(MARCH_2026)))
        .willReturn("https://github.com/TulevaEE/wordpress-theme/pull/123");
    given(gmailDraftClient.fetchSignature()).willReturn("<br>Signature");
    given(gmailDraftClient.createDraft(any(), any(), any(), any(), any())).willReturn("draft-id-1");
    given(gmailProperties.to()).willReturn("test@example.com");
    given(gmailProperties.cc()).willReturn("cc@example.com");

    var result = publisher.publish(MARCH_2026);

    assertThat(result.wordPressUrls()).hasSize(4);
    assertThat(result.gitHubPrUrl())
        .isEqualTo("https://github.com/TulevaEE/wordpress-theme/pull/123");
    assertThat(result.gmailDraftId()).isEqualTo("draft-id-1");
    assertThat(result.errors()).isEmpty();

    verify(wordPressClient, times(4)).upload(any(), any());
    verify(gitHubPrClient).createReportPr(any(), eq(MARCH_2026));
    verify(gmailDraftClient).createDraft(any(), any(), any(), any(), any());
  }

  @Test
  void publishExcludesTkf100FromPrAndEmail() {
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes)))
        .willReturn("https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");
    given(gitHubPrClient.createReportPr(any(), eq(MARCH_2026))).willReturn("pr-url");
    given(gmailDraftClient.fetchSignature()).willReturn("sig");
    given(gmailDraftClient.createDraft(any(), any(), any(), any(), any())).willReturn("draft-1");
    given(gmailProperties.to()).willReturn("test@example.com");
    given(gmailProperties.cc()).willReturn("cc@example.com");

    publisher.publish(MARCH_2026);

    // GitHub PR should only have 3 funds (TKF100 excluded)
    verify(gitHubPrClient)
        .createReportPr(
            argThat(
                map -> {
                  assertThat(map).hasSize(3);
                  assertThat(map.keySet())
                      .extracting(FundReportMapping::fund)
                      .noneMatch(f -> f.getCode().equals("TKF100"));
                  return true;
                }),
            eq(MARCH_2026));

    // Gmail should have 3 attachments (TKF100 excluded)
    verify(gmailDraftClient)
        .createDraft(
            any(),
            any(),
            any(),
            any(),
            argThat(
                attachments -> {
                  assertThat(attachments).hasSize(3);
                  return true;
                }));
  }

  @Test
  void publishContinuesOnPartialFailure() {
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    // TUK75 succeeds, TUK00 fails during PDF generation
    given(dataService.getReportData(FundReportMapping.TUK75.fund(), MARCH_2026))
        .willReturn(context);
    given(dataService.getReportData(FundReportMapping.TUK00.fund(), MARCH_2026))
        .willThrow(new IllegalStateException("No NAV data"));
    given(dataService.getReportData(FundReportMapping.TUV100.fund(), MARCH_2026))
        .willReturn(context);
    given(dataService.getReportData(FundReportMapping.TKF100.fund(), MARCH_2026))
        .willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), any()))
        .willReturn("https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");
    given(gitHubPrClient.createReportPr(any(), any())).willReturn("pr-url");
    given(gmailDraftClient.fetchSignature()).willReturn("sig");
    given(gmailDraftClient.createDraft(any(), any(), any(), any(), any())).willReturn("draft-1");
    given(gmailProperties.to()).willReturn("test@example.com");
    given(gmailProperties.cc()).willReturn("cc@example.com");

    var result = publisher.publish(MARCH_2026);

    assertThat(result.wordPressUrls()).hasSize(3);
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst()).contains("TUK00");
    assertThat(result.gitHubPrUrl()).isNotNull();
    assertThat(result.gmailDraftId()).isNotNull();
  }

  private static InvestmentReportContext sampleContext(String fundTitle) {
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
        BigDecimal.ZERO,
        new BigDecimal("10000000"));
  }
}
