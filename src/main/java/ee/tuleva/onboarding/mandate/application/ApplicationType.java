package ee.tuleva.onboarding.mandate.application;

public enum ApplicationType {
    TRANSFER,
    SELECTION,
    @Deprecated PAYOUT, // Use EARLY_WITHDRAWAL or WITHDRAWAL
    CANCELLATION,
    EARLY_WITHDRAWAL, // 2. samba raha väljavõtmise avaldus
    WITHDRAWAL, // 2. samba ühekordse väljamakse avaldus
}