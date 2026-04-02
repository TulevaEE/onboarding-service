package ee.tuleva.onboarding.account

import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.fund.TulevaFund
import ee.tuleva.onboarding.ledger.LedgerService
import ee.tuleva.onboarding.party.PartyId
import ee.tuleva.onboarding.savings.fund.SavingsFundConfiguration
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity
import static ee.tuleva.onboarding.fund.FundFixture.additionalSavingsFund
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.*
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON
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
    def person = sampleAuthenticatedPersonAndMember().build()
    def personalCode = person.personalCode
    def savingsFund = additionalSavingsFund()

    savingsFundOnboardingService.isOnboardingCompleted(_ as PartyId) >> true
    navProvider.getDisplayNav(_ as TulevaFund) >> new BigDecimal("1.12345")
    ledgerService.getPartyAccount(personalCode, PERSON, FUND_UNITS) >> fundUnitsAccountWithBalance(2.0)
    ledgerService.getPartyAccount(personalCode, PERSON, FUND_UNITS_RESERVED) >> fundUnitsReservedAccountWithBalance(1.0)
    ledgerService.getPartyAccount(personalCode, PERSON, SUBSCRIPTIONS) >> subscriptionsAccountWithBalance(3.0)
    ledgerService.getPartyAccount(personalCode, PERSON, REDEMPTIONS) >> redemptionsAccountWithBalance(1.0)
    savingsFundConfiguration.getIsin() >> savingsFund.isin
    fundRepository.findByIsin(savingsFund.isin) >> savingsFund

    when:
    FundBalance savingsAccountStatement = service.getAccountStatement(person)

    then:
    savingsAccountStatement.fund == savingsFund
    savingsAccountStatement.units == 2
    savingsAccountStatement.value == 2.25
    savingsAccountStatement.unavailableUnits == 1
    savingsAccountStatement.unavailableValue == 1.12
    savingsAccountStatement.contributions == 3
    savingsAccountStatement.subtractions == -1
  }

  def "returns savings account statement for legal entity"() {
    given:
    def person = sampleAuthenticatedPersonLegalEntity().build()
    def registryCode = person.role.code()
    def savingsFund = additionalSavingsFund()

    savingsFundOnboardingService.isOnboardingCompleted(_ as PartyId) >> true
    navProvider.getDisplayNav(_ as TulevaFund) >> new BigDecimal("1.12345")
    ledgerService.getPartyAccount(registryCode, LEGAL_ENTITY, FUND_UNITS) >> fundUnitsAccountWithBalance(2.0)
    ledgerService.getPartyAccount(registryCode, LEGAL_ENTITY, FUND_UNITS_RESERVED) >> fundUnitsReservedAccountWithBalance(1.0)
    ledgerService.getPartyAccount(registryCode, LEGAL_ENTITY, SUBSCRIPTIONS) >> subscriptionsAccountWithBalance(3.0)
    ledgerService.getPartyAccount(registryCode, LEGAL_ENTITY, REDEMPTIONS) >> redemptionsAccountWithBalance(1.0)
    savingsFundConfiguration.getIsin() >> savingsFund.isin
    fundRepository.findByIsin(savingsFund.isin) >> savingsFund

    when:
    FundBalance savingsAccountStatement = service.getAccountStatement(person)

    then:
    savingsAccountStatement.fund == savingsFund
    savingsAccountStatement.units == 2
    savingsAccountStatement.value == 2.25
  }

  def "throws exception if user is not onboarded"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()

    savingsFundOnboardingService.isOnboardingCompleted(_ as PartyId) >> false

    when:
    service.getAccountStatement(person)

    then:
    thrown(IllegalStateException)
  }
}
