package ee.tuleva.onboarding.instrument;

import static java.time.ZoneOffset.UTC;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class InstrumentReferenceServiceFixture {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), UTC);

  private InstrumentReferenceServiceFixture() {}

  public static InstrumentReferenceService instrumentReferenceService(
      List<InstrumentReference> instruments, List<BenchmarkCategoryProxy> proxies) {
    var instrumentRepository = mock(InstrumentReferenceRepository.class);
    given(instrumentRepository.findAllByOrderByIdAsc()).willReturn(instruments);

    var proxyRepository = mock(BenchmarkCategoryProxyRepository.class);
    given(proxyRepository.findAll()).willReturn(proxies);

    var snapshotLoader = new InstrumentSnapshotLoader(instrumentRepository, proxyRepository);
    var service = new InstrumentReferenceService(snapshotLoader, CLOCK);
    service.init();
    return service;
  }
}
