package ee.tuleva.onboarding.savings.fund.documents;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/savings")
@RequiredArgsConstructor
public class SavingsFundDocumentsController {

  private final SavingsFundDocumentsService savingsFundDocumentsService;

  @Operation(summary = "Get savings fund (TKF) legal document URLs")
  @GetMapping("/documents")
  public SavingsFundDocuments getDocuments() {
    return savingsFundDocumentsService.getDocuments();
  }
}
