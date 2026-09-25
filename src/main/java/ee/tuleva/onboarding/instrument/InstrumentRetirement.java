package ee.tuleva.onboarding.instrument;

import ee.tuleva.onboarding.instrument.InstrumentRetirementOutcome.Refusal;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentRetirement {

  private final InstrumentReferenceRepository instrumentReferenceRepository;
  private final InstrumentReferenceService instrumentReferenceService;

  public InstrumentRetirementOutcome retire(Collection<String> isins) {
    var attempts = isins.stream().distinct().flatMap(this::attempt).toList();
    var retiredIsins = only(Retired.class, attempts).map(Retired::isin).toList();
    var refusals = only(Refused.class, attempts).map(Refused::refusal).toList();
    var cacheReloadedOnThisInstance =
        !retiredIsins.isEmpty() && instrumentReferenceService.refresh();
    return new InstrumentRetirementOutcome(retiredIsins, refusals, cacheReloadedOnThisInstance);
  }

  private Stream<Attempt> attempt(String isin) {
    try {
      if (instrumentReferenceRepository.deactivateInItsOwnTransaction(isin) == 0) {
        log.info("Instrument was already retired, nothing to do: isin={}", isin);
        return Stream.empty();
      }
      log.warn("Retired instrument, prices are no longer imported or checked: isin={}", isin);
      return Stream.of(new Retired(isin));
    } catch (RuntimeException e) {
      log.error("Failed to retire instrument: isin={}", isin, e);
      return Stream.of(new Refused(new Refusal(isin, String.valueOf(e.getMessage()))));
    }
  }

  private static <T extends Attempt> Stream<T> only(Class<T> kind, List<Attempt> attempts) {
    return attempts.stream().filter(kind::isInstance).map(kind::cast);
  }

  private sealed interface Attempt permits Retired, Refused {}

  private record Retired(String isin) implements Attempt {}

  private record Refused(Refusal refusal) implements Attempt {}
}
