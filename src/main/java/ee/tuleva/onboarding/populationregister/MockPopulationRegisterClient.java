package ee.tuleva.onboarding.populationregister;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@Profile("mock")
class MockPopulationRegisterClient implements PopulationRegisterClient {

  private final JsonMapper jsonMapper;

  MockPopulationRegisterClient(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public PopulationRegisterPerson fetchPerson(String personalCode) {
    log.info("Mock population register person lookup");
    return PersonMapper.toPerson(load("mock-person-response.json"));
  }

  @Override
  public List<CustodyRight> fetchCustodyRights(String personalCode) {
    log.info("Mock population register custody lookup");
    return PersonMapper.toCustodyRights(load("mock-custody-response.json"));
  }

  private PersonResponse load(String fileName) {
    try (InputStream stream =
        new ClassPathResource("populationregister/" + fileName).getInputStream()) {
      var responses = jsonMapper.readValue(stream, PersonResponse[].class);
      if (responses.length == 0) {
        throw new PopulationRegisterException("Empty mock response: file=" + fileName);
      }
      return responses[0];
    } catch (IOException e) {
      throw new PopulationRegisterException("Failed to load mock response: file=" + fileName, e);
    }
  }
}
