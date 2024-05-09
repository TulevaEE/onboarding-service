package ee.tuleva.onboarding.mandate


import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException
import spock.lang.Specification

import static ee.tuleva.onboarding.account.AccountStatementFixture.activeExternal3rdPillarFundBalance
import static ee.tuleva.onboarding.account.AccountStatementFixture.activeTuleva2ndPillarFundBalance
import static ee.tuleva.onboarding.account.AccountStatementFixture.activeTuleva3rdPillarFund
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund
import static ee.tuleva.onboarding.mandate.MandateFixture.*

class MandateValidatorSpec extends Specification {

  AccountStatementService accountStatementService = Mock()
  MandateValidator mandateValidator = new MandateValidator(accountStatementService)

  def "invalid CreateMandateCommand fails"() {
    given:
    CreateMandateCommand createMandateCmd = invalidCreateMandateCommand()
    when:
    mandateValidator.validate(createMandateCmd, samplePerson())
    then:
    InvalidMandateException exception = thrown()
    exception.errorsResponse.errors.first().code == "invalid.mandate.source.amount.exceeded"
  }

  def "same source and target fund fails"() {
    given:
    CreateMandateCommand createMandateCmd = invalidCreateMandateCommandWithSameSourceAndTargetFund
    when:
    mandateValidator.validate(createMandateCmd, samplePerson())
    then:
    InvalidMandateException exception = thrown()
    exception.errorsResponse.errors.first().code == "invalid.mandate.same.source.and.target.transfer.present"
  }

  def "same 2nd pillar future contribution fund fails"() {
    given:
    Person person = samplePerson()
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand().tap {
      futureContributionFundIsin = tuleva2ndPillarStockFund().isin
    }
    accountStatementService.getAccountStatement(person) >> activeTuleva2ndPillarFundBalance

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    InvalidMandateException exception = thrown()
    exception.errorsResponse.errors.first().code == "invalid.mandate.future.contributions.to.same.fund"
  }

  def "matching 3rd pillar future contribution fund does not fail"() {
    given:
    Person person = samplePerson()
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand().tap {
      futureContributionFundIsin = tuleva3rdPillarFund().isin
    }
    accountStatementService.getAccountStatement(person) >> activeTuleva3rdPillarFund + activeExternal3rdPillarFundBalance

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    noExceptionThrown()
  }

  def "validates"() {
    given:
    Person person = samplePerson()
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand()
    accountStatementService.getAccountStatement(person) >> []

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    noExceptionThrown()
  }

}
