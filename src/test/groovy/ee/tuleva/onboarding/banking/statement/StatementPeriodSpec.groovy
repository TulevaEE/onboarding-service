package ee.tuleva.onboarding.banking.statement

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

class StatementPeriodSpec extends Specification {

  static final StatementPeriod PERIOD =
      new StatementPeriod(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 13))

  @Unroll
  def "a period from 2026-09-11 to 2026-09-13 covers #date: #expected"() {
    expect:
    PERIOD.covers(date) == expected

    where:
    date                      | expected
    LocalDate.of(2026, 9, 10) | false
    LocalDate.of(2026, 9, 11) | true
    LocalDate.of(2026, 9, 12) | true
    LocalDate.of(2026, 9, 13) | true
    LocalDate.of(2026, 9, 14) | false
  }
}
