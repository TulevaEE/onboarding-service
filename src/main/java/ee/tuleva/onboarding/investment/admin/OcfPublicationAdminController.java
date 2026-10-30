package ee.tuleva.onboarding.investment.admin;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationPreview;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationRequest;
import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ocf-publications")
@RequiredArgsConstructor
@Profile("!staging")
public class OcfPublicationAdminController {

  private final AdminTokenValidator tokenValidator;
  private final OcfPublicationService ocfPublicationService;

  public record PreviewRequest(
      @NotNull TulevaFund fund, @NotNull YearMonth month, @NotNull LocalDate validFrom) {}

  @PostMapping("/preview")
  public OcfPublicationPreview preview(
      @RequestHeader("X-Admin-Token") String token, @Valid @RequestBody PreviewRequest request) {
    tokenValidator.validateWithOpsAccess(token);
    return ocfPublicationService.preview(request.fund(), request.month(), request.validFrom());
  }

  @PostMapping
  public OcfPublicationPreview publish(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody OcfPublicationRequest request) {
    tokenValidator.validateWithOpsAccess(token);
    return ocfPublicationService.publish(request);
  }
}
