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
    FundBalance savingsAccountStatement = service.getAccountStatement(person).get()

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
    FundBalance savingsAccountStatement = service.getAccountStatement(person).get()

    then:
    savingsAccountStatement.fund == savingsFund
    savingsAccountStatement.units == 2
    savingsAccountStatement.value == 2.25
  }

  def "returns balance for a not-onboarded party that has a ledger account"() {
    given:
    def person = sampleAuthenticatedPersonLegalEntity().build()
    def registryCode = person.role.code()
    def savingsFund = additionalSavingsFund()

    savingsFundOnboardingService.isOnboardingCompleted(_ as PartyId) >> false
    navProvider.getDisplayNav(_ as TulevaFund) >> new BigDecimal("1.12345")
    ledgerService.findPartyAccount(registryCode, LEGAL_ENTITY, FUND_UNITS) >> Optional.of(fundUnitsAccountWithBalance(2.0))
    ledgerService.findPartyAccount(registryCode, LEGAL_ENTITY, FUND_UNITS_RESERVED) >> Optional.of(fundUnitsReservedAccountWithBalance(1.0))
    ledgerService.findPartyAccount(registryCode, LEGAL_ENTITY, SUBSCRIPTIONS) >> Optional.of(subscriptionsAccountWithBalance(3.0))
    ledgerService.findPartyAccount(registryCode, LEGAL_ENTITY, REDEMPTIONS) >> Optional.of(redemptionsAccountWithBalance(1.0))
    savingsFundConfiguration.getIsin() >> savingsFund.isin
    fundRepository.findByIsin(savingsFund.isin) >> savingsFund

    when:
    def statement = service.getAccountStatement(person)

    then:
    statement.present
    statement.get().fund == savingsFund
    statement.get().units == 2
    statement.get().value == 2.25
    statement.get().unavailableUnits == 1
    statement.get().contributions == 3
    statement.get().subtractions == -1
    0 * ledgerService.getPartyAccount(_, _, _)
  }

  def "missing secondary accounts default to zero for a not-onboarded party"() {
    given:
    def person = sampleAuthenticatedPersonLegalEntity().build()
    def registryCode = person.role.code()
    def savingsFund = additionalSavingsFund()

    savingsFundOnboardingService.isOnboardingCompleted(_ as PartyId) >> false
    navProvider.getDisplayNav(_ as TulevaFund) >> new BigDecimal("1.12345")
    ledgerService.findPartyAccount(registryCode, LEGAL_ENTITY, FUND_UNITS) >> Optional.of(fundUnitsAccountWithBalance(2.0))
    ledgerService.findPartyAccount(registryCode, LEGAL_ENTITY, FUND_UNITS_RESERVED) >> Optional.empty()
    ledgerService.findPartyAccount(registryCode, LEGAL_ENTITY, SUBSCRIPTIONS) >> Optional.empty()
    ledgerService.findPartyAccount(registryCode, LEGAL_ENTITY, REDEMPTIONS) >> Optional.empty()
    savingsFundConfiguration.getIsin() >> savingsFund.isin
    fundRepository.findByIsin(savingsFund.isin) >> savingsFund

    when:
    def statement = service.getAccountStatement(person)

    then:
    statement.present
    statement.get().units == 2
    statement.get().unavailableUnits == 0
    statement.get().contributions == 0
    statement.get().subtractions == 0
  }

  def "returns empty for a not-onboarded party without a ledger account"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()

    savingsFundOnboardingService.isOnboardingCompleted(_ as PartyId) >> false
    ledgerService.findPartyAccount(person.personalCode, PERSON, FUND_UNITS) >> Optional.empty()

    when:
    def statement = service.getAccountStatement(person)

    then:
    statement.empty
    0 * ledgerService.getPartyAccount(_, _, _)
  }
}
