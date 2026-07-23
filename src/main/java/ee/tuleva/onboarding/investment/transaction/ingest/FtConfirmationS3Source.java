package ee.tuleva.onboarding.investment.transaction.ingest;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@NullMarked
@Slf4j
@Component
@RequiredArgsConstructor
class FtConfirmationS3Source {

  private static final String BUCKET = "tuleva-investment-reports";
  private static final String PREFIX = "ft-confirmations/";

  private final S3Client s3Client;

  List<String> list() {
    ListObjectsV2Request request =
        ListObjectsV2Request.builder().bucket(BUCKET).prefix(PREFIX).build();
    List<String> keys =
        s3Client.listObjectsV2(request).contents().stream()
            .map(S3Object::key)
            .filter(key -> !key.equals(PREFIX))
            .toList();
    log.info(
        "Listed FT confirmation objects: bucket={}, prefix={}, count={}, keys={}",
        BUCKET,
        PREFIX,
        keys.size(),
        keys);
    return keys;
  }

  Optional<byte[]> get(String key) {
    try {
      log.info("Fetching FT confirmation PDF: bucket={}, key={}", BUCKET, key);
      GetObjectRequest request = GetObjectRequest.builder().bucket(BUCKET).key(key).build();
      ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
      return Optional.of(response.readAllBytes());
    } catch (NoSuchKeyException e) {
      log.info("FT confirmation PDF not found: bucket={}, key={}", BUCKET, key);
      return Optional.empty();
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        log.info("FT confirmation PDF not found: bucket={}, key={}", BUCKET, key);
        return Optional.empty();
      } else if (e.statusCode() == 403) {
        log.error("Access denied to FT confirmation PDF: bucket={}, key={}", BUCKET, key, e);
        return Optional.empty();
      }
      throw new RuntimeException(
          "S3 error fetching FT confirmation PDF: bucket=" + BUCKET + ", key=" + key, e);
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to read FT confirmation PDF bytes: bucket=" + BUCKET + ", key=" + key, e);
    }
  }

  Optional<Instant> lastModified(String key) {
    try {
      HeadObjectRequest request = HeadObjectRequest.builder().bucket(BUCKET).key(key).build();
      return Optional.ofNullable(s3Client.headObject(request).lastModified());
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    } catch (S3Exception e) {
      if (e.statusCode() == 404 || e.statusCode() == 403) {
        return Optional.empty();
      }
      throw new RuntimeException(
          "S3 error checking last modified for FT confirmation PDF: bucket="
              + BUCKET
              + ", key="
              + key,
          e);
    }
  }
}
