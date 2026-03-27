package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.survey.KycSurveyService;
import ee.tuleva.onboarding.party.PartyId;
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

    PartyId partyId = request.getPartyId();
    User user =
        userService
            .findByPersonalCode(partyId.code())
            .orElseThrow(
                () -> new IllegalStateException("User not found for party: party=" + partyId));
    Country country =
        kycSurveyService
            .getCountry(user.getId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "KYC survey with country not found: userId=" + user.getId()));

    List<AmlCheck> checks = amlService.addSanctionAndPepCheckIfMissing(user, country);

    boolean allChecksPassed = checks.isEmpty() || checks.stream().allMatch(AmlCheck::isSuccess);

    if (!allChecksPassed) {
      log.info("Redemption requires review: id={}, party={}", request.getId(), partyId);
      redemptionStatusService.changeStatus(request.getId(), IN_REVIEW);
    } else {
      log.info("Redemption verification passed: id={}, party={}", request.getId(), partyId);
      redemptionStatusService.changeStatus(request.getId(), VERIFIED);
    }
  }
}
