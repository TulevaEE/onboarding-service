package ee.tuleva.onboarding.instrument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentRetirement {

  private final InstrumentReferenceRepository instrumentReferenceRepository;

  @Transactional
  public boolean retire(String isin) {
    var deactivatedRows = instrumentReferenceRepository.deactivate(isin);
    if (deactivatedRows == 0) {
      log.info("Instrument was already retired, nothing to do: isin={}", isin);
      return false;
    }
    log.warn("Retired instrument, prices are no longer imported or checked: isin={}", isin);
    return true;
  }
}
