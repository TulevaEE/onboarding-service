package ee.tuleva.onboarding.populationregister;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Diagnostic, not a test. Asks X-Road for the population register's own service description so we
 * learn the real andmevaljad field names — the ones we still guess at for citizenships, addresses,
 * contacts, documents and birth place — instead of asking the register about a person. SMIT
 * publishes no field list outside the OpenAPI, and the OpenAPI is only reachable through a security
 * server, so this has to run from an environment that has one.
 *
 * <p>Queries no person and therefore carries no personal data: listMethods and getOpenAPI are
 * X-Road meta-services about the service itself.
 *
 * <p>Run it against production X-Road:
 *
 * <pre>
 * RR_PROBE_ENABLED=true \
 * RR_PROBE_SECURITY_SERVER=https://xroad-consumer.tuleva.ee \
 * POPULATION_REGISTER_CLIENT_ID=EE/COM/14118923/tuleva-fund-management \
 * ./gradlew test --tests '*RrServiceDescriptionProbe' -i
 * </pre>
 *
 * <p>listMethods also settles which service code actually exists.
 */
@EnabledIfEnvironmentVariable(named = "RR_PROBE_ENABLED", matches = "true")
class RrServiceDescriptionProbe {

  private static final String PROVIDER = env("RR_PROBE_PROVIDER", "EE/GOV/70008440/rr");
  // listMethods (2026-07-31) reports the service code as domesticDataExchange, exposing
  // POST /v1/isikud — so the production URL, which ends in domesticDataExchange/v1, is correct.
  private static final String SERVICE_CODE = env("RR_PROBE_SERVICE_CODE", "domesticDataExchange");
  private static final Path OUTPUT = Path.of("build", "rr-service-description");

  private static final List<String> FIELDS_WE_STILL_GUESS_AT =
      List.of(
          "kodakondsus",
          "Kodakondsus",
          "aadress",
          "Aadress",
          "kontakt",
          "Kontakt",
          "dokument",
          "Dokument",
          "elamis",
          "Elamis",
          "synniKoht",
          "SynniKoht",
          "Synnikoht");

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Test
  void printsTheServiceCodesAndTheOpenApiFieldNames() throws Exception {
    String securityServer = required("RR_PROBE_SECURITY_SERVER");
    String clientId = required("POPULATION_REGISTER_CLIENT_ID");

    String methods = get(securityServer + "/r1/" + PROVIDER + "/listMethods", clientId);
    write("listMethods.json", methods);
    System.out.println("=== listMethods (which service code really exists) ===");
    System.out.println(methods);

    String openApi =
        get(
            securityServer + "/r1/" + PROVIDER + "/getOpenAPI?serviceCode=" + SERVICE_CODE,
            clientId);
    write("openapi.yaml", openApi);
    System.out.println("=== field names we still guess at ===");
    Arrays.stream(openApi.split("\n"))
        .filter(line -> FIELDS_WE_STILL_GUESS_AT.stream().anyMatch(line::contains))
        .map(String::strip)
        .distinct()
        .forEach(System.out::println);
    System.out.println("=== full description written to " + OUTPUT.toAbsolutePath() + " ===");

    assertThat(openApi).isNotBlank();
  }

  private String get(String url, String clientId) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .header("X-Road-Client", clientId)
            .header("Accept", "application/json, application/yaml, text/plain, */*")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Population register meta-service call failed: url="
              + url
              + ", status="
              + response.statusCode()
              + ", body="
              + response.body());
    }
    return response.body();
  }

  private void write(String fileName, String content) throws Exception {
    Files.createDirectories(OUTPUT);
    Files.writeString(OUTPUT.resolve(fileName), content, UTF_8);
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing environment variable: name=" + name);
    }
    return value;
  }
}
