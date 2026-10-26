package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.mandate.MandateType.REJOIN;

import org.springframework.stereotype.Service;

@Service
class RejoinMandateFileCreator extends ApplicantOnlyMandateFileCreator {

  RejoinMandateFileCreator(MandateContentService mandateContentService) {
    super(mandateContentService, REJOIN, "maksete_tegemise_avaldus", "second_pillar_rejoin");
  }
}
