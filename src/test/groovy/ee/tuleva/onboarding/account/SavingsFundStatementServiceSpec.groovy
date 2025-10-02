package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.fund.FundFixture
import ee.tuleva.onboarding.ledger.LedgerAccount
import ee.tuleva.onboarding.ledger.LedgerService
import ee.tuleva.onboarding.ledger.SavingsFundLedger
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.account.AccountStatementFixture.activeTuleva2ndPillarFundBalance
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT

class SavingsFundStatementServiceSpec extends Specification {

  UserService userService = Mock()
  LedgerService ledgerService = Mock()

  SavingsFundStatementService service = new SavingsFundStatementService(userService, ledgerService)

  def "returns savings account statement"() {
    given:
    def user = sampleUser().build()

    def fundUnits = Mock(LedgerAccount){ getBalance() >> BigDecimal.TWO }
    def fundUnitsReserved = Mock(LedgerAccount){ getBalance() >> BigDecimal.ONE }

    userService.findByPersonalCode(_ as String) >> Optional.of(user)
    ledgerService.getUserAccount(user, SavingsFundLedger.UserAccount.FUND_UNITS, FUND_UNIT) >> fundUnits
    ledgerService.getUserAccount(user, SavingsFundLedger.UserAccount.FUND_UNITS_RESERVED, FUND_UNIT) >> fundUnitsReserved

    when:
    FundBalance savingsAccountStatement = service.getAccountStatement(user)

    then:
    savingsAccountStatement.fund == FundFixture.additionalSavingsFund()
    savingsAccountStatement.value == 3
    savingsAccountStatement.contributions == 3
  }
}
