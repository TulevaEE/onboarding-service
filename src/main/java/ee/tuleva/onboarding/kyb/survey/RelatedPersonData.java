package ee.tuleva.onboarding.kyb.survey;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record RelatedPersonData(String personalCode, @Nullable String name) {}
