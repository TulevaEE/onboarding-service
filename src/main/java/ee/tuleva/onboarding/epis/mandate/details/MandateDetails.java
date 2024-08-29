package ee.tuleva.onboarding.epis.mandate.details;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.MandateView;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class MandateDetails {

  @JsonView(MandateView.Default.class)
  protected final MandateType mandateType;
}
