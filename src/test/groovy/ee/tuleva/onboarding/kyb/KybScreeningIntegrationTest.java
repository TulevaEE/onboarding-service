package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class KybScreeningIntegrationTest {

  private static final String PERSONAL_CODE = "38501010002";

  @Autowired private KybScreeningService kybScreeningService;
  @Autowired private AmlCheckRepository amlCheckRepository;

  @Test
  void singlePersonCompanyWithValidOwnershipCreatesSuccessfulChecks() {
    var person = new KybRelatedPerson(PERSONAL_CODE, true, true, true, BigDecimal.valueOf(100));
    var data = new KybCompanyData("12345678", PERSONAL_CODE, R, List.of(person));

    var results = kybScreeningService.screen(data);

    assertThat(results).hasSize(2).allMatch(KybCheck::success);

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    var types = amlChecks.stream().map(AmlCheck::getType).toList();
    assertThat(types).containsExactlyInAnyOrder(KYB_COMPANY_ACTIVE, KYB_SOLE_MEMBER_OWNERSHIP);
    assertThat(amlChecks).allMatch(AmlCheck::isSuccess);
  }

  @Test
  void singlePersonCompanyWithInvalidOwnershipCreatesFailedCheck() {
    var person = new KybRelatedPerson(PERSONAL_CODE, true, true, false, BigDecimal.valueOf(100));
    var data = new KybCompanyData("12345678", PERSONAL_CODE, R, List.of(person));

    var results = kybScreeningService.screen(data);

    assertThat(results).filteredOn(c -> !c.success()).hasSize(1);

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(PERSONAL_CODE, aYearAgo());
    var failedCheck =
        amlChecks.stream().filter(c -> c.getType() == KYB_SOLE_MEMBER_OWNERSHIP).findFirst();
    assertThat(failedCheck).isPresent();
    assertThat(failedCheck.get().isSuccess()).isFalse();
  }
}
