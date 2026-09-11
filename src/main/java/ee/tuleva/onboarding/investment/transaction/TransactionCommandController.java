package ee.tuleva.onboarding.investment.transaction;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.investment.transaction.export.ExportFile;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
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

  private final TransactionAdminService adminService;
  private final AdminTokenAuthenticator authenticator;

  @PostMapping("/transaction-commands")
  public TransactionCommandResponse createCommand(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody CreateTransactionCommandRequest request) {

    var actor = authenticator.resolveActor(token);

    log.info(
        "Admin triggered transaction command: fund={}, mode={}, asOfDate={}, actor={}",
        request.fund(),
        request.mode(),
        request.asOfDate(),
        actor);

    return adminService.createAndProcess(
        request.fund(),
        request.mode(),
        request.asOfDate(),
        request.manualAdjustments(),
        actor,
        request.cash());
  }

  @PostMapping("/transaction-commands/batch")
  public List<TransactionCommandResponse> createCommands(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody CreateTransactionCommandBatchRequest request) {

    var actor = authenticator.resolveActor(token);

    log.info(
        "Admin triggered transaction command batch: funds={}, mode={}, asOfDate={}, actor={}",
        request.funds(),
        request.mode(),
        request.asOfDate(),
        actor);

    return adminService.createAndProcessAll(
        request.funds(),
        request.mode(),
        request.asOfDate(),
        actor,
        request.cash() == null ? Map.of() : request.cash());
  }

  @GetMapping("/transaction-commands/{id}")
  public TransactionCommandResponse getCommand(
      @RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {

    authenticator.resolveActor(token);

    return adminService
        .getCommand(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(NOT_FOUND, "Transaction command not found: id=" + id));
  }

  @GetMapping("/transaction-batches/{id}")
  public TransactionBatchResponse getBatch(
      @RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {

    authenticator.resolveActor(token);

    return adminService
        .getBatch(id)
        .orElseThrow(
            () -> new ResponseStatusException(NOT_FOUND, "Transaction batch not found: id=" + id));
  }

  @PostMapping("/transaction-batches/{id}/confirm")
  public TransactionBatchResponse confirmBatch(
      @RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {

    var actor = authenticator.resolveActor(token);

    log.info("Admin triggered transaction batch confirmation: id={}, actor={}", id, actor);

    return adminService.confirmAndFinalize(id, actor);
  }

  @PostMapping("/transaction-batches/{id}/cancel")
  public TransactionBatchResponse cancelBatch(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable Long id,
      @Valid @RequestBody CancelTransactionBatchRequest request) {

    var actor = authenticator.resolveActor(token);

    log.info(
        "Admin triggered transaction batch cancellation: id={}, actor={}, reason={}",
        id,
        actor,
        request.reason());

    return adminService.cancelBatch(id, request.reason(), actor);
  }

  @PostMapping("/transaction-batches/{id}/discard")
  public TransactionBatchResponse discardBatch(
      @RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {

    var actor = authenticator.resolveActor(token);

    log.info("Admin triggered transaction batch discard: id={}, actor={}", id, actor);

    return adminService.discardBatch(id, actor);
  }

  @PostMapping("/transaction-orders/{id}/cancel")
  public TransactionOrderResponse cancelOrder(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable Long id,
      @Valid @RequestBody CancelTransactionOrderRequest request) {

    var actor = authenticator.resolveActor(token);

    log.info(
        "Admin triggered transaction order cancellation: id={}, actor={}, reason={}",
        id,
        actor,
        request.reason());

    return adminService.cancelOrder(id, request.reason(), actor);
  }

  @PostMapping("/transaction-orders/{id}/order-type")
  public TransactionOrderResponse setOrderType(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable Long id,
      @Valid @RequestBody SetTransactionOrderTypeRequest request) {

    var actor = authenticator.resolveActor(token);

    log.info(
        "Admin triggered transaction order type change: id={}, actor={}, orderType={}",
        id,
        actor,
        request.orderType());

    return adminService.setOrderType(id, request.orderType(), actor);
  }

  @GetMapping("/transaction-batches/{id}/exports/{type}")
  public ResponseEntity<byte[]> downloadExport(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable Long id,
      @PathVariable String type) {

    authenticator.resolveActor(token);

    ExportFile exportFile =
        ExportFile.byMetadataKey(type)
            .orElseThrow(
                () -> new ResponseStatusException(NOT_FOUND, "Unknown export type: type=" + type));

    byte[] export =
        adminService
            .exportFile(id, type)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        NOT_FOUND, "Export not found: batchId=" + id + ", type=" + type));

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(exportFile.mimeType()))
        .headers(
            headers ->
                headers.setContentDisposition(
                    ContentDisposition.attachment()
                        .filename(exportFile.downloadFileName(id))
                        .build()))
        .body(export);
  }
}
