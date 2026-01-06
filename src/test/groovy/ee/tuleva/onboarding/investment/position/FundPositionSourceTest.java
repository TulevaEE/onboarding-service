package ee.tuleva.onboarding.investment.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class FundPositionSourceTest {

  @Mock private S3Client s3Client;

  @InjectMocks private FundPositionSource source;

  @Test
  void fetch_returnsInputStream_whenFileExists() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    @SuppressWarnings("unchecked")
    ResponseInputStream<GetObjectResponse> mockResponse = mock(ResponseInputStream.class);
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(mockResponse);

    Optional<InputStream> result = source.fetch(date);

    assertThat(result).isPresent();
  }

  @Test
  void fetch_returnsEmpty_whenNoSuchKeyException() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("Not found").build());

    Optional<InputStream> result = source.fetch(date);

    assertThat(result).isEmpty();
  }

  @Test
  void fetch_returnsEmpty_whenS3ExceptionWith404() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    S3Exception exception =
        (S3Exception) S3Exception.builder().statusCode(404).message("Not found").build();
    when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(exception);

    Optional<InputStream> result = source.fetch(date);

    assertThat(result).isEmpty();
  }

  @Test
  void fetch_returnsEmpty_whenS3ExceptionWith403() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    S3Exception exception =
        (S3Exception) S3Exception.builder().statusCode(403).message("Access denied").build();
    when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(exception);

    Optional<InputStream> result = source.fetch(date);

    assertThat(result).isEmpty();
  }

  @Test
  void fetch_throwsRuntimeException_whenS3ExceptionWithOtherStatus() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    S3Exception exception =
        (S3Exception) S3Exception.builder().statusCode(500).message("Server error").build();
    when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(exception);

    assertThatThrownBy(() -> source.fetch(date))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("S3 error fetching fund position file");
  }
}
