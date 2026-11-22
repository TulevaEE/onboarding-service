package ee.tuleva.onboarding.investment.kid;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/kid")
@RequiredArgsConstructor
@Profile("!staging")
class KidPublicationAdminController {

  private final AdminTokenValidator tokenValidator;
  private final KidPublicationService kidPublicationService;

  @PostMapping("/{fund}/publications")
  List<KidPublication> publish(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable TulevaFund fund,
      @Valid @RequestBody KidPublicationRequest request) {
    tokenValidator.validateWithOpsAccess(token);
    try {
      return kidPublicationService.publish(fund, request);
    } catch (InvalidKidRequestException invalid) {
      throw new ResponseStatusException(BAD_REQUEST, invalid.getMessage(), invalid);
    } catch (KidPublicationRefusedException refused) {
      throw new ResponseStatusException(CONFLICT, refused.getMessage(), refused);
    }
  }
}
