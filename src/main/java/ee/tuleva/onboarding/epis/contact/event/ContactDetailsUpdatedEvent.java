package ee.tuleva.onboarding.epis.contact.event;

import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.user.User;

public record ContactDetailsUpdatedEvent(User user, ContactDetails contactDetails) {}
