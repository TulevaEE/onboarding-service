package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;

import ee.tuleva.onboarding.kyc.KycSurveyPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

public record KycSurveyResponse(
    @NotNull @Size(min = 1) List<@Valid KycSurveyResponseItem> answers, KycSurveyPurpose purpose)
    implements Serializable {

  public KycSurveyResponse {
    purpose = purpose == null ? PERSONAL_ONBOARDING : purpose;
  }

  public KycSurveyResponse(List<KycSurveyResponseItem> answers) {
    this(answers, PERSONAL_ONBOARDING);
  }
}
