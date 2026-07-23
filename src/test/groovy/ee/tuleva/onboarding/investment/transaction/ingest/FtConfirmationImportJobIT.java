package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtConfirmationAuditRecorder.FT_CONFIRMATION_VERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FtConfirmationImportJobIT {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-09T08:00:00Z"), ZoneOffset.UTC);
  private static final String KEY = "ft-confirmations/allocation-1.pdf";
  private static final String DEDUP_KEY =
      TKF100.name() + "|IE00BFG1TM61|2026-01-15|2500|12.3456|NORMAL";

  @MockitoBean private S3Client s3Client;

  @Autowired private FtConfirmationPdfParser parser;
  @Autowired private FtConfirmationVerificationService verificationService;
  @Autowired private TransactionAuditEventRepository auditEventRepository;

  private FtConfirmationImportJob job;

  @BeforeEach
  void setUp() {
    FtConfirmationS3Source s3Source = new FtConfirmationS3Source(s3Client);
    job = new FtConfirmationImportJob(s3Source, parser, verificationService, FIXED_CLOCK);
  }

  @Test
  void run_recordsVerificationOutcomeForNewPdf_andSkipsDuplicateOnSecondRun() {
    stubOneConfirmationPdfUnderPrefix();

    job.run();

    List<TransactionAuditEvent> events =
        auditEventRepository.findByEventTypeAndDedupKey(FT_CONFIRMATION_VERIFIED, DEDUP_KEY);
    assertThat(events).hasSize(1);

    job.run();

    List<TransactionAuditEvent> eventsAfterSecondRun =
        auditEventRepository.findByEventTypeAndDedupKey(FT_CONFIRMATION_VERIFIED, DEDUP_KEY);
    assertThat(eventsAfterSecondRun).hasSize(1);
  }

  private void stubOneConfirmationPdfUnderPrefix() {
    given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .willReturn(
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key(KEY).build())
                .isTruncated(false)
                .build());

    byte[] pdf = confirmationPdf();
    var response =
        new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            AbortableInputStream.create(new ByteArrayInputStream(pdf)));
    given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(response);
  }

  private static byte[] confirmationPdf() {
    List<String> lines =
        List.of(
            "Trade Confirmation",
            "Dear Sir/Madam,",
            "We confirm the following trade .",
            "Allocation ID: TEST0001-00",
            "Counterparty: TULEVA FONDID AS",
            "Account: Tuleva Additional Investment Fund",
            "Trade Date Time: 20260115-09:32:44 UTC",
            "Trade Date: 20260115",
            "Settlement Date: 20260115",
            "Security Description: SYNTHETIC TEST SECURITY",
            "Bloomberg Ticker: TEST GY Equity",
            "ISIN: IE00BFG1TM61",
            "Your Direction: Buy",
            "Quantity: 2,500",
            "Gross Price: 12.345600 EUR",
            "Net Price: 12.345600 EUR",
            "Settlement Currency: EUR");
    return renderPdf(lines);
  }

  private static byte[] renderPdf(List<String> lines) {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(font, 10);
        cs.newLineAtOffset(50, 750);
        for (String line : lines) {
          cs.showText(line);
          cs.newLineAtOffset(0, -14);
        }
        cs.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
