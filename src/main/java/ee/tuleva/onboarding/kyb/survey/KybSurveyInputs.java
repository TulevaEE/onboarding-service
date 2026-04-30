package ee.tuleva.onboarding.kyb.survey;

import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.SelfCertification;

public record KybSurveyInputs(PersonalCode personalCode, SelfCertification selfCertification) {}
