package ee.tuleva.onboarding.aml.sanctions;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevSanctionCheckService implements PepAndSanctionCheckService {

  private final ObjectMapper objectMapper;

  @Override
  public MatchResponse match(Person person, String country) {
    return new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
  }
}
