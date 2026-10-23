package ee.tuleva.onboarding.instrument;

import static java.time.ZoneOffset.UTC;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class InstrumentReferenceServiceFixture {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), UTC);

  private InstrumentReferenceServiceFixture() {}

  public record PastProxy(String holdingIsin, String proxyIsin, LocalDate validUntil) {}

  public static InstrumentReferenceService instrumentReferenceService(
      List<InstrumentReference> instruments, List<BenchmarkCategoryProxy> proxies) {
    return instrumentReferenceService(instruments, proxies, List.of());
  }

  public static InstrumentReferenceService instrumentReferenceService(
      List<InstrumentReference> instruments,
      List<BenchmarkCategoryProxy> proxies,
      List<PastProxy> pastProxies) {
    var instrumentRepository = mock(InstrumentReferenceRepository.class);
    given(instrumentRepository.findAllByOrderByIdAsc()).willReturn(instruments);

    var proxyRepository = mock(BenchmarkCategoryProxyRepository.class);
    given(proxyRepository.findAll()).willReturn(proxies);

    var periodRepository = mock(BenchmarkProxyPeriodRepository.class);
    given(periodRepository.findAllOrderedByValidUntil())
        .willReturn(
            pastProxies.stream()
                .map(
                    past ->
                        new BenchmarkProxyPeriod(
                            past.holdingIsin(), past.proxyIsin(), past.validUntil()))
                .toList());

    var snapshotLoader =
        new InstrumentSnapshotLoader(instrumentRepository, proxyRepository, periodRepository);
    var service = new InstrumentReferenceService(snapshotLoader, CLOCK);
    service.init();
    return service;
  }
}
