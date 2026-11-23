package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class FtUnreadableConfirmationWarningTest {

  private static final LocalDate ALERTS_FROM = LocalDate.of(2026, 6, 1);
  private static final String KEY = "ft-confirmations/a.pdf";
  private static final FtUnreadableConfirmation UNREADABLE =
      new FtUnreadableConfirmation(KEY, "malformed");

  @Mock private FtConfirmationS3Source s3Source;
  @Mock private FtConfirmationDigest digest;
  @Mock private S3Client s3Client;

  private FtUnreadableConfirmationWarning warning;

  @BeforeEach
  void setUp() {
    warning = new FtUnreadableConfirmationWarning(s3Source, digest, ALERTS_FROM);
  }

  @Test
  void pdfUploadedAtTallinnMidnightOnTheDayAlertsStart_isWarnedAbout() {
    given(s3Source.lastModified(KEY))
        .willReturn(Optional.of(Instant.parse("2026-05-31T21:00:00Z")));

    warning.publish(List.of(UNREADABLE));

    then(digest).should().publish(List.of(UNREADABLE));
  }

  @Test
  void pdfUploadedBeforeTallinnMidnightOnTheDayAlertsStart_isNotWarnedAbout() {
    given(s3Source.lastModified(KEY))
        .willReturn(Optional.of(Instant.parse("2026-05-31T20:59:59Z")));

    warning.publish(List.of(UNREADABLE));

    then(digest).should().publish(List.of());
  }

  @Test
  void pdfWhoseUploadTimeS3DoesNotReturn_isStillWarnedAbout() {
    given(s3Source.lastModified(KEY)).willReturn(Optional.empty());

    warning.publish(List.of(UNREADABLE));

    then(digest).should().publish(List.of(UNREADABLE));
  }

  @Test
  void pdfWhoseUploadTimeS3RefusesToShow_isStillWarnedAbout() {
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willThrow(S3Exception.builder().statusCode(403).build());
    FtUnreadableConfirmationWarning warningOverRealSource =
        new FtUnreadableConfirmationWarning(
            new FtConfirmationS3Source(s3Client), digest, ALERTS_FROM);

    warningOverRealSource.publish(List.of(UNREADABLE));

    then(digest).should().publish(List.of(UNREADABLE));
  }

  @Test
  void pdfWhoseUploadTimeCannotBeRead_isStillWarnedAbout() {
    given(s3Source.lastModified(KEY)).willThrow(new IllegalStateException("S3 head failed"));

    warning.publish(List.of(UNREADABLE));

    then(digest).should().publish(List.of(UNREADABLE));
  }
}
