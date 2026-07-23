package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
class FtConfirmationS3SourceTest {

  @Mock private S3Client s3Client;

  private FtConfirmationS3Source source;

  @BeforeEach
  void setUp() {
    source = new FtConfirmationS3Source(s3Client);
  }

  @Test
  void list_returnsKeysUnderPrefix() {
    given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .willReturn(
            ListObjectsV2Response.builder()
                .contents(
                    S3Object.builder().key("ft-confirmations/a.pdf").build(),
                    S3Object.builder().key("ft-confirmations/b.pdf").build())
                .isTruncated(false)
                .build());

    List<String> keys = source.list();

    assertThat(keys).containsExactly("ft-confirmations/a.pdf", "ft-confirmations/b.pdf");
  }

  @Test
  void list_excludesThePrefixPlaceholderKeyItself() {
    given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .willReturn(
            ListObjectsV2Response.builder()
                .contents(
                    S3Object.builder().key("ft-confirmations/").build(),
                    S3Object.builder().key("ft-confirmations/a.pdf").build())
                .isTruncated(false)
                .build());

    List<String> keys = source.list();

    assertThat(keys).containsExactly("ft-confirmations/a.pdf");
  }

  @Test
  void list_returnsEmptyListWhenNoObjectsFound() {
    given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .willReturn(ListObjectsV2Response.builder().isTruncated(false).build());

    List<String> keys = source.list();

    assertThat(keys).isEmpty();
  }

  @Test
  void get_returnsBytesForKey() {
    String content = "test-pdf-bytes";
    var response =
        new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            AbortableInputStream.create(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(response);

    Optional<byte[]> result = source.get("ft-confirmations/a.pdf");

    assertThat(result).isPresent();
    assertThat(new String(result.get(), StandardCharsets.UTF_8)).isEqualTo(content);
  }

  @Test
  void get_returnsEmptyWhenKeyNotFound() {
    given(s3Client.getObject(any(GetObjectRequest.class)))
        .willThrow(NoSuchKeyException.builder().message("Not found").build());

    Optional<byte[]> result = source.get("ft-confirmations/missing.pdf");

    assertThat(result).isEmpty();
  }

  @Test
  void get_returnsEmptyOnAccessDenied() {
    S3Exception exception =
        (S3Exception) S3Exception.builder().statusCode(403).message("Forbidden").build();
    given(s3Client.getObject(any(GetObjectRequest.class))).willThrow(exception);

    Optional<byte[]> result = source.get("ft-confirmations/a.pdf");

    assertThat(result).isEmpty();
  }

  @Test
  void get_throwsRuntimeExceptionOnOtherS3Error() {
    S3Exception exception =
        (S3Exception) S3Exception.builder().statusCode(500).message("Server error").build();
    given(s3Client.getObject(any(GetObjectRequest.class))).willThrow(exception);

    assertThatThrownBy(() -> source.get("ft-confirmations/a.pdf"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void lastModified_returnsInstantFromHeadObject() {
    Instant modified = Instant.parse("2026-06-09T08:00:00Z");
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willReturn(HeadObjectResponse.builder().lastModified(modified).build());

    Optional<Instant> result = source.lastModified("ft-confirmations/a.pdf");

    assertThat(result).contains(modified);
  }

  @Test
  void lastModified_returnsEmptyWhenKeyNotFound() {
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willThrow(NoSuchKeyException.builder().message("Not found").build());

    Optional<Instant> result = source.lastModified("ft-confirmations/missing.pdf");

    assertThat(result).isEmpty();
  }
}
