package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.PensionAccountStatement.PensionFundBalance
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.command.MandateFundTransferExchangeCommand
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarBondFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.lhv3rdPillarFund
import static ee.tuleva.onboarding.mandate.MandateFixture.*

class MandateValidatorSpec extends Specification {

  PensionAccountStatement pensionAccountStatement = Mock()
  FundRepository fundRepository = Mock()
  MandateValidator mandateValidator = new MandateValidator(pensionAccountStatement, fundRepository)

  def "invalid CreateMandateCommand fails"() {
    given:
    CreateMandateCommand createMandateCmd = invalidCreateMandateCommand()
    when:
    mandateValidator.validate(createMandateCmd, samplePerson())
    then:
    InvalidMandateException exception = thrown()
    exception.errorsResponse.errors.first().code == "invalid.mandate.source.amount.exceeded"
  }

  def "sum of source transfer amounts of exactly one does not fail"() {
    given:
    Person person = samplePerson()
    CreateMandateCommand createMandateCmd = new CreateMandateCommand(
        "fundTransferExchanges": [
            new MandateFundTransferExchangeCommand(
                "amount": 0.5,
                "sourceFundIsin": "SOMEISIN",
                "targetFundIsin": futureContibutionFundIsin
            ),
            new MandateFundTransferExchangeCommand(
                "amount": 0.5,
                "sourceFundIsin": "SOMEISIN",
                "targetFundIsin": futureContibutionFundIsin
            )
        ],
        "futureContributionFundIsin": futureContibutionFundIsin
    )

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    noExceptionThrown()
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
    fundRepository.findByIsin(createMandateCmd.futureContributionFundIsin) >> tuleva2ndPillarStockFund()
    pensionAccountStatement.forPerson(person) >> [
        new PensionFundBalance(tuleva2ndPillarStockFund().isin, BigDecimal.valueOf(123.4567), true),
        new PensionFundBalance(tuleva2ndPillarBondFund().isin, BigDecimal.valueOf(234.5678), false)
    ]

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
    fundRepository.findByIsin(createMandateCmd.futureContributionFundIsin) >> tuleva3rdPillarFund()
    pensionAccountStatement.forPerson(person) >> [
        new PensionFundBalance(tuleva3rdPillarFund().isin, BigDecimal.valueOf(234.56), true),
        new PensionFundBalance(lhv3rdPillarFund().isin, BigDecimal.valueOf(2343.8579), true)
    ]

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    noExceptionThrown()
  }

  def "does not fail when no 3rd pillar open"() {
    given:
    Person person = samplePerson()
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand().tap {
      futureContributionFundIsin = tuleva3rdPillarFund().isin
    }
    fundRepository.findByIsin(createMandateCmd.futureContributionFundIsin) >> tuleva2ndPillarStockFund()

    pensionAccountStatement.forPerson(person) >> [
        new PensionFundBalance(tuleva2ndPillarStockFund().isin, BigDecimal.valueOf(123.4567), true),
        new PensionFundBalance(tuleva2ndPillarBondFund().isin, BigDecimal.valueOf(234.5678), false),
        new PensionFundBalance(tuleva3rdPillarFund().isin, BigDecimal.ZERO, false)
    ]

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    noExceptionThrown()
  }

  def "does not fail when no 2nd pillar open"() {
    given:
    Person person = samplePerson()
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand().tap {
      futureContributionFundIsin = tuleva3rdPillarFund().isin
    }
    fundRepository.findByIsin(createMandateCmd.futureContributionFundIsin) >> tuleva3rdPillarFund()
    pensionAccountStatement.forPerson(person) >> [
        new PensionFundBalance(tuleva3rdPillarFund().isin, BigDecimal.valueOf(234.56), true),
        new PensionFundBalance(tuleva2ndPillarStockFund().isin, BigDecimal.valueOf(234.5678), false)
    ]

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    noExceptionThrown()
  }

  def "works with null isin"() {
    given:
    Person person = samplePerson()
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand().tap {
      futureContributionFundIsin = null
    }

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    noExceptionThrown()
  }

  def "works when no fund found"() {
    given:
    Person person = samplePerson()
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand().tap {
      futureContributionFundIsin = tuleva3rdPillarFund().isin
    }
    fundRepository.findByIsin(createMandateCmd.futureContributionFundIsin) >> null

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    noExceptionThrown()
  }

  def "validates"() {
    given:
    Person person = samplePerson()
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand()
    pensionAccountStatement.forPerson(person) >> []
    fundRepository.findByIsin(createMandateCmd.futureContributionFundIsin) >> tuleva2ndPillarStockFund()

    when:
    mandateValidator.validate(createMandateCmd, person)

    then:
    noExceptionThrown()
  }

}
