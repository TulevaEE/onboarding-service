package ee.tuleva.onboarding.taxwin.consent;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofMillis;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.tuleva.onboarding.taxwin.ConsentAlreadyApproved;
import ee.tuleva.onboarding.taxwin.ConsentDocument;
import ee.tuleva.onboarding.taxwin.ConsentRejectedException;
import ee.tuleva.onboarding.taxwin.ConsentRequest;
import ee.tuleva.onboarding.taxwin.ConsentServiceClient;
import ee.tuleva.onboarding.taxwin.ConsentServiceUnavailableException;
import ee.tuleva.onboarding.taxwin.TaxHeadroomProperties;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
@Profile("!mock")
@RequiredArgsConstructor
public class RestConsentServiceClient implements ConsentServiceClient {

  private static final String CONSENT_DOCUMENT = "GENERATED_PDF";
  private static final String ALREADY_APPROVED =
      "ALL_REQUESTED_CONSENTS_HAVE_ALREADY_BEEN_APPROVED";
  private static final String APPROVED = "OK";
  private static final String PROCESSING_FAILED = "CONSENT_SIGNED_CONTAINER_PROCESSING_FAILED";
  private static final String NO_APPROVED_CONSENT = "HTTP_NOT_FOUND";
  private static final String DEFAULT_LANGUAGE = "et";
  private static final Set<String> LANGUAGES = Set.of(DEFAULT_LANGUAGE, "en", "ru");
  private static final Pattern ERROR_CODE = Pattern.compile("\\b[A-Z]+(?:_[A-Z]+)+\\b");

  private static final RetryTemplate RETRY_ONCE_WHEN_UNAVAILABLE =
      new RetryTemplate(
          RetryPolicy.builder()
              .includes(ConsentServiceUnavailableException.class, ResourceAccessException.class)
              .maxRetries(1)
              .delay(ofMillis(200))
              .build());

  private final RestClient consentServiceRestClient;
  private final TaxHeadroomProperties properties;

  @Override
  public ConsentRequest requestConsent(String personalCode, Locale locale) {
    var request =
        new ThirdPartyConsentRequest(
            personalCode, List.of(purposeDeclaration()), language(locale), CONSENT_DOCUMENT);
    return withRetry(
        () ->
            consentServiceRestClient
                .post()
                .uri("/api/consent/third-party")
                .body(request)
                .exchange(
                    (httpRequest, response) -> {
                      if (response.getStatusCode().isError()) {
                        @Nullable String errorCode = errorCode(response);
                        if (ALREADY_APPROVED.equals(errorCode)) {
                          return new ConsentAlreadyApproved();
                        }
                        throw failure("requestConsent", response, errorCode);
                      }
                      List<ThirdPartyConsent> consents =
                          Objects.requireNonNull(
                              response.bodyTo(new ParameterizedTypeReference<>() {}));
                      return consents.getFirst().document();
                    }));
  }

  @Override
  public void approve(String confirmReference, byte[] signedContainer) {
    var request =
        List.of(
            new SignedContainer(
                confirmReference, Base64.getEncoder().encodeToString(signedContainer)));
    List<ContainerResult> results =
        withoutRetry(
            () ->
                consentServiceRestClient
                    .post()
                    .uri("/api/consent/third-party/container")
                    .body(request)
                    .exchange(
                        (httpRequest, response) -> {
                          if (response.getStatusCode().isError()) {
                            throw failure("approve", response, errorCode(response));
                          }
                          return Objects.requireNonNull(
                              response.bodyTo(new ParameterizedTypeReference<>() {}));
                        }));
    results.stream()
        .filter(result -> !APPROVED.equals(result.status()))
        .findFirst()
        .ifPresent(
            result -> {
              String message =
                  "Consent service did not approve the signed consent: errorCode="
                      + result.errorCode();
              if (PROCESSING_FAILED.equals(result.errorCode())) {
                throw new ConsentServiceUnavailableException(message);
              }
              throw new ConsentRejectedException(message);
            });
  }

  @Override
  public Optional<String> findReference(String personalCode) {
    String purposeDeclaration = purposeDeclaration();
    var request = new ConsentReferenceRequest(personalCode, List.of(purposeDeclaration));
    return withRetry(
        () ->
            consentServiceRestClient
                .post()
                .uri("/api/consent/reference")
                .body(request)
                .exchange(
                    (httpRequest, response) -> {
                      if (response.getStatusCode().isError()) {
                        String body = body(response);
                        if (response.getStatusCode().isSameCodeAs(NOT_FOUND)
                            && body.contains(NO_APPROVED_CONSENT)) {
                          return Optional.<String>empty();
                        }
                        throw new ConsentServiceUnavailableException(
                            "Consent service could not look up the consent reference: status="
                                + response.getStatusCode().value()
                                + ", errorCode="
                                + errorCode(body));
                      }
                      Map<String, String> references =
                          Objects.requireNonNull(
                              response.bodyTo(new ParameterizedTypeReference<>() {}));
                      return Optional.ofNullable(references.get(purposeDeclaration));
                    }));
  }

  private String purposeDeclaration() {
    String purposeDeclaration = properties.purposeDeclarationId();
    if (purposeDeclaration == null) {
      throw new ConsentServiceUnavailableException(
          "Tax data purpose declaration is not configured");
    }
    return purposeDeclaration;
  }

  private static String language(Locale locale) {
    return LANGUAGES.contains(locale.getLanguage()) ? locale.getLanguage() : DEFAULT_LANGUAGE;
  }

  private <T> T withRetry(Supplier<T> call) {
    return withoutRetry(() -> RETRY_ONCE_WHEN_UNAVAILABLE.invoke(call));
  }

  private static <T> T withoutRetry(Supplier<T> call) {
    try {
      return call.get();
    } catch (ResourceAccessException e) {
      throw new ConsentServiceUnavailableException("Consent service is unreachable", e);
    }
  }

  private static RuntimeException failure(
      String operation, ClientHttpResponse response, @Nullable String errorCode)
      throws IOException {
    String message =
        "Consent service request failed: operation="
            + operation
            + ", status="
            + response.getStatusCode().value()
            + ", errorCode="
            + errorCode;
    return response.getStatusCode().is5xxServerError()
        ? new ConsentServiceUnavailableException(message)
        : new ConsentRejectedException(message);
  }

  private static @Nullable String errorCode(ClientHttpResponse response) throws IOException {
    return errorCode(body(response));
  }

  private static @Nullable String errorCode(String body) {
    var matcher = ERROR_CODE.matcher(body);
    return matcher.find() ? matcher.group() : null;
  }

  private static String body(ClientHttpResponse response) throws IOException {
    return new String(response.getBody().readAllBytes(), UTF_8);
  }

  record ThirdPartyConsentRequest(
      String idCode,
      List<String> purposeDeclarationBusinessIdentifiers,
      String language,
      String fileType) {}

  record ConsentReferenceRequest(
      String idCode, List<String> purposeDeclarationBusinessIdentifiers) {}

  record SignedContainer(String consentConfirmReference, String file) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ThirdPartyConsent(String consentConfirmReference, List<ConsentFile> files) {

    ConsentDocument document() {
      return files.stream()
          .filter(file -> CONSENT_DOCUMENT.equals(file.fileType()))
          .findFirst()
          .map(
              file ->
                  new ConsentDocument(
                      consentConfirmReference, Base64.getMimeDecoder().decode(file.fileContent())))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Consent service returned no consent document: consentConfirmReference="
                          + consentConfirmReference));
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ConsentFile(String fileType, String fileContent) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ContainerResult(
      String consentConfirmReference, String status, @Nullable String errorCode) {}
}
