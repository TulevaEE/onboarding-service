package ee.tuleva.onboarding.kyc.survey;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import ee.tuleva.onboarding.country.ValidIso2CountryCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = KycSurveyResponseItem.Citizenship.class, name = "CITIZENSHIP"),
  @JsonSubTypes.Type(value = KycSurveyResponseItem.Address.class, name = "ADDRESS"),
  @JsonSubTypes.Type(value = KycSurveyResponseItem.Email.class, name = "EMAIL"),
  @JsonSubTypes.Type(value = KycSurveyResponseItem.PhoneNumber.class, name = "PHONE_NUMBER"),
  @JsonSubTypes.Type(
      value = KycSurveyResponseItem.PepSelfDeclaration.class,
      name = "PEP_SELF_DECLARATION"),
  @JsonSubTypes.Type(
      value = KycSurveyResponseItem.InvestmentGoals.class,
      name = "INVESTMENT_GOALS"),
  @JsonSubTypes.Type(
      value = KycSurveyResponseItem.InvestableAssets.class,
      name = "INVESTABLE_ASSETS"),
  @JsonSubTypes.Type(value = KycSurveyResponseItem.SourceOfIncome.class, name = "SOURCE_OF_INCOME"),
  @JsonSubTypes.Type(value = KycSurveyResponseItem.Terms.class, name = "TERMS"),
})
public sealed interface KycSurveyResponseItem {

  record Citizenship(@Valid CountriesValue value) implements KycSurveyResponseItem {}

  record Address(@Valid AddressValue value) implements KycSurveyResponseItem {}

  record Email(@Valid EmailValue value) implements KycSurveyResponseItem {}

  record PhoneNumber(TextValue value) implements KycSurveyResponseItem {}

  record PepSelfDeclaration(OptionValue<PepStatus> value) implements KycSurveyResponseItem {}

  record InvestmentGoals(InvestmentGoalsValue value) implements KycSurveyResponseItem {}

  record InvestableAssets(OptionValue<AssetRange> value) implements KycSurveyResponseItem {}

  record SourceOfIncome(List<@Valid SourceOfIncomeValueItem> value)
      implements KycSurveyResponseItem {}

  record Terms(OptionValue<TermsAccepted> value) implements KycSurveyResponseItem {}

  // Value types
  record TextValue(String type, String value) {}

  record OptionValue<T>(String type, T value) {}

  record MultiOptionValue<T>(String type, List<T> value) {}

  record CountriesValue(String type, List<@ValidIso2CountryCode String> value) {}

  record EmailValue(String type, @NotBlank @jakarta.validation.constraints.Email String value) {}

  record AddressValue(String type, @Valid AddressDetails value) {}

  record AddressDetails(
      @NotBlank String street,
      @NotBlank String city,
      @NotBlank String postalCode,
      @NotBlank @ValidIso2CountryCode String countryCode) {}

  // Union types for fields that can be either option or text ("Other")
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = InvestmentGoalsValue.Option.class, name = "OPTION"),
    @JsonSubTypes.Type(value = InvestmentGoalsValue.Text.class, name = "TEXT"),
  })
  sealed interface InvestmentGoalsValue {
    record Option(InvestmentGoal value) implements InvestmentGoalsValue {}

    record Text(String value) implements InvestmentGoalsValue {}
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SourceOfIncomeValueItem.Option.class, name = "OPTION"),
    @JsonSubTypes.Type(value = SourceOfIncomeValueItem.Text.class, name = "TEXT"),
  })
  sealed interface SourceOfIncomeValueItem {
    record Option(IncomeSource value) implements SourceOfIncomeValueItem {}

    record Text(String value) implements SourceOfIncomeValueItem {}
  }

  // Enums
  enum PepStatus {
    IS_PEP,
    IS_NOT_PEP
  }

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

  enum IncomeSource {
    SALARY,
    SAVINGS,
    INVESTMENTS,
    PENSION_OR_BENEFITS,
    FAMILY_FUNDS_OR_INHERITANCE,
    BUSINESS_INCOME
  }

  enum TermsAccepted {
    ACCEPTED
  }
}
