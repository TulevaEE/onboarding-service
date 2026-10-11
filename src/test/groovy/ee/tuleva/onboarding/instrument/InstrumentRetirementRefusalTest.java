package ee.tuleva.onboarding.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.instrument.InstrumentRetirementOutcome.Refusal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstrumentRetirementRefusalTest {

  private static final String REFUSED_ISIN = "IE00REFUSED1";
  private static final String RETIRING_ISIN = "IE00RETIRE01";

  private final InstrumentReferenceRepository repository =
      mock(InstrumentReferenceRepository.class);
  private final InstrumentReferenceService instrumentReferenceService =
      mock(InstrumentReferenceService.class);
  private final InstrumentRetirement instrumentRetirement =
      new InstrumentRetirement(repository, instrumentReferenceService);

  @Test
  void keepsRetiringTheOthersAndReloadsTheCacheOnceWhenOneIsRefused() {
    willThrow(new IllegalStateException())
        .given(repository)
        .deactivateInItsOwnTransaction(REFUSED_ISIN);
    given(repository.deactivateInItsOwnTransaction(RETIRING_ISIN)).willReturn(1);
    given(instrumentReferenceService.refresh()).willReturn(true);

    var outcome = instrumentRetirement.retire(List.of(REFUSED_ISIN, RETIRING_ISIN));

    assertThat(outcome)
        .isEqualTo(
            new InstrumentRetirementOutcome(
                List.of(RETIRING_ISIN),
                List.of(new Refusal(REFUSED_ISIN, "IllegalStateException")),
                true));
    verify(instrumentReferenceService).refresh();
  }

  @Test
  void namesTheExceptionTypeWhenTheRefusalCarriesNoMessage() {
    willThrow(new IllegalStateException())
        .given(repository)
        .deactivateInItsOwnTransaction(REFUSED_ISIN);

    var outcome = instrumentRetirement.retire(List.of(REFUSED_ISIN));

    assertThat(outcome)
        .isEqualTo(
            new InstrumentRetirementOutcome(
                List.of(), List.of(new Refusal(REFUSED_ISIN, "IllegalStateException")), false));
  }
}
