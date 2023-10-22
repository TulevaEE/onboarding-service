package ee.tuleva.onboarding.comparisons.returns

import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND
import static ee.tuleva.onboarding.currency.Currency.EUR

class ReturnsSpec extends Specification {
  def "calculates from date of a single return"() {
    given:
    def fromDate = LocalDate.of(2010, 02, 03)
    def aReturn = new Return("isin", 0.01, 123.45, EUR, FUND, fromDate)
    def returns = new Returns([aReturn])
    when:
    LocalDate from = returns.getFrom()
    then:
    from == fromDate
  }

  def "calculates from date of multiple returns"() {
    given:
    def fromDate = LocalDate.of(2010, 02, 03)
    def aReturn1 = new Return("isin1", 0.01, 123.45, EUR, FUND, fromDate)
    def aReturn2 = new Return("isin2", 0.02, 678.90, EUR, FUND, fromDate)
    def returns = new Returns([aReturn1, aReturn2])
    when:
    LocalDate from = returns.getFrom()
    then:
    from == fromDate
  }

  def "throws exception on different from dates"() {
    given:
    def fromDate1 = LocalDate.of(2010, 02, 03)
    def fromDate2 = LocalDate.of(2011, 04, 05)
    def aReturn1 = new Return("isin1", 0.01, 123.45, EUR, FUND, fromDate1)
    def aReturn2 = new Return("isin2", 0.02, 678.90, EUR, FUND, fromDate2)
    def returns = new Returns([aReturn1, aReturn2])
    when:
    returns.getFrom()
    then:
    thrown(IllegalStateException)
  }
}
