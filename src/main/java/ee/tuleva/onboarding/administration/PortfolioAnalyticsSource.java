package ee.tuleva.onboarding.administration;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioAnalyticsSource {

  private final S3Client s3Client;

  public Optional<InputStream> fetchCsv(LocalDate date) {
    String PORTFOLIO_ANALYTICS_DIRECTORY = "portfolio";
    String key = String.format("%s/%s.csv", PORTFOLIO_ANALYTICS_DIRECTORY, date);
    String PORTFOLIO_ANALYTICS_BUCKET = "analytics-administration-data";

    try {
      log.info("Fetching portfolio analytics: {}", key);

      // Build the GetObjectRequest
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().bucket(PORTFOLIO_ANALYTICS_BUCKET).key(key).build();

      // Fetch the object
      ResponseInputStream<GetObjectResponse> objectResponse = s3Client.getObject(getObjectRequest);
      return Optional.of(objectResponse);

    } catch (NoSuchKeyException e) {
      log.info("Portfolio analytics file not found: {}", key);
      return Optional.empty();

    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return Optional.empty();
      } else if (e.statusCode() == 403) {
        log.error(
            "Could not authenticate to download portfolio analytics: {}", e.awsErrorDetails(), e);
        return Optional.empty();
      } else {
        throw new RuntimeException(
            "S3 error when downloading portfolio analytics occurred: " + e.awsErrorDetails(), e);
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not download portfolio analytics", e);
    }
  }
}
