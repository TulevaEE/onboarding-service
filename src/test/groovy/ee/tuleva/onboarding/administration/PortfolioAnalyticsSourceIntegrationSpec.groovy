package ee.tuleva.onboarding.administration

import spock.lang.Ignore
import spock.lang.Specification

import java.time.LocalDate


@Ignore
class PortfolioAnalyticsSourceIntegrationSpec extends Specification {

  PortfolioAnalyticsSource portfolioAnalyticsSource
  def setup() {
    AmazonS3PortfolioAnalyticsConfiguration clientConfiguration = new AmazonS3PortfolioAnalyticsConfiguration()
    clientConfiguration.accessKey = "ACCESS_KEY"
    clientConfiguration.secretKey = "SECRET_KEY"
    portfolioAnalyticsSource = new PortfolioAnalyticsSource(clientConfiguration.amazonS3Client())
  }

  def "fetch CSV from S3 bucket"() {
    when: "Fetching the CSV file for a specific date"
        Optional<InputStream> csvStream = portfolioAnalyticsSource.fetchCsv(LocalDate.of(2024, 5, 31))
        Optional<InputStream> csvStreamNonExistent = portfolioAnalyticsSource.fetchCsv(LocalDate.of(2023, 5, 31))

    then: "The stream should not be null and should have data"
        csvStreamNonExistent.isEmpty()
        assert !csvStream.isEmpty()
        csvStream.get().available() > 0

    cleanup:
        csvStream.get().close()
  }
}
