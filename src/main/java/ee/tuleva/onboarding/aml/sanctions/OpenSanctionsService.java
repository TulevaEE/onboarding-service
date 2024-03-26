package ee.tuleva.onboarding.aml.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.address.Address;
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
public class OpenSanctionsService implements PepAndSanctionCheckService {

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
  public MatchResponse match(Person person, Address address) {
    var personalCode = person.getPersonalCode();
    var fullName = person.getFullName();
    var countries = getCountries(address);
    var gender = PersonalCode.getGender(personalCode).name().toLowerCase();
    var birthDate = PersonalCode.getDateOfBirth(personalCode).toString();
    var properties =
        new PersonProperties(List.of(fullName), List.of(birthDate), countries, List.of(gender));
    var personQuery = new PersonQuery(properties);
    var matchRequest = new MatchRequest(Map.of(personalCode, personQuery));

    String json =
        restTemplate.postForObject(
            baseUrl
                + "/match/default?algorithm=logic-v1&threshold=0.8&cutoff=0.7"
                + "&topics=role.pep&topics=role.rca&topics=sanction"
                + "&facets=countries&facets=topics&facets=datasets&facets=gender",
            new HttpEntity<>(matchRequest, headers()),
            String.class);

    JsonNode rootNode = objectMapper.readTree(json);
    JsonNode response = rootNode.path("responses").path(personalCode);
    return new MatchResponse((ArrayNode) response.path("results"), response.path("query"));
  }

  private HashSet<String> getCountries(Address address) {
    var countries = new HashSet<String>();
    countries.add("ee");
    if (address != null && address.getCountryCode() != null) {
      countries.add(address.getCountryCode());
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
