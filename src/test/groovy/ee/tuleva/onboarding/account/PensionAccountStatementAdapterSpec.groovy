package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.mandate.PensionAccountStatement.PensionFundBalance
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarBondFund

class PensionAccountStatementAdapterSpec extends Specification {

  AccountStatementService accountStatementService = Mock()
  PensionAccountStatementAdapter adapter = new PensionAccountStatementAdapter(accountStatementService)

  def "maps a fund balance to a pension fund balance field-for-field"() {
    given:
    def person = samplePerson()
    def fundBalance = FundBalance.builder()
        .fund(tuleva2ndPillarStockFund())
        .units(123.4567)
        .activeContributions(true)
        .build()
    accountStatementService.getAccountStatement(person) >> [fundBalance]

    when:
    def result = adapter.forPerson(person)

    then:
    result == [new PensionFundBalance(fundBalance.isin, fundBalance.units, fundBalance.activeContributions)]
  }

  def "maps every fund balance in the account statement"() {
    given:
    def person = samplePerson()
    def active = FundBalance.builder()
        .fund(tuleva2ndPillarStockFund())
        .units(123.4567)
        .activeContributions(true)
        .build()
    def inactive = FundBalance.builder()
        .fund(tuleva2ndPillarBondFund())
        .units(234.5678)
        .activeContributions(false)
        .build()
    accountStatementService.getAccountStatement(person) >> [active, inactive]

    when:
    def result = adapter.forPerson(person)

    then:
    result == [
        new PensionFundBalance(active.isin, active.units, active.activeContributions),
        new PensionFundBalance(inactive.isin, inactive.units, inactive.activeContributions)
    ]
  }
}
