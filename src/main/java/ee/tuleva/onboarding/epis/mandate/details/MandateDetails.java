package ee.tuleva.onboarding.epis.mandate.details;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.MandateView;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.io.Serializable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
 * Note that a @JsonCreator is required on non-empty constructor for deserializing.
 */
@Getter
@RequiredArgsConstructor
@JsonDeserialize(using = MandateDetailsDeserializer.class)
public abstract class MandateDetails implements Serializable {

  @JsonView(MandateView.Default.class)
  protected final MandateType mandateType;

  @JsonIgnore
  public abstract ApplicationType getApplicationType();
}
