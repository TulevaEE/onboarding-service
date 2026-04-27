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

  private final RedemptionStatusService redemptionStatusService;
  private final UserService userService;
  private final KycSurveyService kycSurveyService;
  private final AmlService amlService;

  @Transactional
  public void process(RedemptionRequest request) {
    log.info(
        "Processing verification for redemption request: id={}, party={}",
        request.getId(),
        request.getPartyId());

    boolean passed =
        switch (request.getPartyId().type()) {
          case PERSON -> runPersonChecks(request);
          // TODO: Replace this always-IN_REVIEW branch with a lookup against the periodic company
          //  KYB recheck job (sanctions/PEP + structural checks driven by
          // KybScreeningService.screen).
          //  Until that job is in place, every legal-entity redemption is routed to IN_REVIEW for
          //  manual compliance review. We are not going live with LE redemptions before the
          //  periodic job lands.
          case LEGAL_ENTITY -> false;
        };

    if (!passed) {
      log.info(
          "Redemption requires review: id={}, party={}", request.getId(), request.getPartyId());
      redemptionStatusService.changeStatus(request.getId(), IN_REVIEW);
    } else {
      log.info(
          "Redemption verification passed: id={}, party={}", request.getId(), request.getPartyId());
      redemptionStatusService.changeStatus(request.getId(), VERIFIED);
    }
  }

  private boolean runPersonChecks(RedemptionRequest request) {
    User user = userService.getByIdOrThrow(request.getUserId());
    Country country =
        kycSurveyService
            .getCountry(user.getId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "KYC survey with country not found: userId=" + user.getId()));

    List<AmlCheck> checks = amlService.addSanctionAndPepCheckIfMissing(user, country);
    return checks.stream().allMatch(AmlCheck::isSuccess);
  }
}
