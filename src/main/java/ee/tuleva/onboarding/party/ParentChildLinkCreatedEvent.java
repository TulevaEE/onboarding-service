package ee.tuleva.onboarding.party;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ParentChildLinkCreatedEvent(
    String parentPersonalCode, String childPersonalCode, RepresentationType relationshipType) {}
