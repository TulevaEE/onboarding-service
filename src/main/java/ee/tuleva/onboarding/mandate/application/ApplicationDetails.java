package ee.tuleva.onboarding.mandate.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ApplicationDetails {

  @JsonIgnore
  Integer getPillar();

}
