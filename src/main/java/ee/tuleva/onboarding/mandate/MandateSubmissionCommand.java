package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.mandate.details.MandateDetails;
import lombok.Builder;

@Builder
public record MandateSubmissionCommand<T extends MandateDetails>(
    String processId, GenericMandateSubmission<T> submission) {}
