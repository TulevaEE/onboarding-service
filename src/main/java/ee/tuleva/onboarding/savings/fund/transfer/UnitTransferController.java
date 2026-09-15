package ee.tuleva.onboarding.savings.fund.transfer;

import static java.util.Objects.requireNonNullElse;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/savings-fund/unit-transfers")
@RequiredArgsConstructor
@Profile("!staging")
public class UnitTransferController {

  private final UnitTransferService unitTransferService;
  private final AdminTokenValidator tokenValidator;

  @PostMapping("/preview")
  UnitTransferVerdict preview(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody UnitTransferCommand command) {
    tokenValidator.validate(token);
    return unitTransferService.preview(command);
  }

  @PostMapping
  UnitTransferSummary submit(
      @RequestHeader("X-Admin-Token") String token, @Valid @RequestBody SubmitRequest request) {
    tokenValidator.validate(token);
    return UnitTransferSummary.of(
        unitTransferService.submit(request.transfer(), request.confirm(), request.submittedBy()));
  }

  @PostMapping("/{id}/approve")
  UnitTransferSummary approve(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable UUID id,
      @Valid @RequestBody ApproveRequest request) {
    tokenValidator.validate(token);
    return UnitTransferSummary.of(unitTransferService.approve(id, request.approvedBy()));
  }

  @PostMapping("/{id}/cancel")
  UnitTransferSummary cancel(@RequestHeader("X-Admin-Token") String token, @PathVariable UUID id) {
    tokenValidator.validate(token);
    return UnitTransferSummary.of(unitTransferService.cancel(id));
  }

  @GetMapping("/awaiting-approval")
  List<UnitTransferSummary> awaitingApproval(@RequestHeader("X-Admin-Token") String token) {
    tokenValidator.validate(token);
    return unitTransferService.awaitingApproval().stream().map(UnitTransferSummary::of).toList();
  }

  /**
   * ErrorHandlingControllerAdvice maps neither of these, so without this a refusal the caller could
   * act on would reach them as a 500 saying nothing.
   */
  @ExceptionHandler(NoSuchElementException.class)
  @ResponseStatus(NOT_FOUND)
  String notFound(NoSuchElementException exception) {
    return requireNonNullElse(exception.getMessage(), exception.toString());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(BAD_REQUEST)
  String refused(IllegalArgumentException exception) {
    return requireNonNullElse(exception.getMessage(), exception.toString());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(CONFLICT)
  String conflicted(IllegalStateException exception) {
    return requireNonNullElse(exception.getMessage(), exception.toString());
  }

  record SubmitRequest(
      @Valid @NotNull UnitTransferCommand transfer,
      @NotBlank String confirm,
      @NotBlank String submittedBy) {}

  record ApproveRequest(@NotBlank String approvedBy) {}
}
