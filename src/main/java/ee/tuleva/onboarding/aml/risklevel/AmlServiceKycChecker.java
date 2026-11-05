package ee.tuleva.onboarding.aml.risklevel;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import ee.tuleva.onboarding.kyc.KycChecker;
import ee.tuleva.onboarding.user.User;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
class AmlServiceKycChecker implements KycChecker {

  private final RestClient kycOnboardingRestClient;
  private final RetryTemplate amlServiceRetryTemplate;
  private final AmlServiceProperties properties;

  @Override
  public KycCheck check(User user) {
    if (isActualTransactionActive()) {
      throw new IllegalStateException(
          "KYC risk assessment asked inside a transaction, whose writes aml-service cannot read,"
              + " sending no request: userId="
              + user.getId());
    }
    String apiToken =
        properties
            .configuredApiToken()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "aml-service API token is not configured, sending no request: userId="
                            + user.getId()));
    return toKycCheck(user, amlServiceRetryTemplate.invoke(() -> assess(user, apiToken)));
  }

  private AssessmentResponse assess(User user, String apiToken) {
    ResponseEntity<AssessmentResponse> response =
        kycOnboardingRestClient
            .post()
            .uri("/v1/kyc-onboarding/assessments")
            .contentType(APPLICATION_JSON)
            .accept(APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(apiToken))
            .body(new AssessmentRequest(user.getPersonalCode()))
            .retrieve()
            .onStatus(
                status -> status.isSameCodeAs(NOT_FOUND),
                (request, notFound) -> {
                  throw new IllegalStateException(
                      "aml-service found no user or KYC survey for the KYC risk assessment, although the"
                          + " survey was committed: userId="
                          + user.getId());
                })
            .toEntity(AssessmentResponse.class);
    AssessmentResponse body = response.getBody();
    if (!response.getStatusCode().is2xxSuccessful() || body == null) {
      throw new IllegalStateException(
          "aml-service answered without a KYC risk assessment: userId="
              + user.getId()
              + ", status="
              + response.getStatusCode().value());
    }
    return body;
  }

  private static KycCheck toKycCheck(User user, AssessmentResponse response) {
    return new KycCheck(
        required(response.riskLevel(), user, "riskLevel"),
        required(response.metadata(), user, "metadata"));
  }

  private static <T> T required(@Nullable T value, User user, String field) {
    if (value == null) {
      throw new IllegalStateException(
          "aml-service answered without a required value: userId="
              + user.getId()
              + ", field="
              + field);
    }
    return value;
  }

  record AssessmentRequest(String personalCode) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record AssessmentResponse(
      @Nullable RiskLevel riskLevel, @Nullable Map<String, Object> metadata) {}
}
