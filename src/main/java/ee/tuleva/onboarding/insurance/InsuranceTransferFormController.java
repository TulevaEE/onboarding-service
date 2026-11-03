package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.DIGIDOC_CONTAINER;
import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.PDF;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/insurance-transfer-forms")
@RequiredArgsConstructor
public class InsuranceTransferFormController {

  private final InsuranceTransferFormService formService;

  @Operation(summary = "Fill in the insurer's own form to end a third pillar insurance contract")
  @PostMapping
  public InsuranceTransferFormResponse create(
      @AuthenticationPrincipal AuthenticatedPerson person,
      @Valid @RequestBody CreateInsuranceTransferFormCommand command) {
    return InsuranceTransferFormResponse.from(formService.create(person, command).summary());
  }

  @Operation(summary = "Get an insurance transfer form")
  @GetMapping("/{id}")
  public InsuranceTransferFormResponse get(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson person) {
    return InsuranceTransferFormResponse.from(formService.summary(id, person));
  }

  @Operation(summary = "Get the filled-in insurance transfer form before signing")
  @GetMapping("/{id}/preview")
  public ResponseEntity<byte[]> preview(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson person) {
    InsuranceTransferForm form = formService.get(id, person);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(PDF.fileName(form.getInsurer().signedFileBaseName()))
                .build()
                .toString())
        .body(InsuranceTransferFormService.filledPdfOf(form));
  }

  @Operation(summary = "Get the signed insurance transfer form")
  @GetMapping("/{id}/signed-file")
  public ResponseEntity<byte[]> signedFile(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson person) {
    InsuranceTransferForm form = formService.get(id, person);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(DIGIDOC_CONTAINER.getMimeType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(DIGIDOC_CONTAINER.fileName(form.getInsurer().signedFileBaseName()))
                .build()
                .toString())
        .body(InsuranceTransferFormService.signedContainerOf(form));
  }
}
