package ee.tuleva.onboarding.investment.admin;

import static org.springframework.http.HttpStatus.CONFLICT;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationPreview;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationRefusedException;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationRequest;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin/ocf-publications")
@RequiredArgsConstructor
@Profile("!staging")
public class OcfPublicationAdminController {

  private final AdminTokenValidator tokenValidator;
  private final OcfPublicationService ocfPublicationService;

  public record PreviewRequest(TulevaFund fund, YearMonth month, LocalDate validFrom) {}

  @PostMapping("/preview")
  public OcfPublicationPreview preview(
      @RequestHeader("X-Admin-Token") String token, @RequestBody PreviewRequest request) {
    tokenValidator.validateWithOpsAccess(token);
    return answering(
        () -> ocfPublicationService.preview(request.fund(), request.month(), request.validFrom()));
  }

  @PostMapping
  public OcfPublicationPreview publish(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody OcfPublicationRequest request) {
    tokenValidator.validateWithOpsAccess(token);
    return answering(() -> ocfPublicationService.publish(request));
  }

  private static OcfPublicationPreview answering(Supplier<OcfPublicationPreview> call) {
    try {
      return call.get();
    } catch (OcfPublicationRefusedException refusal) {
      log.info("OCF publication refused: reason={}", refusal.getMessage());
      throw new ResponseStatusException(CONFLICT, refusal.getMessage(), refusal);
    }
  }
}
