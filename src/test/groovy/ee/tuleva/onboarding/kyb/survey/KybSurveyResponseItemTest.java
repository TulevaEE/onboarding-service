package ee.tuleva.onboarding.kyb.survey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KybSurveyResponseItemTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializesFullSurveyResponse() throws Exception {
    var json =
        """
        {
          "answers": [
            { "type": "BUSINESS_REGISTRY_NUMBER", "value": { "type": "TEXT", "value": "12345678" } },
            { "type": "COMPANY_ADDRESS", "value": { "type": "ADDRESS", "value": { "countryCode": "EE", "street": "Paju 2", "city": "Tartu linn", "postalCode": "50104" } } },
            { "type": "INVESTMENT_GOALS", "value": { "type": "OPTION", "value": "LONG_TERM" } },
            { "type": "INVESTABLE_ASSETS", "value": { "type": "OPTION", "value": "MORE_THAN_80K" } },
            { "type": "COMPANY_SOURCE_OF_INCOME", "value": [{ "type": "OPTION", "value": "ONLY_ACTIVE_IN_ESTONIA" }, { "type": "OPTION", "value": "NOT_IN_CRYPTO" }] }
          ]
        }
        """;

    var response = objectMapper.readValue(json, KybSurveyResponse.class);

    assertThat(response.answers()).hasSize(5);
    assertThat(response.answers().get(0))
        .isInstanceOf(KybSurveyResponseItem.BusinessRegistryNumber.class);
    assertThat(response.answers().get(1)).isInstanceOf(KybSurveyResponseItem.CompanyAddress.class);
    assertThat(response.answers().get(2)).isInstanceOf(KybSurveyResponseItem.InvestmentGoals.class);
    assertThat(response.answers().get(3))
        .isInstanceOf(KybSurveyResponseItem.InvestableAssets.class);
    assertThat(response.answers().get(4))
        .isInstanceOf(KybSurveyResponseItem.CompanySourceOfIncome.class);

    var registryNumber = (KybSurveyResponseItem.BusinessRegistryNumber) response.answers().get(0);
    assertThat(registryNumber.value().value()).isEqualTo("12345678");

    var address = (KybSurveyResponseItem.CompanyAddress) response.answers().get(1);
    assertThat(address.value().value().countryCode()).isEqualTo("EE");
    assertThat(address.value().value().street()).isEqualTo("Paju 2");
    assertThat(address.value().value().city()).isEqualTo("Tartu linn");
    assertThat(address.value().value().postalCode()).isEqualTo("50104");

    var goals = (KybSurveyResponseItem.InvestmentGoals) response.answers().get(2);
    assertThat(goals.value().value()).isEqualTo(KybSurveyResponseItem.InvestmentGoal.LONG_TERM);

    var assets = (KybSurveyResponseItem.InvestableAssets) response.answers().get(3);
    assertThat(assets.value().value()).isEqualTo(KybSurveyResponseItem.AssetRange.MORE_THAN_80K);

    var sourceOfIncome = (KybSurveyResponseItem.CompanySourceOfIncome) response.answers().get(4);
    assertThat(sourceOfIncome.value()).hasSize(2);
  }
}
