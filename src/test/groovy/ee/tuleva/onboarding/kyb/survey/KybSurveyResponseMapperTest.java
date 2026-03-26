package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSource.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.SelfCertification;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSourceItem;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanySourceOfIncome;
import java.util.List;
import org.junit.jupiter.api.Test;

class KybSurveyResponseMapperTest {

  private final KybSurveyResponseMapper mapper = new KybSurveyResponseMapper();

  @Test
  void extractsSelfCertificationAllTrue() {
    var response =
        new KybSurveyResponse(
            List.of(
                new CompanySourceOfIncome(
                    List.of(
                        new CompanyIncomeSourceItem.Option(ONLY_ACTIVE_IN_ESTONIA),
                        new CompanyIncomeSourceItem.Option(
                            NOT_SANCTIONED_NOT_PROFITING_FROM_SANCTIONED_COUNTRIES),
                        new CompanyIncomeSourceItem.Option(NOT_IN_CRYPTO)))));

    assertThat(mapper.extractSelfCertification(response))
        .isEqualTo(new SelfCertification(true, true, true));
  }

  @Test
  void extractsSelfCertificationPartial() {
    var response =
        new KybSurveyResponse(
            List.of(
                new CompanySourceOfIncome(
                    List.of(new CompanyIncomeSourceItem.Option(ONLY_ACTIVE_IN_ESTONIA)))));

    assertThat(mapper.extractSelfCertification(response))
        .isEqualTo(new SelfCertification(true, false, false));
  }
}
