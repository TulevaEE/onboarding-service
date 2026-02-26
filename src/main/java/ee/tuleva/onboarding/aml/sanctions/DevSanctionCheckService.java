package ee.tuleva.onboarding.aml.sanctions;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevSanctionCheckService implements PepAndSanctionCheckService {

  private final JsonMapper objectMapper;

  @Override
  public MatchResponse match(Person person, Country country) {
    return emptyResponse();
  }

  private MatchResponse emptyResponse() {
    return new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
  }
}
