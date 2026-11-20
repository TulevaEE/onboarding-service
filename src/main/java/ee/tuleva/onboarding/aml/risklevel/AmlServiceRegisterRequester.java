package ee.tuleva.onboarding.aml.risklevel;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.tuleva.onboarding.aml.RegisterRequester;
import ee.tuleva.onboarding.personalcode.PersonalCodeValidator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
class AmlServiceRegisterRequester implements RegisterRequester {

  private static final String ESTONIAN_X_ROAD_USER_PREFIX = "EE";
  private static final PersonalCodeValidator PERSONAL_CODES = new PersonalCodeValidator();

  private final RestClient amlServiceRestClient;
  private final RetryTemplate amlServiceRetryTemplate;
  private final AmlServiceProperties properties;

  @Override
  public Optional<String> designatedXRoadUserId() {
    String apiToken =
        properties
            .configuredApiToken()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "aml-service API token is not configured, sending no request for the"
                            + " register requester"));
    try {
      return Optional.of(
          ESTONIAN_X_ROAD_USER_PREFIX
              + validPersonalCode(amlServiceRetryTemplate.invoke(() -> fetch(apiToken))));
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    }
  }

  private @Nullable DesignatedRequester fetch(String apiToken) {
    return amlServiceRestClient
        .get()
        .uri("/v1/register-requester")
        .accept(APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(apiToken))
        .retrieve()
        .body(DesignatedRequester.class);
  }

  private static String validPersonalCode(@Nullable DesignatedRequester answer) {
    String personalCode = answer == null ? null : answer.personalCode();
    if (personalCode == null || !PERSONAL_CODES.isValid(personalCode)) {
      throw new IllegalStateException(
          "aml-service answered without a valid register requester personal code");
    }
    return personalCode;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DesignatedRequester(@Nullable String personalCode) {}
}
