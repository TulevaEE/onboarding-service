package ee.tuleva.onboarding.aml.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Profile("!dev")
public class OpenSanctionsService implements SanctionCheckService {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${opensanctions.url}")
  private String baseUrl;

  public OpenSanctionsService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
    this.restTemplate = restTemplateBuilder.rootUri(baseUrl).build();
    this.objectMapper = objectMapper;
  }

  @Override
  @SneakyThrows
  public JsonNode match(String fullName, LocalDate birthDate, String idNumber, String nationality) {
    var nationalities = new HashSet<>(List.of("ee", "eu", "suhh", nationality));
    var properties =
        new PersonProperties(
            List.of(fullName), List.of(birthDate.toString()), idNumber, nationalities);
    var personQuery = new PersonQuery(properties);
    var matchRequest = new MatchRequest(Map.of(idNumber, personQuery));

    String json =
        restTemplate.postForObject(
            baseUrl + "/match/default?algorithm=best&fuzzy=false",
            new HttpEntity<>(matchRequest, headers()),
            String.class);

    JsonNode rootNode = objectMapper.readTree(json);
    return rootNode.path("responses").path(idNumber).path("results");
  }

  private record PersonProperties(
      List<String> name, List<String> birthDate, String idNumber, Set<String> nationality) {}

  private record PersonQuery(String schema, PersonProperties properties) {

    public PersonQuery(PersonProperties properties) {
      this("Person", properties);
    }
  }

  private record MatchRequest(Map<String, PersonQuery> queries) {}

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    return headers;
  }
}
