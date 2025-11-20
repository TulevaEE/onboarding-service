package ee.tuleva.onboarding.notification.email.mailchimp;

public record MailchimpCampaignMetrics(
    String campaignId,
    String campaignName,
    int totalSent,
    int uniqueOpens,
    double openRate,
    int uniqueClicks,
    double clickRate,
    int unsubscribes,
    double unsubscribeRate) {}
