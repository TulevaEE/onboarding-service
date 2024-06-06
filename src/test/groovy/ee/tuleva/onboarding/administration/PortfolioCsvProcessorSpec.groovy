package ee.tuleva.onboarding.administration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification
import java.time.LocalDate

@SpringBootTest
class PortfolioCsvProcessorSpec extends Specification {

  @Autowired
  private PortfolioAnalyticsRepository repository

  @Autowired
  private PortfolioCsvProcessor portfolioCsvProcessor

  def cleanup() {
    repository.deleteAll()
  }

  def "test creation of new PortfolioAnalytics entry"() {
    given:
        String csvData = "key1;key2\nvalue1;value2"
        LocalDate date = LocalDate.of(2024, 5, 31)
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvData.getBytes())

    when:
        portfolioCsvProcessor.process(date, inputStream)

    then:
        Iterable<PortfolioAnalytics> results = repository.findAll()
        assert !results.empty
        results.any {
          it.date == date && it.content.get(0).get("key1") == "value1" && it.content.get(0).get("key2") == "value2"
        }
  }

  def "test update of existing PortfolioAnalytics entry"() {
    given:
        LocalDate date = LocalDate.of(2024, 5, 31)
        List initialContent = List.of([key1: 'initialValue1', key2: 'initialValue2'])
        repository.save(new PortfolioAnalytics(date: date, content: initialContent))

        String updatedCsvData = "key1;key2\nupdatedValue1;updatedValue2"
        ByteArrayInputStream updatedInputStream = new ByteArrayInputStream(updatedCsvData.getBytes())

    when:
        portfolioCsvProcessor.process(date, updatedInputStream)

    then:
        Iterable<PortfolioAnalytics> results = repository.findAll()
        assert !results.empty
        results.any {
          it.date == date && it.content.get(0).get("key1") == "updatedValue1" && it.content.get(0).get("key2") == "updatedValue2"
        }
  }

  def "test correct parsing of numeric values"() {
    given:
        String csvData = "key1;key2\n123.456;test"
        LocalDate date = LocalDate.of(2024, 5, 31)
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvData.getBytes())

    when:
        portfolioCsvProcessor.process(date, inputStream)

    then:
        Iterable<PortfolioAnalytics> results = repository.findAll()
        assert !results.empty
        results.any {
          it.date == date && (it.content.get(0).get("key1") instanceof Number || it.content.get(0).get("key1") == "123.456") && it.content.get(0).get("key2") == "test"
        }
  }

  def "test to skip empty keys"() {
    given:
        String csvData = "key1;;key3\nvalue1;value2;value3"
        LocalDate date = LocalDate.of(2024, 5, 31)
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvData.getBytes())

    when:
        portfolioCsvProcessor.process(date, inputStream)

    then:
        Iterable<PortfolioAnalytics> results = repository.findAll()
        assert !results.empty
        results.any {
          it.date == date && it.content.get(0).get("key1") == "value1" && it.content.get(0).get("key3") == "value3" &&
              it.content.get(0).size() == 2
        }
  }

  def "test malformed CSV handling"() {
    given:
        String csvData = "key1;key2;key3\nvalue1;value2"
        LocalDate date = LocalDate.of(2024, 5, 31)
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvData.getBytes())

    when:
        portfolioCsvProcessor.process(date, inputStream)

    then:
        Iterable<PortfolioAnalytics> results = repository.findAll()
        assert !results.empty
        results.any {
          it.date == date && it.content.get(0).get("key1") == "value1" && it.content.get(0).get("key2") == "value2" && !it.content.get(0).containsKey("key3")
        }
  }

  def "test empty and missing values"() {
    given:
        String csvData = "key1;key2;key3\nvalue1;;"
        LocalDate date = LocalDate.of(2024, 5, 31)
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvData.getBytes())

    when:
        portfolioCsvProcessor.process(date, inputStream)
    then:
        Iterable<PortfolioAnalytics> results = repository.findAll()
        assert !results.empty
        results.any {
          it.date == date && it.content.get(0).get("key1") == "value1" && it.content.get(0).get("key2") == "" && it.content.get(0).get("key3") == ""
        }
  }

  def "test exception handling for empty CSV input"() {
    given:
        String csvData = ""
        LocalDate date = LocalDate.of(2024, 5, 31)
        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvData.getBytes())

    when:
        def result = portfolioCsvProcessor.process(date, inputStream)

    then:
        result == null
  }
}
