package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressPageReader;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;

class InvestmentReportPublishingPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              InvestmentReportPublisher.class,
              InvestmentReportPublishingLock.class,
              NewerReportCheck.class)
          .withBean(
              InvestmentReportDataService.class, () -> mock(InvestmentReportDataService.class))
          .withBean(
              InvestmentReportPdfGenerator.class, () -> mock(InvestmentReportPdfGenerator.class))
          .withBean(WordPressMediaClient.class, () -> mock(WordPressMediaClient.class))
          .withBean(WordPressPageReader.class, () -> mock(WordPressPageReader.class))
          .withBean(EmailService.class, () -> mock(EmailService.class))
          .withBean(LockProvider.class, () -> mock(ExtensibleLockProvider.class))
          .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
          .withBean(Clock.class, Clock::systemUTC);

  @Test
  void productionPublishesThePensionFundsReportsAndNotTkf100sYet() throws IOException {
    var production =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml")).stream()
                .filter(
                    document ->
                        "production"
                            .equals(document.getProperty("spring.config.activate.on-profile[0]")))
                .filter(document -> document.containsProperty("investment-report-publishing.funds"))
                .findFirst()
                .orElseThrow();

    var properties =
        new Binder(ConfigurationPropertySources.from(production))
            .bind("investment-report-publishing", InvestmentReportPublishingProperties.class)
            .get();

    assertThat(properties.funds()).containsExactly(TUK75, TUK00, TUV100);
  }

  @Test
  void anEnabledPublisherPublishesTheConfiguredFunds() {
    contextRunner
        .withPropertyValues(
            "investment-report-publishing.enabled=true",
            "investment-report-publishing.funds=TUK75, TUK00")
        .run(
            context -> {
              assertThat(context).hasSingleBean(InvestmentReportPublisher.class);
              assertThat(context.getBean(InvestmentReportPublishingProperties.class).funds())
                  .containsExactly(TUK75, TUK00);
            });
  }

  @Test
  void anEnabledPublisherWithNoFundsConfiguredFailsAtStartupRatherThanPublishingNothing() {
    contextRunner
        .withPropertyValues("investment-report-publishing.enabled=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void aDisabledPublisherNeedsNoFunds() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(InvestmentReportPublisher.class);
          assertThat(context).doesNotHaveBean(InvestmentReportPublishingProperties.class);
        });
  }

  @Test
  void theConfiguredFundsCannotBeChangedAfterBinding() {
    var funds = new ArrayList<>(List.of(TUK75));

    var properties = new InvestmentReportPublishingProperties(funds);
    funds.add(TUK00);

    assertThat(properties.funds()).containsExactly(TUK75);
  }
}
