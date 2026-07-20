package ee.tuleva.onboarding.populationregister;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
  public PopulationRegisterResult<PopulationRegisterPerson> fetchPerson(
      String requesterPersonalCode, String personalCode, Duration maxAge) {
    log.info("Mock population register person lookup");
    return new PopulationRegisterResult<>(
        PersonMapper.toPerson(load("mock-person-response.json")), UUID.randomUUID());
  }

  @Override
  public PopulationRegisterResult<List<CustodyRight>> fetchCustodyRights(
      String requesterPersonalCode, Duration maxAge) {
    log.info("Mock population register custody lookup");
    return new PopulationRegisterResult<>(
        PersonMapper.toCustodyRights(load("mock-custody-response.json")), UUID.randomUUID());
  }

  @Override
  public PopulationRegisterResult<List<Guardian>> fetchCustodyRights(
      String requesterPersonalCode, String subjectPersonalCode, Duration maxAge) {
    log.info("Mock population register guardian lookup");
    return new PopulationRegisterResult<>(
        PersonMapper.toGuardians(load("mock-custody-response.json")), UUID.randomUUID());
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
