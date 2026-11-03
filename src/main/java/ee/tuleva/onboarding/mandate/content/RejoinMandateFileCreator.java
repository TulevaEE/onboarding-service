package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.mandate.MandateType.REJOIN;

import org.springframework.stereotype.Service;

@Service
class RejoinMandateFileCreator extends ApplicantOnlyMandateFileCreator {

  RejoinMandateFileCreator(MandateContentService mandateContentService) {
    super(mandateContentService, REJOIN, "sissemaksete_tasumise_avaldus", "second_pillar_rejoin");
  }
}
