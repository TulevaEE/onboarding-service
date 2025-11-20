package ee.tuleva.onboarding.notification.email.mailchimp;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.MAILCHIMP_CAMPAIGN;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.mandate.email.persistence.EmailRepository;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailchimpCampaignMetricsService {

  private final EmailRepository emailRepository;
  private final EventLogRepository eventLogRepository;

  public MailchimpCampaignMetrics getMetrics(String mailchimpCampaign) {
    log.info("Calculating metrics for campaign: mailchimpCampaign={}", mailchimpCampaign);

    int totalSent = countTotalSent(mailchimpCampaign);

    if (totalSent == 0) {
      log.warn("No emails found for campaign: mailchimpCampaign={}", mailchimpCampaign);
      return new MailchimpCampaignMetrics(
          extractCampaignId(mailchimpCampaign),
          extractCampaignName(mailchimpCampaign),
          0,
          0,
          0.0,
          0,
          0.0,
          0,
          0.0);
    }

    int uniqueOpens = countUniqueEventsByType(mailchimpCampaign, "OPEN");
    int uniqueClicks = countUniqueEventsByType(mailchimpCampaign, "CLICK");
    int unsubscribes = countUniqueEventsByType(mailchimpCampaign, "UNSUBSCRIBE");

    double openRate = calculateRate(uniqueOpens, totalSent);
    double clickRate = calculateRate(uniqueClicks, totalSent);
    double unsubscribeRate = calculateRate(unsubscribes, totalSent);

    return new MailchimpCampaignMetrics(
        extractCampaignId(mailchimpCampaign),
        extractCampaignName(mailchimpCampaign),
        totalSent,
        uniqueOpens,
        openRate,
        uniqueClicks,
        clickRate,
        unsubscribes,
        unsubscribeRate);
  }

  private int countTotalSent(String mailchimpCampaign) {
    List<Email> emails =
        StreamSupport.stream(emailRepository.findAll().spliterator(), false)
            .filter(email -> email.getType() == MAILCHIMP_CAMPAIGN)
            .filter(email -> mailchimpCampaign.equals(email.getMailchimpCampaign()))
            .toList();
    return emails.size();
  }

  private int countUniqueEventsByType(String mailchimpCampaign, String eventType) {
    List<EventLog> events =
        StreamSupport.stream(eventLogRepository.findAll().spliterator(), false)
            .filter(event -> eventType.equals(event.getType()))
            .filter(
                event -> {
                  Object campaignData = event.getData().get("mailchimpCampaign");
                  return mailchimpCampaign.equals(campaignData);
                })
            .toList();

    return (int) events.stream().map(EventLog::getPrincipal).distinct().count();
  }

  private double calculateRate(int count, int total) {
    if (total == 0) {
      return 0.0;
    }
    return (count * 100.0) / total;
  }

  private String extractCampaignId(String mailchimpCampaign) {
    int lastUnderscore = mailchimpCampaign.lastIndexOf('_');
    if (lastUnderscore > 0 && lastUnderscore < mailchimpCampaign.length() - 1) {
      return mailchimpCampaign.substring(lastUnderscore + 1);
    }
    return mailchimpCampaign;
  }

  private String extractCampaignName(String mailchimpCampaign) {
    int lastUnderscore = mailchimpCampaign.lastIndexOf('_');
    if (lastUnderscore > 0) {
      return mailchimpCampaign.substring(0, lastUnderscore);
    }
    return mailchimpCampaign;
  }
}
