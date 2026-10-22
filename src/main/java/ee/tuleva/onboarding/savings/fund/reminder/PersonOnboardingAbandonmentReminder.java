package ee.tuleva.onboarding.savings.fund.reminder;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.Locale;

record PersonOnboardingAbandonmentReminder(
    long userId, Person person, String email, Locale locale) {}
