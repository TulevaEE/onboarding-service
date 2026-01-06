package ee.tuleva.onboarding.investment.position;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundPositionSource {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final String BUCKET = "tuleva-investment-reports";
  private static final String PREFIX = "portfolio/";

  private final S3Client s3Client;

  public Optional<InputStream> fetch(LocalDate date) {
    String key = PREFIX + date.format(DATE_FORMAT) + ".csv";

    try {
      log.info("Fetching fund position file: bucket={}, key={}", BUCKET, key);

      GetObjectRequest request = GetObjectRequest.builder().bucket(BUCKET).key(key).build();

      ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
      return Optional.of(response);

    } catch (NoSuchKeyException e) {
      log.info("Fund position file not found: bucket={}, key={}", BUCKET, key);
      return Optional.empty();

    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        log.info("Fund position file not found: bucket={}, key={}", BUCKET, key);
        return Optional.empty();
      } else if (e.statusCode() == 403) {
        log.error("Access denied to fund position file: bucket={}, key={}", BUCKET, key, e);
        return Optional.empty();
      }
      throw new RuntimeException(
          "S3 error fetching fund position file: bucket=" + BUCKET + ", key=" + key, e);
    }
  }
}
