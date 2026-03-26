package ee.tuleva.onboarding.kyb.survey;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = KybSurveyResponseItem.BusinessRegistryNumber.class,
      name = "BUSINESS_REGISTRY_NUMBER"),
  @JsonSubTypes.Type(value = KybSurveyResponseItem.CompanyAddress.class, name = "COMPANY_ADDRESS"),
  @JsonSubTypes.Type(
      value = KybSurveyResponseItem.InvestmentGoals.class,
      name = "INVESTMENT_GOALS"),
  @JsonSubTypes.Type(
      value = KybSurveyResponseItem.InvestableAssets.class,
      name = "INVESTABLE_ASSETS"),
  @JsonSubTypes.Type(
      value = KybSurveyResponseItem.CompanySourceOfIncome.class,
      name = "COMPANY_SOURCE_OF_INCOME"),
})
sealed interface KybSurveyResponseItem extends Serializable {

  record BusinessRegistryNumber(TextValue value) implements KybSurveyResponseItem {}

  record CompanyAddress(@Valid AddressValue value) implements KybSurveyResponseItem {}

  record InvestmentGoals(OptionValue<InvestmentGoal> value) implements KybSurveyResponseItem {}

  record InvestableAssets(OptionValue<AssetRange> value) implements KybSurveyResponseItem {}

  record CompanySourceOfIncome(List<@Valid CompanyIncomeSourceItem> value)
      implements KybSurveyResponseItem {}

  // Value types
  record TextValue(String type, String value) implements Serializable {}

  record OptionValue<T>(String type, T value) implements Serializable {}

  record AddressValue(String type, @Valid AddressDetails value) implements Serializable {}

  record AddressDetails(
      @NotBlank String countryCode,
      @NotBlank String street,
      @NotBlank String city,
      @NotBlank String postalCode)
      implements Serializable {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = CompanyIncomeSourceItem.Option.class, name = "OPTION"),
  })
  sealed interface CompanyIncomeSourceItem extends Serializable {
    record Option(CompanyIncomeSource value) implements CompanyIncomeSourceItem {}
  }

  // Enums
  enum InvestmentGoal {
    LONG_TERM,
    SPECIFIC_GOAL,
    CHILD,
    TRADING
  }

  enum AssetRange {
    LESS_THAN_20K,
    RANGE_20K_40K,
    RANGE_40K_80K,
    MORE_THAN_80K
  }

  enum CompanyIncomeSource {
    ONLY_ACTIVE_IN_ESTONIA,
    NOT_SANCTIONED_NOT_PROFITING_FROM_SANCTIONED_COUNTRIES,
    NOT_IN_CRYPTO
  }
}
