package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.exception.NotAMemberException;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CapitalController {

  private final UserService userService;
  private final CapitalService capitalService;

  @Operation(summary = "Get info about current user capital")
  @GetMapping("/me/capital")
  public List<CapitalRow> capitalStatement(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId).orElseThrow();
    return user.getMember()
        .map(member -> capitalService.getCapitalRows(member.getId()))
        .orElseThrow(NotAMemberException::new);
  }

  @GetMapping("/me/capital/events")
  public List<ApiCapitalEvent> capitalEvents(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId).orElseThrow();
    return user.getMember()
        .map(member -> capitalService.getCapitalEvents(member.getId()))
        .orElseThrow(NotAMemberException::new);
  }

  @GetMapping("/capital/total")
  public CapitalTotalDto getCapitalTotal() {
    return CapitalTotalDto.from(capitalService.getLatestAggregatedCapitalEvent().orElseThrow());
  }
}
