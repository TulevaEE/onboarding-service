package ee.tuleva.onboarding.notification.email.mailchimp;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SENT;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.MAILCHIMP_CAMPAIGN;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.mandate.email.persistence.EmailRepository;
import ee.tuleva.onboarding.notification.email.provider.MailchimpService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import io.github.erkoristhein.mailchimp.marketing.model.Campaign;
import io.github.erkoristhein.mailchimp.marketing.model.CampaignReport;
import io.github.erkoristhein.mailchimp.marketing.model.MemberActivity2;
import io.github.erkoristhein.mailchimp.marketing.model.SentToRecipient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailchimpCampaignSyncService {

  private final MailchimpService mailchimpService;
  private final EmailRepository emailRepository;
  private final EventLogRepository eventLogRepository;
  private final UserRepository userRepository;
  private final CrmMailchimpRepository crmMailchimpRepository;
  private final MailchimpCampaignMetricsService metricsService;

  @Transactional
  public void syncLatestCampaign() {
    log.info("Starting Mailchimp campaign sync");

    Campaign campaign = mailchimpService.getLatestSentCampaign();
    if (campaign == null) {
      log.warn("No sent campaigns found, skipping sync");
      return;
    }

    String campaignId = campaign.getId();
    String campaignName = campaign.getSettings().getTitle();
    String mailchimpCampaign = campaignName + " " + campaignId;

    log.info(
        "Processing campaign: id={}, name={}, mailchimpCampaign={}",
        campaignId,
        campaignName,
        mailchimpCampaign);

    if (emailRepository.existsByMailchimpCampaign(mailchimpCampaign)) {
      log.info("Campaign already synced: mailchimpCampaign={}, skipping", mailchimpCampaign);
      return;
    }

    syncRecipients(campaign, mailchimpCampaign);
    syncActivity(campaignId, mailchimpCampaign);
    verifyMetrics(campaignId, mailchimpCampaign);

    log.info("Successfully synced campaign: mailchimpCampaign={}", mailchimpCampaign);
  }

  private void syncRecipients(Campaign campaign, String mailchimpCampaign) {
    log.info("Syncing recipients for campaign: mailchimpCampaign={}", mailchimpCampaign);

    Instant sendTime = toInstant(campaign.getSendTime());

    mailchimpService.processCampaignRecipients(
        campaign.getId(),
        recipientsPage -> {
          List<Email> emails =
              recipientsPage.stream()
                  .map(recipient -> buildEmailFromRecipient(recipient, mailchimpCampaign, sendTime))
                  .filter(
                      email -> {
                        if (email.getPersonalCode() == null) {
                          log.error(
                              "Skipping email without personal code: mandrillMessageId={}, mailchimpCampaign={}",
                              email.getMandrillMessageId(),
                              mailchimpCampaign);
                          return false;
                        }
                        return true;
                      })
                  .collect(toList());

          if (!emails.isEmpty()) {
            emailRepository.saveAll(emails);
            log.info(
                "Saved batch of {} recipients for campaign: mailchimpCampaign={}",
                emails.size(),
                mailchimpCampaign);
          }
        });
  }

  private Email buildEmailFromRecipient(
      SentToRecipient recipient, String mailchimpCampaign, Instant sendTime) {
    String emailAddress = recipient.getEmailAddress();
    String personalCode = findPersonalCodeByEmail(emailAddress);

    return Email.builder()
        .personalCode(personalCode)
        .type(MAILCHIMP_CAMPAIGN)
        .status(SENT)
        .mailchimpCampaign(mailchimpCampaign)
        .mandrillMessageId(recipient.getEmailId())
        .createdDate(sendTime)
        .updatedDate(sendTime)
        .build();
  }

  private String findPersonalCodeByEmail(String emailAddress) {
    return userRepository
        .findByEmail(emailAddress)
        .map(User::getPersonalCode)
        .orElseGet(() -> crmMailchimpRepository.findPersonalCodeByEmail(emailAddress).orElse(null));
  }

  private void syncActivity(String campaignId, String mailchimpCampaign) {
    log.info("Syncing activity for campaign: campaignId={}", campaignId);

    mailchimpService.processCampaignActivity(
        campaignId,
        activityPage -> {
          List<EventLog> eventLogs =
              activityPage.stream()
                  .filter(emailWithActivity -> emailWithActivity.getEmailId() != null)
                  .flatMap(
                      emailWithActivity -> {
                        String emailId = emailWithActivity.getEmailId();
                        Optional<Email> emailOpt = emailRepository.findByMandrillMessageId(emailId);

                        if (emailOpt.isEmpty()) {
                          log.warn(
                              "Email not found for activity: emailId={}, campaignId={}, skipping",
                              emailId,
                              campaignId);
                          return Stream.empty();
                        }

                        Email email = emailOpt.get();
                        if (email.getPersonalCode() == null) {
                          log.warn(
                              "Email has no personalCode: emailId={}, campaignId={}, skipping event",
                              emailId,
                              campaignId);
                          return Stream.empty();
                        }

                        if (emailWithActivity.getActivity() == null) {
                          return Stream.empty();
                        }

                        return emailWithActivity.getActivity().stream()
                            .map(
                                activityEvent ->
                                    buildEventLog(activityEvent, email, mailchimpCampaign))
                            .filter(Objects::nonNull);
                      })
                  .collect(toList());

          if (!eventLogs.isEmpty()) {
            eventLogRepository.saveAll(eventLogs);
            log.info(
                "Saved batch of {} event logs for campaign: campaignId={}",
                eventLogs.size(),
                campaignId);
          }
        });
  }

  private EventLog buildEventLog(MemberActivity2 activity, Email email, String mailchimpCampaign) {
    String action = activity.getAction();
    if (action == null) {
      return null;
    }

    String eventType = mapActivityToEventType(action);
    if (eventType == null) {
      log.debug("Ignoring unsupported activity type: action={}", action);
      return null;
    }

    Map<String, Object> eventData = new HashMap<>();
    eventData.put("mandrillMessageId", email.getMandrillMessageId());
    eventData.put("emailType", MAILCHIMP_CAMPAIGN.name());
    eventData.put("mailchimpCampaign", mailchimpCampaign);

    if ("CLICK".equals(eventType) && activity.getUrl() != null) {
      eventData.put("path", activity.getUrl());
    }

    return EventLog.builder()
        .type(eventType)
        .principal(email.getPersonalCode())
        .timestamp(toInstant(activity.getTimestamp()))
        .data(eventData)
        .build();
  }

  private String mapActivityToEventType(String action) {
    return switch (action.toUpperCase()) {
      case "OPEN", "CLICK" -> action.toUpperCase();
      case "UNSUB", "UNSUBSCRIBE" -> "UNSUBSCRIBE";
      default -> null;
    };
  }

  private Instant toInstant(OffsetDateTime offsetDateTime) {
    return offsetDateTime != null ? offsetDateTime.toInstant() : Instant.now();
  }

  private void verifyMetrics(String campaignId, String mailchimpCampaign) {
    log.info("Verifying metrics for campaign: mailchimpCampaign={}", mailchimpCampaign);

    CampaignReport mailchimpReport = mailchimpService.getCampaignReport(campaignId);
    MailchimpCampaignMetrics dbMetrics = metricsService.getMetrics(mailchimpCampaign);

    boolean hasMismatch = false;

    if (!equals(mailchimpReport.getEmailsSent(), dbMetrics.totalSent())) {
      log.warn(
          "Emails sent mismatch: mailchimpCampaign={}, mailchimp={}, database={}",
          mailchimpCampaign,
          mailchimpReport.getEmailsSent(),
          dbMetrics.totalSent());
      hasMismatch = true;
    }

    if (mailchimpReport.getOpens() != null
        && !equals(mailchimpReport.getOpens().getUniqueOpens(), dbMetrics.uniqueOpens())) {
      log.warn(
          "Unique opens mismatch: mailchimpCampaign={}, mailchimp={}, database={}",
          mailchimpCampaign,
          mailchimpReport.getOpens().getUniqueOpens(),
          dbMetrics.uniqueOpens());
      hasMismatch = true;
    }

    if (mailchimpReport.getClicks() != null
        && !equals(mailchimpReport.getClicks().getUniqueClicks(), dbMetrics.uniqueClicks())) {
      log.warn(
          "Unique clicks mismatch: mailchimpCampaign={}, mailchimp={}, database={}",
          mailchimpCampaign,
          mailchimpReport.getClicks().getUniqueClicks(),
          dbMetrics.uniqueClicks());
      hasMismatch = true;
    }

    if (!equals(mailchimpReport.getUnsubscribed(), dbMetrics.unsubscribes())) {
      log.warn(
          "Unsubscribes mismatch: mailchimpCampaign={}, mailchimp={}, database={}",
          mailchimpCampaign,
          mailchimpReport.getUnsubscribed(),
          dbMetrics.unsubscribes());
      hasMismatch = true;
    }

    if (hasMismatch) {
      log.warn(
          "Metrics verification completed with mismatches: mailchimpCampaign={}",
          mailchimpCampaign);
    } else {
      log.info("Metrics verification successful: mailchimpCampaign={}", mailchimpCampaign);
    }
  }

  private boolean equals(Integer mailchimpValue, int dbValue) {
    return mailchimpValue != null && mailchimpValue == dbValue;
  }
}
