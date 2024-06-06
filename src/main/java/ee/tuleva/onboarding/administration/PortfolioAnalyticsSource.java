package ee.tuleva.onboarding.administration;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioAnalyticsSource {

  private final AmazonS3 amazonS3Client;

  public Optional<InputStream> fetchCsv(LocalDate date) {
    String PORTFOLIO_ANALYTICS_DIRECTORY = "portfolio";
    String key = String.format("%s/%s.csv", PORTFOLIO_ANALYTICS_DIRECTORY, date);
    try {
      log.info("Fetching portfolio analytics: {}", key);
      String PORTFOLIO_ANALYTICS_BUCKET = "analytics-administration-data";
      S3Object s3Object = amazonS3Client.getObject(PORTFOLIO_ANALYTICS_BUCKET, key);
      InputStream objectContent = s3Object.getObjectContent();
      return Optional.ofNullable(objectContent);
    } catch (AmazonS3Exception e) {
      if (e.getStatusCode() == 404) {
        return Optional.empty();
      } else if (e.getStatusCode() == 403) {
        log.error("Could not authenticate to download portfolio analytics: {}", e.getMessage());
        return Optional.empty();
      } else {
        throw new RuntimeException(
            "S3 error when downloading portfolio analytics occurred: " + e.getMessage(), e);
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not download portfolio analytics: " + e.getMessage(), e);
    }
  }
}
