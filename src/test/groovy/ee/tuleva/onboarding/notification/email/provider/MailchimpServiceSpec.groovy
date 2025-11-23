package ee.tuleva.onboarding.notification.email.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import ee.tuleva.onboarding.notification.email.auto.EmailEvent
import io.github.erkoristhein.mailchimp.api.MessagesApi
import io.github.erkoristhein.mailchimp.marketing.api.CampaignsApi
import io.github.erkoristhein.mailchimp.marketing.api.ListsApi
import io.github.erkoristhein.mailchimp.marketing.api.ReportsApi
import io.github.erkoristhein.mailchimp.marketing.model.*
import spock.lang.Specification

import java.time.OffsetDateTime

import static ee.tuleva.onboarding.notification.email.mailchimp.MailchimpFixture.*

class MailchimpServiceSpec extends Specification {

  MessagesApi messagesApi = Mock()
  ListsApi listsApi = Mock()
  CampaignsApi campaignsApi = Mock()
  ReportsApi reportsApi = Mock()

  String mailchimpListId = "test_list_id"
  MailchimpService service

  def setup() {
    service = new MailchimpService(messagesApi, listsApi, campaignsApi, reportsApi)
    service.mailchimpListId = mailchimpListId
  }

  def "sendEvent_postsMemberEventToMailchimp"() {
    given:
    def email = "test@example.com"
    def emailEvent = EmailEvent.NEW_LEAVER

    when:
    service.sendEvent(email, emailEvent)

    then:
    1 * listsApi.postListMemberEvents(mailchimpListId, email, { Events event ->
      event.name == "new_leaver"
    })
  }

  def "getLatestSentCampaign_returnsFirstSentCampaign"() {
    given:
    def testCampaign = campaign("campaign_1", "First Campaign", OffsetDateTime.parse("2024-01-15T10:00:00Z"))
    campaignsApi.getCampaigns(null, null, 1, 0, null, "sent", null, null, null, null, null, null, null, "send_time", "DESC", null) >>
        new GetCampaigns200Response().campaigns([testCampaign])

    when:
    def result = service.getLatestSentCampaign()

    then:
    result != null
    result.id == "campaign_1"
    result.settings.title == "First Campaign"
  }

  def "getLatestSentCampaign_returnsNullWhenNoCampaignsFound"() {
    given:
    campaignsApi.getCampaigns(null, null, 1, 0, null, "sent", null, null, null, null, null, null, null, "send_time", "DESC", null) >>
        new GetCampaigns200Response().campaigns([])

    when:
    def result = service.getLatestSentCampaign()

    then:
    result == null
  }

  def "getLatestSentCampaign_throwsExceptionWhenApiFails"() {
    given:
    campaignsApi.getCampaigns(null, null, 1, 0, null, "sent", null, null, null, null, null, null, null, "send_time", "DESC", null) >>
        { throw new RuntimeException("API error") }

    when:
    service.getLatestSentCampaign()

    then:
    thrown(RuntimeException)
  }

  def "getCampaignRecipients_returnsSinglePageOfRecipients"() {
    given:
    def campaignId = "campaign_123"
    def recipient1 = recipient("email1@example.com", "msg_1")
    def recipient2 = recipient("email2@example.com", "msg_2")

    reportsApi.getReportsIdSentTo(campaignId, null, null, 1000, 0) >>
        new SentTo().sentTo([recipient1, recipient2])

    when:
    def collectedRecipients = []
    service.processCampaignRecipients(campaignId, { page -> collectedRecipients.addAll(page) })

    then:
    collectedRecipients.size() == 2
    collectedRecipients[0].emailAddress == "email1@example.com"
    collectedRecipients[1].emailAddress == "email2@example.com"
  }

  def "getCampaignRecipients_paginatesThroughMultiplePages"() {
    given:
    def campaignId = "campaign_123"
    def recipients1 = (1..1000).collect { recipient("email${it}@example.com", "msg_${it}") }
    def recipients2 = (1001..1500).collect { recipient("email${it}@example.com", "msg_${it}") }

    reportsApi.getReportsIdSentTo(campaignId, null, null, 1000, 0) >>
        new SentTo().sentTo(recipients1)
    reportsApi.getReportsIdSentTo(campaignId, null, null, 1000, 1000) >>
        new SentTo().sentTo(recipients2)

    when:
    def collectedRecipients = []
    service.processCampaignRecipients(campaignId, { page -> collectedRecipients.addAll(page) })

    then:
    collectedRecipients.size() == 1500
    collectedRecipients[0].emailAddress == "email1@example.com"
    collectedRecipients[999].emailAddress == "email1000@example.com"
    collectedRecipients[1000].emailAddress == "email1001@example.com"
    collectedRecipients[1499].emailAddress == "email1500@example.com"
  }

  def "getCampaignRecipients_stopsWhenNoMoreRecipients"() {
    given:
    def campaignId = "campaign_123"
    reportsApi.getReportsIdSentTo(campaignId, null, null, 1000, 0) >> new SentTo().sentTo([])

    when:
    def collectedRecipients = []
    service.processCampaignRecipients(campaignId, { page -> collectedRecipients.addAll(page) })

    then:
    collectedRecipients.isEmpty()
  }

  def "getCampaignRecipients_throwsExceptionWhenApiFails"() {
    given:
    def campaignId = "campaign_123"
    reportsApi.getReportsIdSentTo(campaignId, null, null, 1000, 0) >> { throw new RuntimeException("API error") }

    when:
    service.processCampaignRecipients(campaignId, { page -> })

    then:
    thrown(RuntimeException)
  }

  def "getCampaignActivity_returnsSinglePageOfActivity"() {
    given:
    def campaignId = "campaign_123"
    def activity1 = openActivity("msg_1")
    def activity2 = clickActivity("msg_2")

    reportsApi.getReportsIdEmailActivity(campaignId, null, null, 1000, 0, null) >>
        new EmailActivity().emails([activity1, activity2])

    when:
    def collectedActivities = []
    service.processCampaignActivity(campaignId, { page -> collectedActivities.addAll(page) })

    then:
    collectedActivities.size() == 2
    collectedActivities[0].emailId == "msg_1"
    collectedActivities[1].emailId == "msg_2"
  }

  def "getCampaignActivity_paginatesThroughMultiplePages"() {
    given:
    def campaignId = "campaign_123"
    def activities1 = (1..1000).collect { openActivity("msg_${it}") }
    def activities2 = (1001..1200).collect { clickActivity("msg_${it}") }

    reportsApi.getReportsIdEmailActivity(campaignId, null, null, 1000, 0, null) >>
        new EmailActivity().emails(activities1)
    reportsApi.getReportsIdEmailActivity(campaignId, null, null, 1000, 1000, null) >>
        new EmailActivity().emails(activities2)

    when:
    def collectedActivities = []
    service.processCampaignActivity(campaignId, { page -> collectedActivities.addAll(page) })

    then:
    collectedActivities.size() == 1200
    collectedActivities[0].emailId == "msg_1"
    collectedActivities[999].emailId == "msg_1000"
    collectedActivities[1000].emailId == "msg_1001"
    collectedActivities[1199].emailId == "msg_1200"
  }

  def "getCampaignActivity_stopsWhenNoMoreActivity"() {
    given:
    def campaignId = "campaign_123"
    reportsApi.getReportsIdEmailActivity(campaignId, null, null, 1000, 0, null) >> new EmailActivity().emails([])

    when:
    def collectedActivities = []
    service.processCampaignActivity(campaignId, { page -> collectedActivities.addAll(page) })

    then:
    collectedActivities.isEmpty()
  }

  def "getCampaignActivity_throwsExceptionWhenApiFails"() {
    given:
    def campaignId = "campaign_123"
    reportsApi.getReportsIdEmailActivity(campaignId, null, null, 1000, 0, null) >> { throw new RuntimeException("API error") }

    when:
    service.processCampaignActivity(campaignId, { page -> })

    then:
    thrown(RuntimeException)
  }

  def "getCampaignRecipients_handlesEmptyAbsplitGroupInJsonResponse"() {
    given:
    def campaignId = "campaign_123"
    def jsonResponse = '''
    {
      "sent_to": [
        {
          "email_id": "msg_1",
          "email_address": "test@example.com",
          "absplit_group": "",
          "status": "sent",
          "open_count": 1
        }
      ],
      "campaign_id": "campaign_123",
      "total_items": 1
    }
    '''

    def objectMapper = new ObjectMapper()
    objectMapper.registerModule(new JavaTimeModule())

    when:
    def sentTo = objectMapper.readValue(jsonResponse, SentTo.class)

    then:
    sentTo.sentTo.size() == 1
    sentTo.sentTo[0].emailAddress == "test@example.com"
    sentTo.sentTo[0].absplitGroup == null
  }
}
