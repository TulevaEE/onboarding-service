package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.Comparator.comparing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
@Component
@RequiredArgsConstructor
class SebFundConfirmationS3Source {

  private static final String BUCKET = "tuleva-investment-reports";
  private static final String PREFIX = "seb-confirmations/";
  private static final String PDF_SUFFIX = ".pdf";
  private static final Duration LOOKBACK = Duration.ofDays(14);

  private final S3Client s3Client;
  private final Clock clock;

  List<String> recentKeys() {
    Instant uploadedAfter = clock.instant().minus(LOOKBACK);
    List<S3Object> objects = new ArrayList<>();
    @Nullable String continuationToken = null;
    do {
      ListObjectsV2Response page = s3Client.listObjectsV2(request(continuationToken));
      page.contents().stream()
          .filter(object -> object.key().endsWith(PDF_SUFFIX))
          .filter(object -> isUploadedAfter(object, uploadedAfter))
          .forEach(objects::add);
      continuationToken =
          Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
    } while (continuationToken != null);
    List<String> keys =
        objects.stream().sorted(comparing(S3Object::lastModified)).map(S3Object::key).toList();
    log.info(
        "Listed SEB fund confirmation PDFs: bucket={}, prefix={}, count={}",
        BUCKET,
        PREFIX,
        keys.size());
    return keys;
  }

  Optional<byte[]> get(String key) {
    try {
      return Optional.of(
          s3Client
              .getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(key).build())
              .asByteArray());
    } catch (NoSuchKeyException e) {
      log.info("SEB fund confirmation PDF not found: bucket={}, key={}", BUCKET, key);
      return Optional.empty();
    }
  }

  static String tradeReference(String key) {
    String fileName = key.substring(PREFIX.length(), key.length() - PDF_SUFFIX.length());
    int sequenceSeparator = fileName.lastIndexOf('-');
    return sequenceSeparator < 0 ? fileName : fileName.substring(0, sequenceSeparator);
  }

  private static ListObjectsV2Request request(@Nullable String continuationToken) {
    ListObjectsV2Request.Builder request =
        ListObjectsV2Request.builder().bucket(BUCKET).prefix(PREFIX);
    if (continuationToken != null) {
      request.continuationToken(continuationToken);
    }
    return request.build();
  }

  private static boolean isUploadedAfter(S3Object object, Instant uploadedAfter) {
    return object.lastModified() != null && object.lastModified().isAfter(uploadedAfter);
  }
}
