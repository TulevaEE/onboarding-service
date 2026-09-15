package ee.tuleva.onboarding.savings.fund.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/savings-fund/unit-transfers")
@RequiredArgsConstructor
@Profile("!staging")
public class UnitTransferController {

  private final UnitTransferService unitTransferService;

  @PostMapping("/preview")
  UnitTransferVerdict preview(@Valid @RequestBody UnitTransferCommand command) {
    return unitTransferService.preview(command);
  }

  @PostMapping
  UnitTransferSummary submit(@Valid @RequestBody SubmitRequest request) {
    return UnitTransferSummary.of(
        unitTransferService.submit(request.transfer(), request.confirm(), request.submittedBy()));
  }

  @PostMapping("/{id}/approve")
  UnitTransferSummary approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest request) {
    return UnitTransferSummary.of(unitTransferService.approve(id, request.approvedBy()));
  }

  @PostMapping("/{id}/cancel")
  UnitTransferSummary cancel(@PathVariable UUID id) {
    return UnitTransferSummary.of(unitTransferService.cancel(id));
  }

  @GetMapping("/awaiting-approval")
  List<UnitTransferSummary> awaitingApproval() {
    return unitTransferService.awaitingApproval().stream().map(UnitTransferSummary::of).toList();
  }

  record SubmitRequest(
      @Valid UnitTransferCommand transfer,
      @NotBlank String confirm,
      @NotBlank String submittedBy) {}

  record ApproveRequest(@NotBlank String approvedBy) {}
}
