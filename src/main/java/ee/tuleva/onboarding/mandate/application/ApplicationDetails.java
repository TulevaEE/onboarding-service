package ee.tuleva.onboarding.mandate.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.mandate.ApplicationType;

public interface ApplicationDetails {

  @JsonIgnore
  Integer getPillar();

  @JsonIgnore
  ApplicationType getType();
}
