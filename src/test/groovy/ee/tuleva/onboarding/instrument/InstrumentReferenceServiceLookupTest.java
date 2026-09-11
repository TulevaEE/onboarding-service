package ee.tuleva.onboarding.instrument;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InstrumentReferenceServiceLookupTest {

  @Test
  void morningstarFundsAndSettlementTermsComeFromTheActiveSnapshot() {
    var morningstarFund =
        instrument("IE00FUND0002").morningstarId("F00000TEST").active(true).build();
    ReflectionTestUtils.setField(morningstarFund, "settlementCutoffTime", LocalTime.of(13, 0));
    ReflectionTestUtils.setField(morningstarFund, "settlementCutoffZone", "Europe/Dublin");
    ReflectionTestUtils.setField(morningstarFund, "settlementDaysFromAcceptance", 2);
    var inactiveMorningstarFund =
        instrument("IE00FUND0003").morningstarId("F00000GONE").active(false).build();

    var instrumentRepository = mock(InstrumentReferenceRepository.class);
    var proxyRepository = mock(BenchmarkCategoryProxyRepository.class);
    given(instrumentRepository.findAllByOrderByIdAsc())
        .willReturn(List.of(morningstarFund, inactiveMorningstarFund));
    given(proxyRepository.findAll()).willReturn(List.of());

    var service =
        new InstrumentReferenceService(
            new InstrumentSnapshotLoader(instrumentRepository, proxyRepository), Clock.systemUTC());
    service.init();

    assertThat(service.getMorningstarFunds()).containsExactly(morningstarFund);
    assertThat(service.settlementTerms("IE00FUND0002"))
        .contains(new SettlementTerms(LocalTime.of(13, 0), ZoneId.of("Europe/Dublin"), 2));
    assertThat(service.settlementTerms("IE00FUND0003")).isEmpty();
  }
}
