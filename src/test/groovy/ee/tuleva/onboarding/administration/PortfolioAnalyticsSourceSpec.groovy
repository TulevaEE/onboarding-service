package ee.tuleva.onboarding.administration

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import spock.lang.Specification

import java.time.LocalDate

class PortfolioAnalyticsSourceSpec extends Specification {

  AmazonS3 mockS3Client = Mock(AmazonS3)
  PortfolioAnalyticsSource service = new PortfolioAnalyticsSource(mockS3Client)

  def "successfully fetch CSV from S3"() {
    setup:
        def date = LocalDate.now()
        def key = "portfolio/${date}.csv"
        byte[] content = "header1,header2\nvalue1,value2".getBytes()
        ByteArrayInputStream actualInputStream = new ByteArrayInputStream(content)
        S3Object mockS3Object = Mock(S3Object)
        mockS3Object.getObjectContent() >> new S3ObjectInputStream(actualInputStream, null)
        mockS3Client.getObject("analytics-administration-data", key) >> mockS3Object

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
        AmazonS3Exception notFoundException = new AmazonS3Exception("Not Found")
        notFoundException.statusCode = 404
        mockS3Client.getObject("analytics-administration-data", key) >> { throw notFoundException }

    when:
        def result = service.fetchCsv(date)

    then:
        assert !result.isPresent()
  }

  def "handle S3 unauthorized"() {
    setup:
        def date = LocalDate.now()
        def key = "portfolio/${date}.csv"
        AmazonS3Exception exception = new AmazonS3Exception("Unauthorized")
        exception.statusCode = 403
        mockS3Client.getObject("analytics-administration-data", key) >> { throw exception }

    when:
        def result = service.fetchCsv(date)

    then:
        assert !result.isPresent()
  }

  def "handle S3 generic exception"() {
    setup:
        def date = LocalDate.now()
        def key = "portfolio/${date}.csv"
        mockS3Client.getObject("analytics-administration-data", key) >> { throw new RuntimeException("Error") }

    when:
        service.fetchCsv(date)

    then:
        thrown(RuntimeException)
  }
}
