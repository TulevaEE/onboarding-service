package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({
  OcfPublicationService.class,
  OcfPublicationRepository.class,
  OcfCalculationVersions.class,
  OcfExceptionRepository.class,
  OcfSnapshotRepository.class,
  FeeRateRepository.class
})
public class OcfPublicationReadConfiguration {}
