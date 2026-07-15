package ee.tuleva.onboarding.investment.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Profile("!staging")
@NullMarked
public class TransactionCommandController {

  private static final MediaType XLSX_MEDIA_TYPE =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final TransactionAdminService adminService;

  @Value("${admin.api-token:}")
  private String adminApiToken = "";

  @PostMapping("/transaction-commands")
  public TransactionCommandResponse createCommand(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody CreateTransactionCommandRequest request) {

    validateToken(token);

    log.info(
        "Admin triggered transaction command: fund={}, mode={}, asOfDate={}",
        request.fund(),
        request.mode(),
        request.asOfDate());

    return adminService.createAndProcess(
        request.fund(), request.mode(), request.asOfDate(), request.manualAdjustments());
  }

  @PostMapping("/transaction-commands/batch")
  public List<TransactionCommandResponse> createCommands(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody CreateTransactionCommandBatchRequest request) {

    validateToken(token);

    log.info(
        "Admin triggered transaction command batch: funds={}, mode={}, asOfDate={}",
        request.funds(),
        request.mode(),
        request.asOfDate());

    return adminService.createAndProcessAll(request.funds(), request.mode(), request.asOfDate());
  }

  @GetMapping("/transaction-commands/{id}")
  public TransactionCommandResponse getCommand(
      @RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {

    validateToken(token);

    return adminService
        .getCommand(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(NOT_FOUND, "Transaction command not found: id=" + id));
  }

  @GetMapping("/transaction-batches/{id}")
  public TransactionBatchResponse getBatch(
      @RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {

    validateToken(token);

    return adminService
        .getBatch(id)
        .orElseThrow(
            () -> new ResponseStatusException(NOT_FOUND, "Transaction batch not found: id=" + id));
  }

  @PostMapping("/transaction-batches/{id}/confirm")
  public TransactionBatchResponse confirmBatch(
      @RequestHeader("X-Admin-Token") String token,
      @RequestHeader(name = "X-Admin-Actor", required = false, defaultValue = "admin") String actor,
      @PathVariable Long id) {

    validateToken(token);

    log.info("Admin triggered transaction batch confirmation: id={}, actor={}", id, actor);

    return adminService.confirmAndFinalize(id, actor);
  }

  @PostMapping("/transaction-batches/{id}/cancel")
  public TransactionBatchResponse cancelBatch(
      @RequestHeader("X-Admin-Token") String token,
      @RequestHeader(name = "X-Admin-Actor", required = false, defaultValue = "admin") String actor,
      @PathVariable Long id,
      @Valid @RequestBody CancelTransactionBatchRequest request) {

    validateToken(token);

    log.info(
        "Admin triggered transaction batch cancellation: id={}, actor={}, reason={}",
        id,
        actor,
        request.reason());

    return adminService.cancelBatch(id, request.reason(), actor);
  }

  @GetMapping("/transaction-batches/{id}/exports/{type}")
  public ResponseEntity<byte[]> downloadExport(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable Long id,
      @PathVariable String type) {

    validateToken(token);

    byte[] export =
        adminService
            .exportFile(id, type)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        NOT_FOUND, "Export not found: batchId=" + id + ", type=" + type));

    return ResponseEntity.ok()
        .contentType(XLSX_MEDIA_TYPE)
        .headers(
            headers ->
                headers.setContentDisposition(
                    ContentDisposition.attachment()
                        .filename("batch-%d-%s.xlsx".formatted(id, type))
                        .build()))
        .body(export);
  }

  private void validateToken(String token) {
    if (adminApiToken.isBlank()) {
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Admin API not configured");
    }
    if (!MessageDigest.isEqual(adminApiToken.getBytes(UTF_8), token.getBytes(UTF_8))) {
      throw new ResponseStatusException(UNAUTHORIZED, "Invalid admin token");
    }
  }
}
