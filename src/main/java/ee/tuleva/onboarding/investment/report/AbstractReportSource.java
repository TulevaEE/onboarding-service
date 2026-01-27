package ee.tuleva.onboarding.investment.report;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractReportSource implements ReportSource {

  private static final String BUCKET = "tuleva-investment-reports";

  private final S3Client s3Client;

  @Override
  public Optional<InputStream> fetch(ReportType reportType, LocalDate date) {
    String key = getKey(reportType, date);

    try {
      log.info("Fetching report file: provider={}, bucket={}, key={}", getProvider(), BUCKET, key);

      GetObjectRequest request = GetObjectRequest.builder().bucket(BUCKET).key(key).build();

      ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
      return Optional.of(response);

    } catch (NoSuchKeyException e) {
      log.info("Report file not found: provider={}, bucket={}, key={}", getProvider(), BUCKET, key);
      return Optional.empty();

    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        log.info(
            "Report file not found: provider={}, bucket={}, key={}", getProvider(), BUCKET, key);
        return Optional.empty();
      } else if (e.statusCode() == 403) {
        log.error(
            "Access denied to report file: provider={}, bucket={}, key={}",
            getProvider(),
            BUCKET,
            key,
            e);
        return Optional.empty();
      }
      throw new RuntimeException(
          "S3 error fetching report file: provider="
              + getProvider()
              + ", bucket="
              + BUCKET
              + ", key="
              + key,
          e);
    }
  }

  @Override
  public String getBucket() {
    return BUCKET;
  }
}
