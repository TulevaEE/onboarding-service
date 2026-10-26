package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.deadline.PublicHolidays
import ee.tuleva.onboarding.error.NotFoundException
import ee.tuleva.onboarding.mandate.MandateGateway
import ee.tuleva.onboarding.mandate.MandateService
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleRejoinCancellationMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationSnapshotFixture.samplePendingRejoinApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationSnapshotFixture.sampleTransferApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.time.TestClockHolder.now

class ApplicationCancellationServiceSpec extends Specification {

  static final Instant REJOIN_FILING_WINDOW_CLOSES = Instant.parse("2020-03-31T20:59:59.999999999Z")

  MandateGateway mandateGateway = Mock(MandateGateway)
  MandateService mandateService = Mock(MandateService)
  RejoinCancellations rejoinCancellations = Mock(RejoinCancellations)
  def person = authenticatedPersonFromUser(sampleUser().build()).build()

  ApplicationCancellationService applicationCancellationService = cancellationServiceAt(now)

  def "can cancel applications"() {
    given:
    def applicationDTO = sampleTransferApplicationDto()
    def mandate = sampleMandate()

    1 * mandateGateway.getApplications(person) >> [applicationDTO]
    1 * mandateService.saveCancellation(person, applicationDTO) >> mandate

    when:
    ApplicationCancellationResponse response =
      applicationCancellationService.createCancellationMandate(person, applicationDTO.id)

    then:
    response.mandateId == mandate.id
  }

  def "returns first application when multiple applications with same id found"() {
    given:
    def applicationDTO = sampleTransferApplicationDto()
    def mandate = sampleMandate()

    1 * mandateGateway.getApplications(person) >> [applicationDTO, applicationDTO]
    1 * mandateService.saveCancellation(person, applicationDTO) >> mandate

    when:
    ApplicationCancellationResponse response =
      applicationCancellationService.createCancellationMandate(person, applicationDTO.id)

    then:
    response.mandateId == mandate.id
  }

  def "throws NotFoundException when no application matches the given id"() {
    given:
    def applicationDTO = sampleTransferApplicationDto()

    1 * mandateGateway.getApplications(person) >> [applicationDTO]

    when:
    applicationCancellationService.createCancellationMandate(person, 999999L)

    then:
    thrown(NotFoundException)
  }

  def "cancels a transfer that is no longer pending and past its deadline as before, since only a rejoin is checked"() {
    given:
    def transfer = sampleTransferApplicationDto()
    transfer.status = COMPLETE
    def mandate = sampleMandate()
    mandateGateway.getApplications(person) >> [transfer]

    when:
    def response = cancellationServiceAt(REJOIN_FILING_WINDOW_CLOSES.plusSeconds(1))
        .createCancellationMandate(person, transfer.id)

    then:
    1 * mandateService.saveCancellation(person, transfer) >> mandate
    0 * rejoinCancellations._
    response.mandateId == mandate.id
  }

  def "cancels a pending rejoin within its filing window that the person has not canceled yet"() {
    given:
    def rejoin = samplePendingRejoinApplicationDto()
    def mandate = sampleRejoinCancellationMandate()
    mandateGateway.getApplications(person) >> [rejoin]
    rejoinCancellations.isCanceled(person, rejoin) >> false

    when:
    def response = applicationCancellationService.createCancellationMandate(person, rejoin.id)

    then:
    1 * mandateService.saveCancellation(person, rejoin) >> mandate
    response.mandateId == mandate.id
  }

  def "cancels a pending rejoin at the last instant of its filing window"() {
    given:
    def rejoin = samplePendingRejoinApplicationDto()
    def mandate = sampleRejoinCancellationMandate()
    mandateGateway.getApplications(person) >> [rejoin]
    rejoinCancellations.isCanceled(person, rejoin) >> false

    when:
    def response = cancellationServiceAt(REJOIN_FILING_WINDOW_CLOSES)
        .createCancellationMandate(person, rejoin.id)

    then:
    1 * mandateService.saveCancellation(person, rejoin) >> mandate
    response.mandateId == mandate.id
  }

  def "refuses to cancel a rejoin once its filing window has closed, as a later leave application would stop contributions instead"() {
    given:
    def rejoin = samplePendingRejoinApplicationDto()
    mandateGateway.getApplications(person) >> [rejoin]
    rejoinCancellations.isCanceled(person, rejoin) >> false

    when:
    cancellationServiceAt(REJOIN_FILING_WINDOW_CLOSES.plusNanos(1))
        .createCancellationMandate(person, rejoin.id)

    then:
    def refusal = thrown(InvalidMandateException)
    refusal.errorsResponse == InvalidMandateException.rejoinCancellationDeadlinePassed().errorsResponse
    0 * mandateService.saveCancellation(_, _)
  }

  def "refuses to cancel a rejoin that is no longer pending"() {
    given:
    def rejoin = samplePendingRejoinApplicationDto()
    rejoin.status = COMPLETE
    mandateGateway.getApplications(person) >> [rejoin]
    rejoinCancellations.isCanceled(person, rejoin) >> false

    when:
    applicationCancellationService.createCancellationMandate(person, rejoin.id)

    then:
    def refusal = thrown(InvalidMandateException)
    refusal.errorsResponse == InvalidMandateException.rejoinNotPending().errorsResponse
    0 * mandateService.saveCancellation(_, _)
  }

  def "refuses to cancel a rejoin the person has already canceled through us"() {
    given:
    def rejoin = samplePendingRejoinApplicationDto()
    mandateGateway.getApplications(person) >> [rejoin]
    rejoinCancellations.isCanceled(person, rejoin) >> true

    when:
    applicationCancellationService.createCancellationMandate(person, rejoin.id)

    then:
    def refusal = thrown(InvalidMandateException)
    refusal.errorsResponse == InvalidMandateException.rejoinAlreadyCanceled().errorsResponse
    0 * mandateService.saveCancellation(_, _)
  }

  private ApplicationCancellationService cancellationServiceAt(Instant instant) {
    def estonianClock = Clock.fixed(instant, ZoneId.of("Europe/Tallinn"))
    return new ApplicationCancellationService(
        mandateService,
        mandateGateway,
        new MandateDeadlinesService(estonianClock, new PublicHolidays()),
        rejoinCancellations,
        estonianClock)
  }
}
