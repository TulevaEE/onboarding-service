package ee.tuleva.onboarding.aml.risklevel;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.tuleva.onboarding.aml.OnboardingReview;
import ee.tuleva.onboarding.aml.OnboardingReview.Outcome;
import ee.tuleva.onboarding.aml.OnboardingReview.PartyType;
import ee.tuleva.onboarding.aml.OnboardingReviews;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;

@Slf4j
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
    List<OnboardingReview> usable =
        answer.stream().flatMap(review -> usable(review).stream()).toList();
    if (usable.size() < answer.size()) {
      log.error(
          "aml-service answered settled onboarding reviews without a code or a known party type"
              + " or outcome,"
              + " leaving them out: unusable={}, usable={}",
          answer.size() - usable.size(),
          usable.size());
    }
    return usable;
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

  private static Optional<OnboardingReview> usable(@Nullable SettledReview review) {
    if (review == null || review.code() == null) {
      return Optional.empty();
    }
    String code = review.code();
    Optional<PartyType> partyType = named(PartyType.values(), review.type());
    return named(Outcome.values(), review.outcome())
        .flatMap(outcome -> partyType.map(type -> new OnboardingReview(code, type, outcome)));
  }

  private static <E extends Enum<E>> Optional<E> named(E[] values, @Nullable String name) {
    return Arrays.stream(values).filter(value -> value.name().equals(name)).findFirst();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SettledReview(@Nullable String code, @Nullable String type, @Nullable String outcome) {}
}
