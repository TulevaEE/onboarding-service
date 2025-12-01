package ee.tuleva.onboarding.kyc.survey;

import jakarta.validation.Valid;
import java.util.List;

public record KycSurveyResponse(List<@Valid KycSurveyResponseItem> answers) {}
