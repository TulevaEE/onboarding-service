package ee.tuleva.onboarding.kyc.survey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record KycSurveyResponse(
    @NotNull @Size(min = 1) List<@Valid KycSurveyResponseItem> answers) {}
