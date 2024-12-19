package ee.tuleva.onboarding.administration


import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import spock.lang.Specification

import java.time.LocalDate

class PortfolioAnalyticsSourceSpec extends Specification {

  S3Client mockS3Client = Mock(S3Client)
  PortfolioAnalyticsSource service = new PortfolioAnalyticsSource(mockS3Client)

  def "successfully fetch CSV from S3"() {
    setup:
    def date = LocalDate.now()
    def key = "portfolio/${date}.csv"
    byte[] content = "header1,header2\nvalue1,value2".bytes

    mockS3Client.getObject(_) >> { GetObjectRequest request ->
      assert request.bucket() == "analytics-administration-data"
      assert request.key() == key
      return new ResponseInputStream<>(Mock(GetObjectResponse), new ByteArrayInputStream(content))
    }

    when:
    def result = service.fetchCsv(date)

    then:
    assert result.isPresent()
    result.get().text == "header1,header2\nvalue1,value2"
  }

  def "handle S3 object not found"() {
    setup:
    def date = LocalDate.now()
    def key = "portfolio/${date}.csv"

    mockS3Client.getObject(_) >> {
      throw S3Exception.builder()
          .statusCode(404)
          .message("Not Found")
          .build()
    }

    when:
    def result = service.fetchCsv(date)

    then:
    assert !result.isPresent()
  }

  def "handle S3 unauthorized"() {
    setup:
    def date = LocalDate.now()
    def key = "portfolio/${date}.csv"

    mockS3Client.getObject(_) >> {
      throw S3Exception.builder()
          .statusCode(403)
          .message("Unauthorized")
          .build()
    }

    when:
    def result = service.fetchCsv(date)

    then:
    assert !result.isPresent()
  }

  def "handle S3 generic exception"() {
    setup:
    def date = LocalDate.now()
    def key = "portfolio/${date}.csv"

    mockS3Client.getObject(_) >> {
      throw new RuntimeException("Error")
    }

    when:
    service.fetchCsv(date)

    then:
    thrown(RuntimeException)
  }
}
