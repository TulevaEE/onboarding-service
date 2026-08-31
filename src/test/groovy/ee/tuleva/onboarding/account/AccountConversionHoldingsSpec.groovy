package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.conversion.ConversionHolding
import spock.lang.Specification

import static ee.tuleva.onboarding.account.AccountStatementFixture.activeTuleva2ndPillarFundBalance
import static ee.tuleva.onboarding.account.AccountStatementFixture.activeTuleva3rdPillarFundBalance
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class AccountConversionHoldingsSpec extends Specification {

  def accountStatementService = Mock(AccountStatementService)
  def conversionHoldings = new AccountConversionHoldings(accountStatementService)

  def "maps fund balances to conversion holdings, whole record"() {
    given:
    accountStatementService.getAccountStatement(samplePerson) >> activeTuleva2ndPillarFundBalance

    when:
    List<ConversionHolding> result = conversionHoldings.forPerson(samplePerson)

    then:
    result == [
        new ConversionHolding(2, "EE3600109435", true, false, true, 100.0, 123.4567, 0.005),
        new ConversionHolding(2, "EE3600109443", true, false, false, 100.0, 234.5678, 0.005),
    ]
  }

  def "carries own-fund, exit-restricted and total value/units for a 3rd pillar mix, whole record"() {
    given:
    accountStatementService.getAccountStatement(samplePerson) >> activeTuleva3rdPillarFundBalance

    when:
    List<ConversionHolding> result = conversionHoldings.forPerson(samplePerson)

    then:
    result == [
        new ConversionHolding(3, "EE3600001707", true, false, true, 100.0, 234.56, 0.005),
        new ConversionHolding(3, "EE3600109484", false, true, false, 200.0, 345.67, 0.006),
    ]
  }
}
