package ee.tuleva.onboarding.populationregister;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PopulationRegisterMember {

  private static final int CLIENT_ID_PARTS = 4;
  private static final int MEMBER_CODE_PART = 2;

  private final PopulationRegisterProperties properties;

  public String memberCode() {
    String[] parts = properties.clientId().split("/");
    if (parts.length != CLIENT_ID_PARTS || parts[MEMBER_CODE_PART].isBlank()) {
      throw new IllegalStateException(
          "Population register client id names no member code: property=population-register.client-id");
    }
    return parts[MEMBER_CODE_PART];
  }
}
