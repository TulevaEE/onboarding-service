package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.ledger.LedgerService
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider
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
  SavingsFundNavProvider navProvider = Mock()

  SavingsFundStatementService service = new SavingsFundStatementService(userService, ledgerService, savingsFundOnboardingService, navProvider)

  def "returns savings account statement"() {
    given:
    def user = sampleUser().build()

    def fundUnits = fundUnitsAccountWithBalance(2.0)
    def fundUnitsReserved = fundUnitsReservedAccountWithBalance(1.0)
    def subscriptions = subscriptionsAccountWithBalance(3.0)
    def redemptions = redemptionsAccountWithBalance(1.0)

    userService.findByPersonalCode(user.personalCode) >> Optional.of(user)
    savingsFundOnboardingService.isOnboardingCompleted(user) >> true
    navProvider.getCurrentNav() >> new BigDecimal("1.12345")
    ledgerService.getUserAccount(user, FUND_UNITS) >> fundUnits
    ledgerService.getUserAccount(user, FUND_UNITS_RESERVED) >> fundUnitsReserved
    ledgerService.getUserAccount(user, SUBSCRIPTIONS) >> subscriptions
    ledgerService.getUserAccount(user, REDEMPTIONS) >> redemptions

    when:
    FundBalance savingsAccountStatement = service.getAccountStatement(user)

    then:
    savingsAccountStatement.fund == additionalSavingsFund()
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

    userService.findByPersonalCode(user.personalCode) >> Optional.of(user)
    savingsFundOnboardingService.isOnboardingCompleted(user) >> false

    when:
    service.getAccountStatement(user)

    then:
    thrown(IllegalStateException)
  }
}
