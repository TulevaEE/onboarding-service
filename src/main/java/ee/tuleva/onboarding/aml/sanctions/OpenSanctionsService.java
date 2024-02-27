package ee.tuleva.onboarding.aml.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
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
  public ArrayNode match(String fullName, String idNumber, String country) {
    var countries = getCountries(country);
    var gender = PersonalCode.getGender(idNumber).name().toLowerCase();
    var birthDate = PersonalCode.getDateOfBirth(idNumber);
    var properties =
        new PersonProperties(
            List.of(fullName), List.of(birthDate.toString()), countries, List.of(gender));
    var personQuery = new PersonQuery(properties);
    var matchRequest = new MatchRequest(Map.of(idNumber, personQuery));

    String json =
        restTemplate.postForObject(
            baseUrl
                + "/match/default?algorithm=logic-v1&threshold=0.8&cutoff=0.7"
                + "&topics=role.pep&topics=role.rca&topics=sanction&topics=sanction.linked"
                + "&facets=countries&facets=topics&facets=datasets&facets=gender",
            new HttpEntity<>(matchRequest, headers()),
            String.class);

    JsonNode rootNode = objectMapper.readTree(json);
    return (ArrayNode) rootNode.path("responses").path(idNumber).path("results");
  }

  private HashSet<String> getCountries(String country) {
    var countries = new HashSet<String>();
    countries.add("ee");
    if (country != null) {
      countries.add(country);
    }
    return countries;
  }

  private record PersonProperties(
      List<String> name, List<String> birthDate, Set<String> country, List<String> gender) {}

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
