package ee.tuleva.onboarding.mandate.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.mandate.ApplicationType;
import org.jspecify.annotations.Nullable;

public interface ApplicationDetails {

  @JsonIgnore
  @Nullable Integer getPillar();

  @JsonIgnore
  ApplicationType getType();
}
