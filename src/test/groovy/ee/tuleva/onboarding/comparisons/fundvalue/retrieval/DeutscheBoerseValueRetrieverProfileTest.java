package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class DeutscheBoerseValueRetrieverProfileTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(DeutscheBoerseValueRetriever.class)
          .withBean(RestClient.Builder.class, RestClient::builder)
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(InstrumentReferenceService.class, () -> mock(InstrumentReferenceService.class))
          .withBean(FundValueRepository.class, () -> mock(FundValueRepository.class))
          .withInitializer(
              context ->
                  context
                      .getBeanFactory()
                      .setConversionService(ApplicationConversionService.getSharedInstance()));

  @Test
  void stagingDoesNotDownloadFromDeutscheBoerse() {
    contextRunner
        .withPropertyValues("spring.profiles.active=staging")
        .run(context -> assertThat(context).doesNotHaveBean(DeutscheBoerseValueRetriever.class));
  }

  @Test
  void productionDownloadsFromDeutscheBoerse() {
    contextRunner
        .withPropertyValues("spring.profiles.active=production")
        .run(context -> assertThat(context).hasSingleBean(DeutscheBoerseValueRetriever.class));
  }
}
