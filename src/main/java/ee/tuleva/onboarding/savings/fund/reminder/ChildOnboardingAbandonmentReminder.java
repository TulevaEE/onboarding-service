package ee.tuleva.onboarding.savings.fund.reminder;

import ee.tuleva.onboarding.auth.principal.Person;

record ChildOnboardingAbandonmentReminder(long parentUserId, Person parent, String parentEmail) {}
