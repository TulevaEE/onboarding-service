package ee.tuleva.onboarding.analytics.transaction.generic;

public record SyncResult(
    String transactionType, String syncIdentifier, int deleted, int inserted) {}
