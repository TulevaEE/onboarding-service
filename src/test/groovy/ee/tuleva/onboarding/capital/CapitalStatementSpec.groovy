package ee.tuleva.onboarding.capital

import spock.lang.Specification

class CapitalStatementSpec extends Specification {
  def "calculates total capital"() {
    given:
    def capitalStatement = CapitalStatementFixture.fixture().build()

    when:
    def total = capitalStatement.total

    then:
    total == (capitalStatement.membershipBonus
        + capitalStatement.capitalPayment
        + capitalStatement.unvestedWorkCompensation
        + capitalStatement.workCompensation
        + capitalStatement.profit)
  }
}
