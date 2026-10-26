package ee.tuleva.onboarding.savings.fund.gift;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/savings-fund/gifts")
@RequiredArgsConstructor
class ReceivedGiftController {

  private final ReceivedGiftService receivedGiftService;

  @GetMapping
  @Operation(summary = "Gifts that have arrived in the logged-in person's own savings fund account")
  List<ReceivedGift> giftsToMe(@AuthenticationPrincipal AuthenticatedPerson person) {
    if (!person.isActingAsSelf()) {
      throw new NotActingAsSelfException();
    }
    return receivedGiftService.giftsTo(person.getPersonalCode());
  }
}
