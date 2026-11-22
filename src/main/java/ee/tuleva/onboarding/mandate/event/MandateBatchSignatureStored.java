package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.Locale;

public record MandateBatchSignatureStored(
    User user, MandateBatch mandateBatch, Locale locale, Instant signedAt) {}
