package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
class SebFundConfirmationS3SourceTest {

  private static final Instant NOW = Instant.parse("2026-02-10T10:00:00Z");

  @Mock private S3Client s3Client;

  private SebFundConfirmationS3Source source;

  @BeforeEach
  void setUp() {
    source = new SebFundConfirmationS3Source(s3Client, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void recentKeys_listsThePdfsUploadedWithinTheLookbackOldestFirstAcrossEveryPage() {
    given(
            s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket("tuleva-investment-reports")
                    .prefix("seb-confirmations/")
                    .build()))
        .willReturn(
            ListObjectsV2Response.builder()
                .contents(
                    object("seb-confirmations/", NOW),
                    object("seb-confirmations/DLA0000001-001.pdf", NOW.minusSeconds(3600)))
                .isTruncated(true)
                .nextContinuationToken("next")
                .build());
    given(
            s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket("tuleva-investment-reports")
                    .prefix("seb-confirmations/")
                    .continuationToken("next")
                    .build()))
        .willReturn(
            ListObjectsV2Response.builder()
                .contents(
                    object("seb-confirmations/DLA0000002-001.pdf", NOW.minusSeconds(86_400 * 15L)),
                    object("seb-confirmations/DLA0000003-002.pdf", NOW.minusSeconds(86_400 * 13L)))
                .isTruncated(false)
                .build());

    assertThat(source.recentKeys())
        .containsExactly(
            "seb-confirmations/DLA0000003-002.pdf", "seb-confirmations/DLA0000001-001.pdf");
  }

  @Test
  void get_returnsTheObjectBytes() {
    given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .willReturn(
            ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {1, 2}));

    assertThat(source.get("seb-confirmations/DLA0000001-001.pdf")).contains(new byte[] {1, 2});
  }

  @Test
  void get_isEmptyWhenTheObjectDisappeared() {
    given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .willThrow(NoSuchKeyException.builder().build());

    assertThat(source.get("seb-confirmations/DLA0000001-001.pdf")).isEqualTo(Optional.empty());
  }

  @Test
  void tradeReference_isTheFileNameWithoutItsSequenceSuffix() {
    assertThat(SebFundConfirmationS3Source.tradeReference("seb-confirmations/DLA0000001-001.pdf"))
        .isEqualTo("DLA0000001");
  }

  private static S3Object object(String key, Instant lastModified) {
    return S3Object.builder().key(key).lastModified(lastModified).build();
  }
}
