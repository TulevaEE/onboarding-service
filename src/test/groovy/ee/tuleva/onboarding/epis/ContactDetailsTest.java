package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.epis.ContactDetails.ContributionStatus.CONTRIBUTING;
import static ee.tuleva.onboarding.epis.ContactDetails.ContributionStatus.NOT_CONTRIBUTING;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.config.ObjectMapperConfiguration;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.ContactDetails.ContributionPeriod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class ContactDetailsTest {

  @Test
  void setAddress_returnsThisForChainingAndUpdatesCountry() {
    ContactDetails contactDetails = ContactDetails.builder().build();
    Country address = Country.builder().countryCode("EE").build();

    ContactDetails result = contactDetails.setAddress(address);

    assertThat(result).isSameAs(contactDetails);
    assertThat(contactDetails.getAddress()).isEqualTo(address);
  }

  @Test
  void hasPensionAccountOnceTheRegistryHasGivenItANumber() {
    ContactDetails contactDetails =
        ContactDetails.builder().pensionAccountNumber("993432432").build();

    assertThat(contactDetails.hasPensionAccount()).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void hasNoPensionAccountWithoutANumber(String pensionAccountNumber) {
    ContactDetails contactDetails =
        ContactDetails.builder().pensionAccountNumber(pensionAccountNumber).build();

    assertThat(contactDetails.hasPensionAccount()).isFalse();
  }

  @Test
  void readsTheSecondPillarContributionPeriodsTheRegistryReports() {
    String json =
        """
        {"secondPillarContributionPeriods": [
          {"status": "CONTRIBUTING", "startDate": "2002-07-01", "endDate": "2011-12-31"},
          {"status": "NOT_CONTRIBUTING", "startDate": "2012-01-01", "endDate": null}
        ]}
        """;

    ContactDetails contactDetails = readAsTheEpisClientDoes(json);

    assertThat(contactDetails.getSecondPillarContributionPeriods())
        .isEqualTo(
            List.of(
                new ContributionPeriod(
                    CONTRIBUTING, LocalDate.parse("2002-07-01"), LocalDate.parse("2011-12-31")),
                new ContributionPeriod(NOT_CONTRIBUTING, LocalDate.parse("2012-01-01"), null)));
  }

  @Test
  void leavesTheSecondPillarContributionPeriodsOutOfARegistryResponseWithoutThem() {
    ContactDetails contactDetails = readAsTheEpisClientDoes("{\"country\": \"EE\"}");

    assertThat(contactDetails.getSecondPillarContributionPeriods()).isNull();
  }

  private static ContactDetails readAsTheEpisClientDoes(String json) {
    JsonMapper.Builder builder = JsonMapper.builder();
    new ObjectMapperConfiguration().customizeObjectMapper().customize(builder);
    return builder.build().readValue(json, ContactDetails.class);
  }
}
