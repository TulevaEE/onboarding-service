package ee.tuleva.onboarding.comparisons.returns

import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND
import static ee.tuleva.onboarding.currency.Currency.EUR

class ReturnsSpec extends Specification {
  def "calculates from date of a single return"() {
    given:
    def fromDate = LocalDate.parse("2010-02-03")
    def toDate = LocalDate.parse("2025-04-05")
    def aReturn = new Return("isin", 0.01, 123.45, 234.56, EUR, FUND, fromDate, toDate)
    def returns = new Returns([aReturn])
    when:
    LocalDate from = returns.getFrom()
    then:
    from == fromDate
  }

  def "calculates from date of multiple returns"() {
    given:
    def fromDate = LocalDate.parse("2010-02-03")
    def toDate = LocalDate.parse("2025-04-05")

    def aReturn1 = new Return("isin1", 0.01, 123.45, 234.56, EUR, FUND, fromDate, toDate)
    def aReturn2 = new Return("isin2", 0.02, 678.90, 789.12, EUR, FUND, fromDate, toDate)
    def returns = new Returns([aReturn1, aReturn2])
    when:
    LocalDate from = returns.getFrom()
    then:
    from == fromDate
  }

  def "throws exception on different from dates"() {
    given:
    def fromDate1 = LocalDate.parse("2010-02-03")
    def fromDate2 = LocalDate.parse("2011-03-04")
    def toDate = LocalDate.parse("2025-04-05")
    def aReturn1 = new Return("isin1", 0.01, 123.45, 234.56, EUR, FUND, fromDate1, toDate)
    def aReturn2 = new Return("isin2", 0.02, 678.90, 789.12, EUR, FUND, fromDate2, toDate)
    def returns = new Returns([aReturn1, aReturn2])
    when:
    returns.getFrom()
    then:
    thrown(IllegalStateException)
  }

  def "throws exception on different to dates"() {
    given:
    def fromDate = LocalDate.parse("2010-02-03")
    def toDate1 = LocalDate.parse("2025-04-05")
    def toDate2 = LocalDate.parse("2026-05-06")
    def aReturn1 = new Return("isin1", 0.01, 123.45, 234.56, EUR, FUND, fromDate, toDate1)
    def aReturn2 = new Return("isin2", 0.02, 678.90, 789.12, EUR, FUND, fromDate, toDate2)
    def returns = new Returns([aReturn1, aReturn2])
    when:
    returns.getTo()
    then:
    thrown(IllegalStateException)
  }
}
