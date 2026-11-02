package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class InvestmentReportPublishingPropertiesTest {

  @Test
  void productionPublishesThePensionFundsReportsAndNotTkf100sYet() throws IOException {
    var production =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml")).stream()
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
  void publishingWithNoFundsConfiguredFailsAtStartupRatherThanPublishingNothing() {
    var binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of("investment-report-publishing.enabled", "true")));

    assertThatThrownBy(
            () ->
                binder.bindOrCreate(
                    "investment-report-publishing", InvestmentReportPublishingProperties.class))
        .isInstanceOf(BindException.class);
  }

  @Test
  void theConfiguredFundsCannotBeChangedAfterBinding() {
    var funds = new ArrayList<>(List.of(TUK75));

    var properties = new InvestmentReportPublishingProperties(funds);
    funds.add(TUK00);

    assertThat(properties.funds()).containsExactly(TUK75);
  }
}
