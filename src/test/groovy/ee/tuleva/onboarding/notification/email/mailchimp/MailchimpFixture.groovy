package ee.tuleva.onboarding.notification.email.mailchimp

import ee.tuleva.onboarding.mandate.email.persistence.Email
import io.github.erkoristhein.mailchimp.marketing.model.*

import java.time.Instant
import java.time.OffsetDateTime

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SENT
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.MAILCHIMP_CAMPAIGN

class MailchimpFixture {

  static Campaign campaign(String id = "camp_123", String title = "Test Campaign", OffsetDateTime sendTime = OffsetDateTime.now()) {
    new Campaign().with {
      it.id = id
      it.settings = new CampaignSettings2().tap { it.title = title }
      it.sendTime = sendTime
      it
    }
  }

  static SentToRecipient recipient(String emailAddress = "test@example.com", String emailId = "msg_1") {
    new SentToRecipient().with {
      it.emailAddress = emailAddress
      it.emailId = emailId
      it
    }
  }

  static EmailActivityRecord emailActivity(String emailId = "msg_1", String action = "open", String url = null) {
    def activity = new MemberActivity2().with {
      it.action = action
      it.timestamp = OffsetDateTime.now()
      if (url != null) {
        it.url = url
      }
      it
    }

    new EmailActivityRecord().with {
      it.emailId = emailId
      it.activity = [activity]
      it
    }
  }

  static EmailActivityRecord clickActivity(String emailId = "msg_1", String url = "https://tuleva.ee") {
    emailActivity(emailId, "click", url)
  }

  static EmailActivityRecord openActivity(String emailId = "msg_1") {
    emailActivity(emailId, "open", null)
  }

  static EmailActivityRecord unsubscribeActivity(String emailId = "msg_1") {
    emailActivity(emailId, "unsub", null)
  }

  static Email email(String personalCode = "39001010000",
                     String mandrillMessageId = "msg_1",
                     String mailchimpCampaign = "Test Campaign camp_123") {
    Email.builder()
        .personalCode(personalCode)
        .type(MAILCHIMP_CAMPAIGN)
        .status(SENT)
        .mailchimpCampaign(mailchimpCampaign)
        .mandrillMessageId(mandrillMessageId)
        .createdDate(Instant.now())
        .updatedDate(Instant.now())
        .build()
  }

  static Email emailWithoutPersonalCode(String mandrillMessageId = "msg_1") {
    Email.builder()
        .personalCode(null)
        .type(MAILCHIMP_CAMPAIGN)
        .status(SENT)
        .mandrillMessageId(mandrillMessageId)
        .createdDate(Instant.now())
        .updatedDate(Instant.now())
        .build()
  }
}
