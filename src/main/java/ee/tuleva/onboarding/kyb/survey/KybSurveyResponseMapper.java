package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSource.*;

import ee.tuleva.onboarding.kyb.SelfCertification;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSourceItem;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanySourceOfIncome;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class KybSurveyResponseMapper {

  SelfCertification extractSelfCertification(KybSurveyResponse response) {
    var sources =
        response.answers().stream()
            .filter(CompanySourceOfIncome.class::isInstance)
            .map(CompanySourceOfIncome.class::cast)
            .findFirst()
            .map(
                item ->
                    item.value().stream()
                        .map(CompanyIncomeSourceItem.Option.class::cast)
                        .map(CompanyIncomeSourceItem.Option::value)
                        .collect(Collectors.toSet()))
            .orElse(Set.of());

    return new SelfCertification(
        sources.contains(ONLY_ACTIVE_IN_ESTONIA),
        sources.contains(NOT_SANCTIONED_NOT_PROFITING_FROM_SANCTIONED_COUNTRIES),
        sources.contains(NOT_IN_CRYPTO));
  }
}
