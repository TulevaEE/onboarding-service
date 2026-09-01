package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.user.User;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionAndPepScreener {

  // The party module records these on its custody checks; screening reads them for parties whose
  // KYC survey carries no citizenship of its own.
  private static final String RECORDED_CITIZENSHIP = "citizenship";
  private static final String RECORDED_CITIZENSHIPS = "citizenships";

  private final AmlCheckRepository amlCheckRepository;
  private final PepAndSanctionCheckService pepAndSanctionCheckService;
  private final KycCountryService kycCountryService;
  private final JsonMapper jsonMapper;
  private final MeterRegistry meterRegistry;
  private final AmlService amlService;

  public List<AmlCheck> addSanctionAndPepCheckIfMissing(Person person, Set<Country> countries) {
    return screenForSanctionAndPep(person, countries).checks();
  }

  public List<AmlCheck> addSanctionAndPepCheckIfMissing(User user) {
    return addSanctionAndPepCheckIfMissing(user, knownCountries(user));
  }

  Set<Country> knownCountries(User user) {
    return Stream.concat(
            kycCountryService.getCountries(user.getIdOrThrow()).orElseGet(Set::of).stream(),
            recordedCitizenships(user).stream())
        .collect(toUnmodifiableSet());
  }

  public Set<Country> recordedCitizenships(Person person) {
    return amlCheckRepository
        .findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
            person.getPersonalCode(), CUSTODY_RIGHT)
        .map(AmlCheck::getMetadata)
        .map(SanctionAndPepScreener::citizenshipsFrom)
        .orElseGet(Set::of);
  }

  private static Set<Country> citizenshipsFrom(Map<String, Object> metadata) {
    if (metadata.get(RECORDED_CITIZENSHIPS) instanceof Collection<?> recorded) {
      return Countries.of(
          recorded.stream().filter(String.class::isInstance).map(String.class::cast).toList());
    }
    if (metadata.get(RECORDED_CITIZENSHIP) instanceof String citizenship) {
      return Countries.of(citizenship);
    }
    return Set.of();
  }

  public boolean isSanctionAndPepClear(Person person, Set<Country> countries) {
    if (screenForSanctionAndPep(person, countries).failed()) {
      return false;
    }
    return latestCheckPassed(person, SANCTION)
        && latestCheckPassed(person, POLITICALLY_EXPOSED_PERSON_AUTO);
  }

  private boolean latestCheckPassed(Person person, AmlCheckType type) {
    return amlCheckRepository
        .findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(person.getPersonalCode(), type)
        .map(AmlCheck::isSuccess)
        .orElse(false);
  }

  ScreeningResult screenForSanctionAndPep(Person person, Set<Country> countries) {
    MatchResponse response;
    try {
      response = pepAndSanctionCheckService.match(person, countries);
    } catch (RuntimeException e) {
      handleScreeningFailure(person, "match", e);
      return new ScreeningResult(List.of(), true);
    }

    Optional<AmlCheck> pepCheck = addPepCheckIfMissing(person, response);
    Optional<AmlCheck> sanctionCheck = addSanctionCheckIfMissing(person, response);

    List<AmlCheck> checks = Stream.of(pepCheck, sanctionCheck).flatMap(Optional::stream).toList();
    return new ScreeningResult(checks, false);
  }

  void handleScreeningFailure(Person person, String phase, RuntimeException e) {
    log.error(
        "Sanction/PEP screening failed for personalCode={} during phase={}",
        person.getPersonalCode(),
        phase,
        e);
    meterRegistry.counter("aml.screening.failure", "phase", phase).increment();
  }

  private Optional<AmlCheck> addSanctionCheckIfMissing(Person person, MatchResponse response) {
    AmlCheck sanctionCheck =
        AmlCheck.builder()
            .personalCode(person.getPersonalCode())
            .type(SANCTION)
            .success(isSuccess(person, SANCTION_OVERRIDE, response, "sanction"))
            .metadata(metadata(response.results(), response.query()))
            .build();
    return addScreeningCheck(sanctionCheck);
  }

  private Optional<AmlCheck> addPepCheckIfMissing(Person person, MatchResponse response) {
    AmlCheck pepCheck =
        AmlCheck.builder()
            .personalCode(person.getPersonalCode())
            .type(POLITICALLY_EXPOSED_PERSON_AUTO)
            .success(isSuccess(person, POLITICALLY_EXPOSED_PERSON_OVERRIDE, response, "role"))
            .metadata(metadata(response.results(), response.query()))
            .build();
    return addScreeningCheck(pepCheck);
  }

  private Optional<AmlCheck> addScreeningCheck(AmlCheck screeningCheck) {
    if (amlService.hasCheck(screeningCheck.getPersonalCode(), screeningCheck.getType())
        && outcomeUnchanged(screeningCheck)) {
      return Optional.empty();
    }
    return Optional.of(amlService.addCheck(screeningCheck));
  }

  private boolean outcomeUnchanged(AmlCheck screeningCheck) {
    return amlCheckRepository
        .findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
            screeningCheck.getPersonalCode(), screeningCheck.getType())
        .map(latest -> latest.isSuccess() == screeningCheck.isSuccess())
        .orElse(false);
  }

  private boolean isSuccess(
      Person person, AmlCheckType overrideType, MatchResponse response, String topic) {
    boolean hasMatch = hasMatch(response.results(), topic);
    boolean hasSuccessOverride = hasSuccessOverride(person, overrideType, response.results());
    return !hasMatch || hasSuccessOverride;
  }

  private boolean hasSuccessOverride(
      Person person, AmlCheckType overrideType, Iterable<JsonNode> results) {
    List<String> ids =
        stream(results)
            .filter(result -> result.hasNonNull("id"))
            .map(result -> result.get("id").asString())
            .toList();
    List<AmlCheck> successOverrides =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            person.getPersonalCode(), overrideType, true);
    return successOverrides.stream().anyMatch(override -> overrideApplies(override, ids));
  }

  private boolean overrideApplies(AmlCheck override, List<String> matchedIds) {
    Object overrideResults = override.getMetadata().get("results");
    if (!(overrideResults instanceof Iterable<?> results)) {
      return true;
    }
    return stream(results)
        .anyMatch(result -> result instanceof Map<?, ?> map && matchedIds.contains(map.get("id")));
  }

  private Map<String, Object> metadata(JsonNode results, JsonNode query) {
    return Map.of(
        "results", jsonMapper.convertValue(results, Object.class),
        "query", jsonMapper.convertValue(query, Object.class));
  }

  private boolean hasMatch(Iterable<JsonNode> results, String topicNameStartsWith) {
    List<JsonNode> hits =
        stream(results)
            .filter(
                result ->
                    stream(result.get("properties").get("topics"))
                            .anyMatch(topic -> topic.asString().startsWith(topicNameStartsWith))
                        && result.get("match").asBoolean())
            .toList();
    hits.forEach(
        hit ->
            log.info(
                "AML screening hit: topic={}, caption={}, score={}, id={}",
                topicNameStartsWith,
                hit.path("caption").asString(null),
                hit.path("score").isMissingNode() ? null : hit.path("score").asDouble(),
                hit.path("id").asString(null)));
    return !hits.isEmpty();
  }

  private <T> Stream<T> stream(Iterable<T> iterable) {
    return StreamSupport.stream(iterable.spliterator(), false);
  }
}
