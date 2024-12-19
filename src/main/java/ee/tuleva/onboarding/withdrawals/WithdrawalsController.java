package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.withdrawals.WithdrawalsController.WITHDRAWALS_URI;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1" + WITHDRAWALS_URI)
@RequiredArgsConstructor
public class WithdrawalsController {
  public static final String WITHDRAWALS_URI = "/withdrawals";

  private final WithdrawalEligibilityService withdrawalEligibilityService;
  private final FundPensionStatusService fundPensionStatusService;

  @Operation(summary = "Get fund pension status")
  @GetMapping("/fund-pension-status")
  public FundPensionStatus getFundPensionStatus(@AuthenticationPrincipal AuthenticatedPerson user) {
    return fundPensionStatusService.getFundPensionStatus(user);
  }

  @Operation(summary = "Get withdrawal eligibility")
  @GetMapping("/eligibility")
  public WithdrawalEligibilityDto getWithdrawalEligibility(
      @AuthenticationPrincipal AuthenticatedPerson user) {
    return withdrawalEligibilityService.getWithdrawalEligibility(user);
  }
}
