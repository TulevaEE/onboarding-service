package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.ledger.LedgerService
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.*
import static ee.tuleva.onboarding.ledger.UserAccount.*

class SavingsFundStatementServiceSpec extends Specification {

  LedgerService ledgerService = Mock()
  SavingsFundOnboardingService savingsFundOnboardingService = Mock()
  FundNavProvider navProvider = Mock()
  FundRepository fundRepository = Mock()
  SavingsFundConfiguration savingsFundConfiguration = Mock()

  SavingsFundStatementService service = new SavingsFundStatementService(ledgerService, savingsFundOnboardingService, navProvider, fundRepository, savingsFundConfiguration)

  def "returns savings account statement"() {
    given:
    def user = sampleUser().build()
    def savingsFund = additionalSavingsFund()

    def fundUnits = fundUnitsAccountWithBalance(2.0)
    def fundUnitsReserved = fundUnitsReservedAccountWithBalance(1.0)
    def subscriptions = subscriptionsAccountWithBalance(3.0)
    def redemptions = redemptionsAccountWithBalance(1.0)

    savingsFundOnboardingService.isOnboardingCompleted(user.personalCode) >> true
    navProvider.getDisplayNav(_) >> new BigDecimal("1.12345")
    ledgerService.getPartyAccount(user.personalCode, FUND_UNITS) >> fundUnits
    ledgerService.getPartyAccount(user.personalCode, FUND_UNITS_RESERVED) >> fundUnitsReserved
    ledgerService.getPartyAccount(user.personalCode, SUBSCRIPTIONS) >> subscriptions
    ledgerService.getPartyAccount(user.personalCode, REDEMPTIONS) >> redemptions
    savingsFundConfiguration.getIsin() >> savingsFund.isin
    fundRepository.findByIsin(savingsFund.isin) >> savingsFund

    when:
    FundBalance savingsAccountStatement = service.getAccountStatement(user)

    then:
    savingsAccountStatement.fund == savingsFund
    savingsAccountStatement.units == 2
    savingsAccountStatement.value == 2.25
    savingsAccountStatement.unavailableUnits == 1
    savingsAccountStatement.unavailableValue == 1.12
    savingsAccountStatement.contributions == 3
    savingsAccountStatement.subtractions == -1
  }

  def "throws exception if user is not onboarded"() {
    given:
    def user = sampleUser().build()

    savingsFundOnboardingService.isOnboardingCompleted(user.personalCode) >> false

    when:
    service.getAccountStatement(user)

    then:
    thrown(IllegalStateException)
  }
}
