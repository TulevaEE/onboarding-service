package ee.tuleva.onboarding.aml.risklevel;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.tuleva.onboarding.aml.OnboardingReview;
import ee.tuleva.onboarding.aml.OnboardingReview.Outcome;
import ee.tuleva.onboarding.aml.OnboardingReviews;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
class AmlServiceOnboardingReviews implements OnboardingReviews {

  private static final ParameterizedTypeReference<List<@Nullable SettledReview>> SETTLED =
      new ParameterizedTypeReference<>() {};

  private final RestClient amlServiceRestClient;
  private final RetryTemplate amlServiceRetryTemplate;
  private final AmlServiceProperties properties;

  @Override
  public List<OnboardingReview> settled() {
    String apiToken =
        properties
            .configuredApiToken()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "aml-service API token is not configured, sending no request for the"
                            + " settled onboarding reviews"));
    List<@Nullable SettledReview> answer = amlServiceRetryTemplate.invoke(() -> fetch(apiToken));
    if (answer == null) {
      throw new IllegalStateException("aml-service answered without settled onboarding reviews");
    }
    return IntStream.range(0, answer.size())
        .mapToObj(index -> toReview(index, answer.get(index)))
        .toList();
  }

  private @Nullable List<@Nullable SettledReview> fetch(String apiToken) {
    return amlServiceRestClient
        .get()
        .uri("/v1/tkf-onboarding/outcomes")
        .accept(APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(apiToken))
        .retrieve()
        .body(SETTLED);
  }

  private static OnboardingReview toReview(int index, @Nullable SettledReview review) {
    String field = "[" + index + "]";
    SettledReview present = required(review, field);
    return new OnboardingReview(
        required(present.personalCode(), field + ".personalCode"),
        required(present.outcome(), field + ".outcome"));
  }

  private static <T> T required(@Nullable T value, String field) {
    if (value == null) {
      throw new IllegalStateException(
          "aml-service answered a settled onboarding review without a required value: field="
              + field);
    }
    return value;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SettledReview(@Nullable String personalCode, @Nullable Outcome outcome) {}
}
