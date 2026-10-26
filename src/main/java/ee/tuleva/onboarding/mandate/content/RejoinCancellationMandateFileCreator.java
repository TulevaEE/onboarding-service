package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.mandate.MandateType.REJOIN_CANCELLATION;

import org.springframework.stereotype.Service;

@Service
class RejoinCancellationMandateFileCreator extends ApplicantOnlyMandateFileCreator {

  RejoinCancellationMandateFileCreator(MandateContentService mandateContentService) {
    super(
        mandateContentService,
        REJOIN_CANCELLATION,
        "maksete_mittetegemise_avaldus",
        "second_pillar_rejoin_cancellation");
  }
}
