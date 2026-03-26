package ee.tuleva.onboarding.kyb.survey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

record KybSurveyResponse(@NotNull @Size(min = 1) List<@Valid KybSurveyResponseItem> answers)
    implements Serializable {}
