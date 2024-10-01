package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.batch.MandateBatchController.MANDATE_BATCHES_URI;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATE_BATCHES_URI)
@RequiredArgsConstructor
public class MandateBatchController {
  public static final String MANDATE_BATCHES_URI = "/mandate-batches";

  private final MandateBatchService mandateBatchService;

  @Operation(summary = "Create mandate batch")
  @PostMapping()
  public MandateBatchDto createMandateBatch(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody MandateBatchDto mandateBatchDto) {

    return MandateBatchDto.from(
        mandateBatchService.createMandateBatch(authenticatedPerson, mandateBatchDto));
  }
}
