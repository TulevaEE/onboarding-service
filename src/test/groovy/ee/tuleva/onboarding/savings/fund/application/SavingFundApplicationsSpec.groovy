package ee.tuleva.onboarding.savings.fund.application

import ee.tuleva.onboarding.company.BoardMembershipService
import ee.tuleva.onboarding.mandate.application.Application
import ee.tuleva.onboarding.party.PartyId
import ee.tuleva.onboarding.savings.PendingRedemption
import ee.tuleva.onboarding.savings.RedemptionQueries
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService
import ee.tuleva.onboarding.savings.SavingFundPayment
import ee.tuleva.onboarding.savings.SavingFundPaymentQueries
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING
import static java.math.BigDecimal.valueOf

class SavingFundApplicationsSpec extends Specification {

  SavingFundDeadlinesService savingFundDeadlinesService = Mock()
  SavingFundPaymentQueries savingFundPaymentQueries = Mock()
  RedemptionQueries savingFundRedemptionQueries = Mock()
  BoardMembershipService boardMembershipService = Mock()

  SavingFundApplications savingFundApplications =
      new SavingFundApplications(savingFundDeadlinesService, savingFundPaymentQueries, savingFundRedemptionQueries, boardMembershipService)

  def "gets saving fund payment applications for authenticated person"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()

    // Create sample UUIDs for the payments
    def payment1Id = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    def payment2Id = UUID.fromString("87654321-4321-4321-4321-cba987654321")

    // Create sample saving fund payments
    def payment1 = Mock(SavingFundPayment) {
      getId() >> payment1Id
      getAmount() >> 100.0
      getCurrency() >> EUR
      getCreatedAt() >> TestClockHolder.now
      getStatus() >> SavingFundPayment.Status.CREATED
    }

    def payment2 = Mock(SavingFundPayment) {
      getId() >> payment2Id
      getAmount() >> 250.0
      getCurrency() >> EUR
      getCreatedAt() >> TestClockHolder.now.minusSeconds(3600)
      getStatus() >> SavingFundPayment.Status.VERIFIED
    }

    savingFundPaymentQueries.getPendingPayments(PartyId.from(authenticatedPerson.getRole())) >> [payment2, payment1]
    savingFundRedemptionQueries.getPendingRedemptions(PartyId.from(authenticatedPerson.getRole())) >> []

    savingFundDeadlinesService.getCancellationDeadline(payment1) >> Instant.parse("2021-03-31T21:00:00.000000000Z")
    savingFundDeadlinesService.getFulfillmentDeadline(payment1) >> Instant.parse("2021-04-20T10:00:00Z")
    savingFundDeadlinesService.getCancellationDeadline(payment2) >> Instant.parse("2021-03-31T21:00:00.000000000Z")
    savingFundDeadlinesService.getFulfillmentDeadline(payment2) >> Instant.parse("2021-04-20T10:00:00Z")

    when:
    def applications = savingFundApplications.getApplications(authenticatedPerson)

    then:
    applications.size() == 2

    with(applications[0] as Application<SavingFundPaymentApplicationDetails>) {
      id == payment2Id.getMostSignificantBits() // Converted from UUID
      status == PENDING // TODO: Map real status when available
      creationTime == TestClockHolder.now.minusSeconds(3600)
      with(details) {
        amount == 250.0
        currency == EUR
        paymentId == payment2Id
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.000000000Z")
        fulfillmentDeadline == Instant.parse("2021-04-20T10:00:00Z")
      }
    }

    with(applications[1] as Application<SavingFundPaymentApplicationDetails>) {
      id == payment1Id.getMostSignificantBits() // Converted from UUID
      status == PENDING // TODO: Map real status when available
      creationTime == TestClockHolder.now
      with(details) {
        amount == 100.0
        currency == EUR
        paymentId == payment1Id
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.000000000Z")
        fulfillmentDeadline == Instant.parse("2021-04-20T10:00:00Z")
      }
    }
  }

  def "gets saving fund redemption applications for authenticated person"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()

    def redemption1Id = UUID.fromString("11111111-1111-1111-1111-111111111111")
    def redemption2Id = UUID.fromString("22222222-2222-2222-2222-222222222222")

    def redemption1 = PendingRedemption.builder()
        .id(redemption1Id)
        .amount(valueOf(150.00))
        .customerIban("EE123456789012345678")
        .requestedAt(TestClockHolder.now)
        .cancellationDeadline(Instant.parse("2021-03-31T21:00:00Z"))
        .fulfillmentDeadline(Instant.parse("2021-04-20T10:00:00Z"))
        .build()

    def redemption2 = PendingRedemption.builder()
        .id(redemption2Id)
        .amount(valueOf(300.50))
        .customerIban("EE987654321098765432")
        .requestedAt(TestClockHolder.now.minusSeconds(7200))
        .cancellationDeadline(Instant.parse("2021-03-31T21:00:00Z"))
        .fulfillmentDeadline(Instant.parse("2021-04-20T10:00:00Z"))
        .build()

    savingFundPaymentQueries.getPendingPayments(PartyId.from(authenticatedPerson.getRole())) >> []
    savingFundRedemptionQueries.getPendingRedemptions(PartyId.from(authenticatedPerson.getRole())) >> [redemption2, redemption1]

    when:
    def applications = savingFundApplications.getApplications(authenticatedPerson)

    then:
    applications.size() == 2

    with(applications[0] as Application<SavingFundWithdrawalApplicationDetails>) {
      id == redemption2Id.getMostSignificantBits()
      status == PENDING
      creationTime == TestClockHolder.now.minusSeconds(7200)
      with(details) {
        id == redemption2Id
        amount == valueOf(300.50)
        currency == EUR
        iban == "EE987654321098765432"
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59Z")
        fulfillmentDeadline == Instant.parse("2021-04-20T10:00:00Z")
      }
    }

    with(applications[1] as Application<SavingFundWithdrawalApplicationDetails>) {
      id == redemption1Id.getMostSignificantBits()
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        id == redemption1Id
        amount == valueOf(150.00)
        currency == EUR
        iban == "EE123456789012345678"
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59Z")
        fulfillmentDeadline == Instant.parse("2021-04-20T10:00:00Z")
      }
    }
  }

  def "loads redemptions for the active party only"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def activeParty = PartyId.from(authenticatedPerson.getRole())

    def personRedemptionId = UUID.randomUUID()
    def personRedemption = PendingRedemption.builder()
        .id(personRedemptionId)
        .amount(valueOf(150.00))
        .customerIban("EE123456789012345678")
        .requestedAt(TestClockHolder.now)
        .cancellationDeadline(Instant.parse("2021-03-31T21:00:00Z"))
        .fulfillmentDeadline(Instant.parse("2021-04-20T10:00:00Z"))
        .build()

    savingFundPaymentQueries.getPendingPayments(activeParty) >> []
    savingFundRedemptionQueries.getPendingRedemptions(activeParty) >> [personRedemption]

    when:
    def applications = savingFundApplications.getApplications(authenticatedPerson)

    then:
    applications.size() == 1
    (applications[0] as Application<SavingFundWithdrawalApplicationDetails>).details.id == personRedemptionId
  }

  def "shows legal-entity redemptions when authenticated under a legal-entity role"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonLegalEntity().build()
    def legalEntityPartyId = PartyId.from(authenticatedPerson.getRole())

    def legalEntityRedemptionId = UUID.randomUUID()
    def legalEntityRedemption = PendingRedemption.builder()
        .id(legalEntityRedemptionId)
        .amount(valueOf(300.00))
        .customerIban("EE382200221020145686")
        .requestedAt(TestClockHolder.now)
        .cancellationDeadline(Instant.parse("2021-03-31T21:00:00Z"))
        .fulfillmentDeadline(Instant.parse("2021-04-20T10:00:00Z"))
        .build()

    boardMembershipService.isBoardMember(authenticatedPerson.getPersonalCode(), legalEntityPartyId.code()) >> true
    savingFundPaymentQueries.getPendingPayments(legalEntityPartyId) >> []
    savingFundRedemptionQueries.getPendingRedemptions(legalEntityPartyId) >> [legalEntityRedemption]

    when:
    def applications = savingFundApplications.getApplications(authenticatedPerson)

    then:
    applications.size() == 1
    (applications[0] as Application<SavingFundWithdrawalApplicationDetails>).details.id == legalEntityRedemptionId
  }

  def "hides legal-entity savings-fund applications when active legal-entity role is no longer a board member"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonLegalEntity().build()
    def legalEntityPartyId = PartyId.from(authenticatedPerson.getRole())

    boardMembershipService.isBoardMember(authenticatedPerson.getPersonalCode(), legalEntityPartyId.code()) >> false

    when:
    def applications = savingFundApplications.getApplications(authenticatedPerson)

    then:
    applications.isEmpty()
    0 * savingFundPaymentQueries.getPendingPayments(_)
    0 * savingFundRedemptionQueries.getPendingRedemptions(_)
  }
}
