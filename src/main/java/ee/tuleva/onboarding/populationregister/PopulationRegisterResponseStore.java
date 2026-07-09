package ee.tuleva.onboarding.populationregister;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class PopulationRegisterResponseStore {

  private final PopulationRegisterResponseRepository repository;
  private final Clock clock;

  Optional<List<Map<String, Object>>> findFresh(
      String personalCode, PopulationRegisterQueryType queryType, Duration maxAge) {
    if (maxAge.isZero() || maxAge.isNegative()) {
      return Optional.empty();
    }
    return repository
        .findLatestSince(personalCode, queryType, clock.instant().minus(maxAge))
        .flatMap(stored -> Optional.ofNullable(stored.getResponse()));
  }

  @Transactional(propagation = REQUIRES_NEW)
  void save(
      String personalCode,
      PopulationRegisterQueryType queryType,
      UUID messageId,
      List<Map<String, Object>> response) {
    repository.save(
        PopulationRegisterResponse.builder()
            .personalCode(personalCode)
            .queryType(queryType)
            .messageId(messageId)
            .response(response)
            .createdAt(clock.instant())
            .build());
  }

  @Transactional
  int eraseResponsesOlderThan(Duration retention) {
    return repository.eraseResponsesOlderThan(clock.instant().minus(retention));
  }
}
