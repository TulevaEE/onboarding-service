package ee.tuleva.onboarding.notification.email.mailchimp

import ee.tuleva.onboarding.event.EventLog
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.mandate.email.persistence.Email
import ee.tuleva.onboarding.mandate.email.persistence.EmailRepository
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SENT
import static ee.tuleva.onboarding.notification.email.mailchimp.MailchimpFixture.email

class MailchimpCampaignMetricsServiceSpec extends Specification {

  EmailRepository emailRepository = Mock()
  EventLogRepository eventLogRepository = Mock()

  MailchimpCampaignMetricsService service

  def setup() {
    service = new MailchimpCampaignMetricsService(emailRepository, eventLogRepository)
  }

  def "getMetrics_calculatesMetricsCorrectlyForCampaignWithActivity"() {
    given:
    final campaignName = "Test Campaign camp_123"
    final email1 = email("39001010000", "msg_1", campaignName)
    final email2 = email("39001020000", "msg_2", campaignName)
    final email3 = email("39001030000", "msg_3", campaignName)

    final openEvent1 = buildEventLog("OPEN", "39001010000", campaignName)
    final openEvent2 = buildEventLog("OPEN", "39001020000", campaignName)
    final clickEvent = buildEventLog("CLICK", "39001010000", campaignName)
    final unsubEvent = buildEventLog("UNSUBSCRIBE", "39001030000", campaignName)

    emailRepository.findAll() >> [email1, email2, email3]
    eventLogRepository.findAll() >> [openEvent1, openEvent2, openEvent1, clickEvent, unsubEvent]

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.campaignId() == "123"
    result.campaignName() == "Test Campaign camp"
    result.totalSent() == 3
    result.uniqueOpens() == 2
    Math.abs(result.openRate() - 66.67) < 0.01
    result.uniqueClicks() == 1
    Math.abs(result.clickRate() - 33.33) < 0.01
    result.unsubscribes() == 1
    Math.abs(result.unsubscribeRate() - 33.33) < 0.01
  }

  def "getMetrics_returnsZeroMetricsWhenNoCampaignFound"() {
    given:
    final campaignName = "Nonexistent Campaign camp_999"
    emailRepository.findAll() >> []
    eventLogRepository.findAll() >> []

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.campaignId() == "999"
    result.campaignName() == "Nonexistent Campaign camp"
    result.totalSent() == 0
    result.uniqueOpens() == 0
    result.openRate() == 0.0
    result.uniqueClicks() == 0
    result.clickRate() == 0.0
    result.unsubscribes() == 0
    result.unsubscribeRate() == 0.0
  }

  def "getMetrics_countsUniqueOpensByPrincipal"() {
    given:
    final campaignName = "Test Campaign camp_123"
    final email1 = email("39001010000", "msg_1", campaignName)

    final openEvent1 = buildEventLog("OPEN", "39001010000", campaignName)
    final openEvent2 = buildEventLog("OPEN", "39001010000", campaignName)
    final openEvent3 = buildEventLog("OPEN", "39001010000", campaignName)

    emailRepository.findAll() >> [email1]
    eventLogRepository.findAll() >> [openEvent1, openEvent2, openEvent3]

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.totalSent() == 1
    result.uniqueOpens() == 1
    result.openRate() == 100.0
  }

  def "getMetrics_filtersEventsByCampaignName"() {
    given:
    final campaignName = "Campaign A camp_123"
    final otherCampaignName = "Campaign B camp_456"

    final email1 = email("39001010000", "msg_1", campaignName)
    final email2 = email("39001020000", "msg_2", otherCampaignName)

    final openEvent1 = buildEventLog("OPEN", "39001010000", campaignName)
    final openEvent2 = buildEventLog("OPEN", "39001020000", otherCampaignName)

    emailRepository.findAll() >> [email1, email2]
    eventLogRepository.findAll() >> [openEvent1, openEvent2]

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.totalSent() == 1
    result.uniqueOpens() == 1
  }

  def "getMetrics_filtersEmailsByType"() {
    given:
    final campaignName = "Test Campaign camp_123"
    final mailchimpEmail = email("39001010000", "msg_1", campaignName)
    final otherEmail = Email.builder()
        .personalCode("39001020000")
        .type(ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_MANDATE)
        .status(SENT)
        .mandrillMessageId("msg_2")
        .build()

    emailRepository.findAll() >> [mailchimpEmail, otherEmail]
    eventLogRepository.findAll() >> []

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.totalSent() == 1
  }

  def "getMetrics_calculatesRatesCorrectly"() {
    given:
    final campaignName = "Test Campaign camp_123"
    final emails = (1..10).collect { email("3900101000${it}", "msg_${it}", campaignName) }
    final openEvents = (1..5).collect { buildEventLog("OPEN", "3900101000${it}", campaignName) }

    emailRepository.findAll() >> emails
    eventLogRepository.findAll() >> openEvents

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.totalSent() == 10
    result.uniqueOpens() == 5
    result.openRate() == 50.0
  }

  def "getMetrics_extractsCampaignIdFromStandardFormat"() {
    given:
    final campaignName = "Monthly Newsletter 2024 camp_abc123"
    final email1 = email("39001010000", "msg_1", campaignName)

    emailRepository.findAll() >> [email1]
    eventLogRepository.findAll() >> []

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.campaignId() == "abc123"
    result.campaignName() == "Monthly Newsletter 2024 camp"
  }

  def "getMetrics_handlesCampaignNameWithoutUnderscore"() {
    given:
    final campaignName = "SimpleCampaign"
    final email1 = email("39001010000", "msg_1", campaignName)

    emailRepository.findAll() >> [email1]
    eventLogRepository.findAll() >> []

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.campaignId() == "SimpleCampaign"
    result.campaignName() == "SimpleCampaign"
  }

  def "getMetrics_handlesCampaignNameWithMultipleUnderscores"() {
    given:
    final campaignName = "Campaign_With_Multiple_Underscores_camp_123"
    final email1 = email("39001010000", "msg_1", campaignName)

    emailRepository.findAll() >> [email1]
    eventLogRepository.findAll() >> []

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.campaignId() == "123"
    result.campaignName() == "Campaign_With_Multiple_Underscores_camp"
  }

  def "getMetrics_ignoresEventsWithoutMailchimpCampaignData"() {
    given:
    final campaignName = "Test Campaign camp_123"
    final email1 = email("39001010000", "msg_1", campaignName)

    final validEvent = buildEventLog("OPEN", "39001010000", campaignName)
    final invalidEvent = EventLog.builder()
        .type("OPEN")
        .principal("39001020000")
        .data(Map.of("someOtherField", "value"))
        .build()

    emailRepository.findAll() >> [email1]
    eventLogRepository.findAll() >> [validEvent, invalidEvent]

    when:
    final result = service.getMetrics(campaignName)

    then:
    result.uniqueOpens() == 1
  }

  private EventLog buildEventLog(String type, String principal, String campaignName) {
    EventLog.builder()
        .type(type)
        .principal(principal)
        .data(Map.of("mailchimpCampaign", campaignName))
        .build()
  }
}
