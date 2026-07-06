package ee.tuleva.onboarding.kyc.survey;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import ee.tuleva.onboarding.country.ValidIso2CountryCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
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
  @JsonSubTypes.Type(
      value = KycSurveyResponseItem.PlannedContribution.class,
      name = "PLANNED_CONTRIBUTION"),
  @JsonSubTypes.Type(value = KycSurveyResponseItem.SourceOfIncome.class, name = "SOURCE_OF_INCOME"),
  @JsonSubTypes.Type(value = KycSurveyResponseItem.FundingSources.class, name = "FUNDING_SOURCES"),
  @JsonSubTypes.Type(value = KycSurveyResponseItem.Terms.class, name = "TERMS"),
})
public sealed interface KycSurveyResponseItem extends Serializable {

  record Citizenship(@NotNull @Valid CountriesValue value) implements KycSurveyResponseItem {}

  record Address(@NotNull @Valid AddressValue value) implements KycSurveyResponseItem {}

  record Email(@NotNull @Valid EmailValue value) implements KycSurveyResponseItem {}

  record PhoneNumber(@NotNull TextValue value) implements KycSurveyResponseItem {}

  record PepSelfDeclaration(@NotNull OptionValue<PepStatus> value)
      implements KycSurveyResponseItem {}

  record InvestmentGoals(@NotNull InvestmentGoalsValue value) implements KycSurveyResponseItem {}

  record InvestableAssets(@NotNull OptionValue<AssetRange> value)
      implements KycSurveyResponseItem {}

  record PlannedContribution(@NotNull OptionValue<PlannedContributionRange> value)
      implements KycSurveyResponseItem {}

  record SourceOfIncome(@NotNull List<@Valid SourceOfIncomeValueItem> value)
      implements KycSurveyResponseItem {}

  record FundingSources(@NotNull List<@Valid FundingSourceValueItem> value)
      implements KycSurveyResponseItem {}

  record Terms(@NotNull OptionValue<TermsAccepted> value) implements KycSurveyResponseItem {}

  // Value types
  record TextValue(String type, String value) implements Serializable {}

  record OptionValue<T>(String type, T value) implements Serializable {}

  record MultiOptionValue<T>(String type, List<T> value) implements Serializable {}

  record CountriesValue(String type, List<@ValidIso2CountryCode String> value)
      implements Serializable {}

  record EmailValue(String type, @NotBlank @jakarta.validation.constraints.Email String value)
      implements Serializable {}

  record AddressValue(String type, @NotNull @Valid AddressDetails value) implements Serializable {}

  record AddressDetails(
      @NotBlank String street,
      @NotBlank String city,
      @NotBlank String postalCode,
      @NotBlank @ValidIso2CountryCode String countryCode)
      implements Serializable {}

  // Union types for fields that can be either option or text ("Other")
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = InvestmentGoalsValue.Option.class, name = "OPTION"),
    @JsonSubTypes.Type(value = InvestmentGoalsValue.Text.class, name = "TEXT"),
  })
  sealed interface InvestmentGoalsValue extends Serializable {
    record Option(InvestmentGoal value) implements InvestmentGoalsValue {}

    record Text(String value) implements InvestmentGoalsValue {}
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SourceOfIncomeValueItem.Option.class, name = "OPTION"),
    @JsonSubTypes.Type(value = SourceOfIncomeValueItem.Text.class, name = "TEXT"),
  })
  sealed interface SourceOfIncomeValueItem extends Serializable {
    record Option(IncomeSource value) implements SourceOfIncomeValueItem {}

    record Text(String value) implements SourceOfIncomeValueItem {}
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = FundingSourceValueItem.Option.class, name = "OPTION"),
    @JsonSubTypes.Type(value = FundingSourceValueItem.Text.class, name = "TEXT"),
  })
  sealed interface FundingSourceValueItem extends Serializable {
    record Option(FundingSource value) implements FundingSourceValueItem {}

    record Text(String value) implements FundingSourceValueItem {}
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
    TRADING,
    EDUCATION,
    FIRST_HOME
  }

  enum AssetRange {
    LESS_THAN_20K,
    RANGE_20K_40K,
    RANGE_40K_80K,
    MORE_THAN_80K
  }

  enum PlannedContributionRange {
    UP_TO_50,
    FROM_50_TO_100,
    FROM_100_TO_300,
    OVER_300
  }

  enum IncomeSource {
    SALARY,
    SAVINGS,
    INVESTMENTS,
    PENSION_OR_BENEFITS,
    FAMILY_FUNDS_OR_INHERITANCE,
    BUSINESS_INCOME
  }

  enum FundingSource {
    PARENT_INCOME_AND_SAVINGS,
    GIFTS,
    INHERITANCE,
    CHILD_OWN
  }

  enum TermsAccepted {
    ACCEPTED
  }
}
