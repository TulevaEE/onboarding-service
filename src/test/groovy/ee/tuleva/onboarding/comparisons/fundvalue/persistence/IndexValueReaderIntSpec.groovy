package ee.tuleva.onboarding.comparisons.fundvalue.persistence

import ee.tuleva.onboarding.time.ClockConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import spock.lang.Specification

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue
import static java.time.LocalDate.parse

@DataJpaTest
@Import([JdbcFundValueRepository, IndexValueReader, ClockConfig])
class IndexValueReaderIntSpec extends Specification {

  @Autowired
  JdbcFundValueRepository fundValueRepository

  @Autowired
  IndexValueReader indexValueReader

  def "it finds the latest value of each requested key, ordered by key"() {
    given:
    def keyB = "LATEST_B_" + UUID.randomUUID()
    def keyA = "LATEST_A_" + UUID.randomUUID()
    def notRequested = "LATEST_C_" + UUID.randomUUID()
    fundValueRepository.saveAll([
        aFundValue(keyB, parse("2020-01-01"), 10.12345),
        aFundValue(keyB, parse("2020-01-02"), 11.12345),
        aFundValue(keyA, parse("2020-01-05"), 20.12345),
        aFundValue(notRequested, parse("2020-01-09"), 30.12345),
    ])

    when:
    def result = indexValueReader.findLatestValuesByKeys([keyB, keyA])

    then:
    result == [
        aFundValue(keyA, parse("2020-01-05"), 20.12345),
        aFundValue(keyB, parse("2020-01-02"), 11.12345),
    ]
  }

  def "it finds the values between two dates for several keys, ordered by key and then date"() {
    given:
    def keyB = "RANGE_B_" + UUID.randomUUID()
    def keyA = "RANGE_A_" + UUID.randomUUID()
    saveRangeFixture(keyA, keyB)

    when:
    def result = indexValueReader.findValuesBetweenDatesForKeys(
        [keyB, keyA], parse("2020-01-01"), parse("2020-01-03"), 10)

    then:
    result == [
        aFundValue(keyA, parse("2020-01-01"), 20.12345),
        aFundValue(keyB, parse("2020-01-01"), 10.12345),
        aFundValue(keyB, parse("2020-01-02"), 11.12345),
    ]
  }

  def "it counts the values between two dates for several keys"() {
    given:
    def keyB = "COUNT_B_" + UUID.randomUUID()
    def keyA = "COUNT_A_" + UUID.randomUUID()
    saveRangeFixture(keyA, keyB)

    expect:
    indexValueReader.countValuesBetweenDatesForKeys(
        [keyB, keyA], parse("2020-01-01"), parse("2020-01-03")) == 3
  }

  def "it reads no more rows between two dates than the limit it is given"() {
    given:
    def keyB = "LIMIT_B_" + UUID.randomUUID()
    def keyA = "LIMIT_A_" + UUID.randomUUID()
    saveRangeFixture(keyA, keyB)

    when:
    def result = indexValueReader.findValuesBetweenDatesForKeys(
        [keyB, keyA], parse("2020-01-01"), parse("2020-01-03"), 2)

    then:
    result == [
        aFundValue(keyA, parse("2020-01-01"), 20.12345),
        aFundValue(keyB, parse("2020-01-01"), 10.12345),
    ]
  }

  private void saveRangeFixture(String keyA, String keyB) {
    fundValueRepository.saveAll([
        aFundValue(keyB, parse("2020-01-02"), 11.12345),
        aFundValue(keyB, parse("2020-01-01"), 10.12345),
        aFundValue(keyA, parse("2020-01-01"), 20.12345),
        aFundValue(keyA, parse("2020-01-04"), 21.12345),
    ])
  }
}
