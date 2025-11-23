package ee.tuleva.onboarding.notification.email.mailchimp

import ee.tuleva.onboarding.event.EventLog
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.mandate.email.persistence.Email
import ee.tuleva.onboarding.mandate.email.persistence.EmailRepository
import ee.tuleva.onboarding.notification.email.provider.MailchimpService
import ee.tuleva.onboarding.user.UserRepository
import io.github.erkoristhein.mailchimp.marketing.model.CampaignReport
import spock.lang.Specification

import java.time.OffsetDateTime

import static ee.tuleva.onboarding.auth.UserFixture.simpleUser
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SENT
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.MAILCHIMP_CAMPAIGN
import static ee.tuleva.onboarding.notification.email.mailchimp.MailchimpFixture.*

class MailchimpCampaignSyncServiceSpec extends Specification {

  MailchimpService mailchimpService = Mock()
  EmailRepository emailRepository = Mock()
  EventLogRepository eventLogRepository = Mock()
  UserRepository userRepository = Mock()
  CrmMailchimpRepository crmMailchimpRepository = Mock()
  MailchimpCampaignMetricsService metricsService = Mock()

  MailchimpCampaignSyncService service

  def setup() {
    service = new MailchimpCampaignSyncService(
        mailchimpService,
        emailRepository,
        eventLogRepository,
        userRepository,
        crmMailchimpRepository,
        metricsService
    )
  }

  def "syncLatestCampaign_syncsNewCampaignSuccessfully"() {
    given:
    def testCampaign = campaign("camp_123", "Test Campaign")
    def recipient1 = recipient("test1@example.com", "msg_1")
    def recipient2 = recipient("test2@example.com", "msg_2")
    def user1 = simpleUser().personalCode("39001010000").email("test1@example.com").build()
    def user2 = simpleUser().personalCode("39001020000").email("test2@example.com").build()
    def activity = openActivity("msg_1")

    mailchimpService.getLatestSentCampaign() >> testCampaign
    emailRepository.existsByMailchimpCampaign("Test Campaign camp_123") >> false
    mailchimpService.processCampaignRecipients("camp_123", _) >> { String campaignId, callback ->
      callback.accept([recipient1, recipient2])
    }
    userRepository.findByEmail("test1@example.com") >> Optional.of(user1)
    userRepository.findByEmail("test2@example.com") >> Optional.of(user2)
    crmMailchimpRepository.findPersonalCodeByEmail(_) >> Optional.empty()
    mailchimpService.processCampaignActivity("camp_123", _) >> { String campaignId, callback ->
      callback.accept([activity])
    }
    emailRepository.findByMandrillMessageId("msg_1") >> Optional.of(
        email("39001010000", "msg_1", "Test Campaign camp_123")
    )
    mailchimpService.getCampaignReport("camp_123") >> Mock(io.github.erkoristhein.mailchimp.marketing.model.CampaignReport)
    metricsService.getMetrics("Test Campaign camp_123") >> Mock(MailchimpCampaignMetrics)

    when:
    service.syncLatestCampaign()

    then:
    1 * emailRepository.saveAll({ List<Email> emails ->
      emails.size() == 2 &&
          emails[0].personalCode == "39001010000" &&
          emails[1].personalCode == "39001020000" &&
          emails[0].type == MAILCHIMP_CAMPAIGN &&
          emails[0].status == SENT
    })
    1 * eventLogRepository.saveAll({ List<EventLog> logs ->
      logs.size() == 1 &&
          logs[0].type == "OPEN" &&
          logs[0].principal == "39001010000" &&
          logs[0].data.mandrillMessageId == "msg_1" &&
          logs[0].data.emailType == "MAILCHIMP_CAMPAIGN" &&
          logs[0].data.mailchimpCampaign == "Test Campaign camp_123"
    })
  }

  def "syncLatestCampaign_skipsAlreadySyncedCampaign"() {
    given:
    def testCampaign = campaign("camp_123", "Test Campaign")
    mailchimpService.getLatestSentCampaign() >> testCampaign
    emailRepository.existsByMailchimpCampaign("Test Campaign camp_123") >> true

    when:
    service.syncLatestCampaign()

    then:
    0 * mailchimpService.processCampaignRecipients(_, _)
    0 * mailchimpService.processCampaignActivity(_, _)
    0 * emailRepository.saveAll(_)
  }

  def "syncLatestCampaign_handlesMissingCampaign"() {
    given:
    mailchimpService.getLatestSentCampaign() >> null

    when:
    service.syncLatestCampaign()

    then:
    0 * emailRepository.existsByMailchimpCampaign(_)
    0 * mailchimpService.processCampaignRecipients(_, _)
  }

  def "syncLatestCampaign_skipsRecipientsWithoutPersonalCode"() {
    given:
    def campaign = campaign("camp_123", "Test Campaign", OffsetDateTime.now())
    def recipientWithUser = recipient("user@example.com", "msg_1")
    def recipientWithoutUser = recipient("unknown@example.com", "msg_2")
    def user = simpleUser().personalCode("39001010000").email("user@example.com").build()

    mailchimpService.getLatestSentCampaign() >> campaign
    emailRepository.existsByMailchimpCampaign("Test Campaign camp_123") >> false
    mailchimpService.processCampaignRecipients("camp_123", _) >> { String campaignId, callback ->
      callback.accept([recipientWithUser, recipientWithoutUser])
    }
    userRepository.findByEmail("user@example.com") >> Optional.of(user)
    userRepository.findByEmail("unknown@example.com") >> Optional.empty()
    crmMailchimpRepository.findPersonalCodeByEmail("user@example.com") >> Optional.empty()
    crmMailchimpRepository.findPersonalCodeByEmail("unknown@example.com") >> Optional.empty()
    mailchimpService.processCampaignActivity("camp_123", _) >> { String campaignId, callback ->
      callback.accept([])
    }
    mailchimpService.getCampaignReport("camp_123") >> Mock(CampaignReport)
    metricsService.getMetrics("Test Campaign camp_123") >> Mock(MailchimpCampaignMetrics)

    when:
    service.syncLatestCampaign()

    then:
    1 * emailRepository.saveAll({ List<Email> emails ->
      emails.size() == 1 &&
          emails[0].personalCode == "39001010000" &&
          emails[0].mandrillMessageId == "msg_1"
    })
  }

  def "syncLatestCampaign_skipsActivityForEmailsWithoutPersonalCode"() {
    given:
    def campaign = campaign("camp_123", "Test Campaign", OffsetDateTime.now())
    def recipient1 = recipient("test1@example.com", "msg_1")
    def recipient2 = recipient("test2@example.com", "msg_2")
    def user1 = simpleUser().personalCode("39001010000").email("test1@example.com").build()
    def user2 = simpleUser().personalCode("39001020000").email("test2@example.com").build()
    def activity1 = openActivity("msg_1")
    def activity2 = openActivity("msg_2")

    mailchimpService.getLatestSentCampaign() >> campaign
    emailRepository.existsByMailchimpCampaign("Test Campaign camp_123") >> false
    mailchimpService.processCampaignRecipients("camp_123", _) >> { String campaignId, callback ->
      callback.accept([recipient1, recipient2])
    }
    userRepository.findByEmail("test1@example.com") >> Optional.of(user1)
    userRepository.findByEmail("test2@example.com") >> Optional.of(user2)
    crmMailchimpRepository.findPersonalCodeByEmail(_) >> Optional.empty()
    mailchimpService.processCampaignActivity("camp_123", _) >> { String campaignId, callback ->
      callback.accept([activity1, activity2])
    }
    emailRepository.findByMandrillMessageId("msg_1") >> Optional.of(
        email("39001010000", "msg_1", "Test Campaign camp_123")
    )
    emailRepository.findByMandrillMessageId("msg_2") >> Optional.of(
        emailWithoutPersonalCode("msg_2")
    )
    mailchimpService.getCampaignReport("camp_123") >> Mock(CampaignReport)
    metricsService.getMetrics("Test Campaign camp_123") >> Mock(MailchimpCampaignMetrics)

    when:
    service.syncLatestCampaign()

    then:
    1 * eventLogRepository.saveAll({ List<EventLog> logs ->
      logs.size() == 1 &&
          logs[0].principal == "39001010000" &&
          logs[0].data.mandrillMessageId == "msg_1"
    })
  }

  def "syncLatestCampaign_handlesClickEventsWithUrl"() {
    given:
    def campaign = campaign("camp_123", "Test Campaign", OffsetDateTime.now())
    def recipient = recipient("test@example.com", "msg_1")
    def user = simpleUser().personalCode("39001010000").email("test@example.com").build()
    def clickActivity = clickActivity("msg_1", "https://tuleva.ee")

    mailchimpService.getLatestSentCampaign() >> campaign
    emailRepository.existsByMailchimpCampaign("Test Campaign camp_123") >> false
    mailchimpService.processCampaignRecipients("camp_123", _) >> { String campaignId, callback ->
      callback.accept([recipient])
    }
    userRepository.findByEmail("test@example.com") >> Optional.of(user)
    crmMailchimpRepository.findPersonalCodeByEmail(_) >> Optional.empty()
    mailchimpService.processCampaignActivity("camp_123", _) >> { String campaignId, callback ->
      callback.accept([clickActivity])
    }
    emailRepository.findByMandrillMessageId("msg_1") >> Optional.of(
        email("39001010000", "msg_1", "Test Campaign camp_123")
    )
    mailchimpService.getCampaignReport("camp_123") >> Mock(CampaignReport)
    metricsService.getMetrics("Test Campaign camp_123") >> Mock(MailchimpCampaignMetrics)

    when:
    service.syncLatestCampaign()

    then:
    1 * eventLogRepository.saveAll({ List<EventLog> logs ->
      logs.size() == 1 &&
          logs[0].type == "CLICK" &&
          logs[0].principal == "39001010000" &&
          logs[0].data.path == "https://tuleva.ee"
    })
  }

  def "syncLatestCampaign_handlesUnsubscribeEvents"() {
    given:
    def campaign = campaign("camp_123", "Test Campaign", OffsetDateTime.now())
    def recipient = recipient("test@example.com", "msg_1")
    def user = simpleUser().personalCode("39001010000").email("test@example.com").build()
    def unsubActivity = unsubscribeActivity("msg_1")

    mailchimpService.getLatestSentCampaign() >> campaign
    emailRepository.existsByMailchimpCampaign("Test Campaign camp_123") >> false
    mailchimpService.processCampaignRecipients("camp_123", _) >> { String campaignId, callback ->
      callback.accept([recipient])
    }
    userRepository.findByEmail("test@example.com") >> Optional.of(user)
    crmMailchimpRepository.findPersonalCodeByEmail(_) >> Optional.empty()
    mailchimpService.processCampaignActivity("camp_123", _) >> { String campaignId, callback ->
      callback.accept([unsubActivity])
    }
    emailRepository.findByMandrillMessageId("msg_1") >> Optional.of(
        email("39001010000", "msg_1", "Test Campaign camp_123")
    )
    mailchimpService.getCampaignReport("camp_123") >> Mock(CampaignReport)
    metricsService.getMetrics("Test Campaign camp_123") >> Mock(MailchimpCampaignMetrics)

    when:
    service.syncLatestCampaign()

    then:
    1 * eventLogRepository.saveAll({ List<EventLog> logs ->
      logs.size() == 1 &&
          logs[0].type == "UNSUBSCRIBE"
    })
  }

  def "syncLatestCampaign_ignoresUnsupportedActivityTypes"() {
    given:
    def campaign = campaign("camp_123", "Test Campaign", OffsetDateTime.now())
    def recipient = recipient("test@example.com", "msg_1")
    def user = simpleUser().personalCode("39001010000").email("test@example.com").build()
    def openActivityRecord = openActivity("msg_1")
    def bounceActivityRecord = emailActivity("msg_1", "bounce", null)

    mailchimpService.getLatestSentCampaign() >> campaign
    emailRepository.existsByMailchimpCampaign("Test Campaign camp_123") >> false
    mailchimpService.processCampaignRecipients("camp_123", _) >> { String campaignId, callback ->
      callback.accept([recipient])
    }
    userRepository.findByEmail("test@example.com") >> Optional.of(user)
    crmMailchimpRepository.findPersonalCodeByEmail(_) >> Optional.empty()
    mailchimpService.processCampaignActivity("camp_123", _) >> { String campaignId, callback ->
      callback.accept([openActivityRecord, bounceActivityRecord])
    }
    emailRepository.findByMandrillMessageId("msg_1") >> Optional.of(
        email("39001010000", "msg_1", "Test Campaign camp_123")
    )
    mailchimpService.getCampaignReport("camp_123") >> Mock(CampaignReport)
    metricsService.getMetrics("Test Campaign camp_123") >> Mock(MailchimpCampaignMetrics)

    when:
    service.syncLatestCampaign()

    then:
    1 * eventLogRepository.saveAll({ List<EventLog> logs ->
      logs.size() == 1 &&
          logs[0].type == "OPEN" &&
          logs[0].principal == "39001010000"
    })
  }
}
