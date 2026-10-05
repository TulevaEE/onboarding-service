package ee.tuleva.onboarding.savings.fund.reminder;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.Locale;

record ChildOnboardingAbandonmentReminder(
    long parentUserId, Person parent, String parentEmail, Locale locale) {}
