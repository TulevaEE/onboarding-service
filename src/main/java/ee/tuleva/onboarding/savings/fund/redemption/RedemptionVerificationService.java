package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.survey.KycSurveyService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionVerificationService {

  private final UserService userService;
  private final KycSurveyService kycSurveyService;
  private final AmlService amlService;
  private final RedemptionStatusService redemptionStatusService;

  @Transactional
  public void process(RedemptionRequest request) {
    log.info("Processing verification for redemption request: id={}", request.getId());

    User user = userService.getByIdOrThrow(request.getUserId());
    Country country = kycSurveyService.getCountry(user.getId()).orElseThrow();

    List<AmlCheck> checks = amlService.addSanctionAndPepCheckIfMissing(user, country);

    boolean allChecksPassed = checks.isEmpty() || checks.stream().allMatch(AmlCheck::isSuccess);

    if (!allChecksPassed) {
      log.info("Redemption requires review: id={}, userId={}", request.getId(), user.getId());
      redemptionStatusService.changeStatus(request.getId(), IN_REVIEW);
    } else {
      log.info("Redemption verification passed: id={}, userId={}", request.getId(), user.getId());
      redemptionStatusService.changeStatus(request.getId(), VERIFIED);
    }
  }
}
