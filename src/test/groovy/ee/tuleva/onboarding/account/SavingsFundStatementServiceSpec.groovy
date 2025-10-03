package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.ledger.LedgerService
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.*
import static ee.tuleva.onboarding.ledger.UserAccount.*

class SavingsFundStatementServiceSpec extends Specification {

  UserService userService = Mock()
  LedgerService ledgerService = Mock()
  SavingsFundOnboardingService savingsFundOnboardingService = Mock()

  SavingsFundStatementService service = new SavingsFundStatementService(userService, ledgerService, savingsFundOnboardingService)

  def "returns savings account statement"() {
    given:
    def user = sampleUser().build()

    def fundUnits = fundUnitsAccountWithBalance(2.0)
    def fundUnitsReserved = fundUnitsReservedAccountWithBalance(1.0)
    def subscriptions = subscriptionsAccountWithBalance(3.0)
    def redemptions = redemptionsAccountWithBalance(0.0)

    userService.findByPersonalCode(user.personalCode) >> Optional.of(user)
    savingsFundOnboardingService.isOnboardingCompleted(user) >> true
    ledgerService.getUserAccount(user, FUND_UNITS) >> fundUnits
    ledgerService.getUserAccount(user, FUND_UNITS_RESERVED) >> fundUnitsReserved
    ledgerService.getUserAccount(user, SUBSCRIPTIONS) >> subscriptions
    ledgerService.getUserAccount(user, REDEMPTIONS) >> redemptions


    when:
    FundBalance savingsAccountStatement = service.getAccountStatement(user)

    then:
    savingsAccountStatement.fund == additionalSavingsFund()
    savingsAccountStatement.value == 3
    savingsAccountStatement.units == 3
    savingsAccountStatement.contributions == 3
    savingsAccountStatement.subtractions == 0
  }

  def "throws exception if user is not onboarded"() {
    given:
    def user = sampleUser().build()

    userService.findByPersonalCode(user.personalCode) >> Optional.of(user)
    savingsFundOnboardingService.isOnboardingCompleted(user) >> false

    when:
    service.getAccountStatement(user)

    then:
    thrown(IllegalStateException)
  }
}
