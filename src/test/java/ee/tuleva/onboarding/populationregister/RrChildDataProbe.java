package ee.tuleva.onboarding.populationregister;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Manual diagnostic — NOT a CI test. It answers one question: when we ask the population
 * register (rahvastikuregister) about a CHILD, does the child's {@code hooldusoigused}
 * block list the child's guardians (both parents) via {@code teineIsikIsikukood}?
 *
 * <p>Today we DO query the child, but only for identity: {@code CustodyVerificationService#verify}
 * calls {@code fetchPerson(parentCode, childCode)} with {@code DataFields.identity()}, and
 * {@code PersonMapper#toPerson} keeps only name/DOB/status/citizenship — any custody block on
 * the child response is dropped. This probe asks for the custody block ON THE CHILD (under a
 * parent's authorisation) and prints the RAW response so we can see everything RR returns.
 *
 * <p>Self-contained on purpose: it builds its own {@link RestClient} and imports none of the
 * populationregister production classes, so it compiles on any branch. No personal codes are
 * hardcoded — they come from environment variables, so nothing sensitive lands in git.
 *
 * <p>Run it from an environment that can reach the RR X-Road consumer, passing your OWN personal
 * code as the authorised requester and the child's code as the subject:
 *
 * <pre>{@code
 * RR_PROBE_ENABLED=true \
 * RR_PROBE_REQUESTER_CODE=<your-personal-code> \
 * RR_PROBE_CHILD_CODE=62509240055 \
 * POPULATION_REGISTER_URL=https://xroad-consumer.tuleva.ee/r1/EE/GOV/70008440/rr/domesticDataExchange/v1 \
 * POPULATION_REGISTER_CLIENT_ID=EE/COM/14118923/tuleva-fund-management \
 * ./gradlew test --tests '*RrChildDataProbe' -i
 * }</pre>
 *
 * The {@code -i} (info) flag surfaces the printed response; it is also captured in
 * {@code build/reports/tests/test/} either way.
 */
class RrChildDataProbe {

  private static final String REQUEST_REASON = "oigustatud";

  @Test
  @EnabledIfEnvironmentVariable(named = "RR_PROBE_ENABLED", matches = "true")
  void dumpsRawChildDataToSeeIfCustodyBlockListsGuardians() {
    String url = required("POPULATION_REGISTER_URL");
    String clientId = required("POPULATION_REGISTER_CLIENT_ID");
    String requesterCode = required("RR_PROBE_REQUESTER_CODE");
    String childCode = required("RR_PROBE_CHILD_CODE");

    RestClient restClient = RestClient.builder().baseUrl(url).build();

    // Ask for the child's identity AND custody block. The custody sub-fields are exactly the
    // ones the app already uses for the parent query; `teineIsikIsikukood` = "the other person",
    // which on a CHILD subject would be a guardian, if RR returns it.
    Map<String, Object> request =
        Map.of(
            "isikukoodid",
            List.of(childCode),
            "andmevaljad",
            Map.of(
                "isikuandmed",
                List.of("Isikukood", "Eesnimi", "Perekonnanimi", "SynniKuupaev", "IsikuStaatus"),
                "hooldusoigused",
                List.of(
                    "Liik", "Staatus", "HooldusoigusAlgus", "TeineIsikIsikukood", "TeineIsikOlek")));

    List<Map<String, Object>> response =
        restClient
            .post()
            .uri("/isikud")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("X-Road-Client", clientId)
            .header("X-Road-UserId", requesterCode)
            .header("X-Road-Id", UUID.randomUUID().toString())
            .header("RR-Request-Reason", REQUEST_REASON)
            .body(request)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    System.out.println("=== RAW population register response for the child ===");
    System.out.println(response);

    if (response != null) {
      for (Map<String, Object> person : response) {
        System.out.println(
            "--- hooldusoigused on the child (candidate guardians = teineIsikIsikukood) ---");
        System.out.println(person.get("hooldusoigused"));
      }
    }
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Set the " + name + " environment variable to run this probe");
    }
    return value;
  }
}
