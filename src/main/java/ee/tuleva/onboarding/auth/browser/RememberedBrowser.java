package ee.tuleva.onboarding.auth.browser;

import java.time.Instant;

public record RememberedBrowser(long id, Instant expiresAt) {}
