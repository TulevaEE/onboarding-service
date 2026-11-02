package ee.tuleva.onboarding.rejoin;

import com.fasterxml.jackson.annotation.JsonProperty;

record RejoinPermissionResponse(RejoinDecision decision) {

  @JsonProperty("permitted")
  public boolean isPermitted() {
    return decision == RejoinDecision.PERMITTED;
  }
}
