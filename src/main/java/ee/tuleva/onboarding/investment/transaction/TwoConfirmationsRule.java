package ee.tuleva.onboarding.investment.transaction;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class TwoConfirmationsRule {

  private final AdminTokenAuthenticator authenticator;
  private final OrderSendAccess sendAccess;

  void requireAllowedToGiveFirstConfirmation(
      TransactionBatch batch, Long reviewedVersion, String actor) {
    if (!isNamedOperator(actor)) {
      throw refused("Only a named operator can give a batch its first confirmation", batch, actor);
    }
    requireTheReviewedVersion(batch, reviewedVersion, actor);
  }

  OrderSender requireAllowedToSend(
      TransactionBatch batch,
      Long reviewedVersion,
      String sender,
      List<TransactionAuditEvent> firstConfirmations,
      long orderTypeChanges) {
    if (!isNamedOperator(sender)) {
      throw refused("Only an operator with send access can send a batch", batch, sender);
    }
    OrderSender ownAddress =
        sendAccess
            .senderFor(sender)
            .filter(OrderSender::hasTulevaAddress)
            .orElseThrow(
                () ->
                    refused(
                        "Only an operator with send access and a tuleva.ee address can send a"
                            + " batch",
                        batch,
                        sender));
    requireTheReviewedVersion(batch, reviewedVersion, sender);
    long contents = batch.getVersion() - orderTypeChanges;
    if (firstConfirmations.stream()
        .noneMatch(
            confirmation ->
                confirmsContents(confirmation, contents)
                    && isAnotherNamedOperator(confirmation.getActor(), sender))) {
      throw refused(
          "A batch is sent only after another named operator confirmed its current contents",
          batch,
          sender);
    }
    return ownAddress;
  }

  static Map<String, Object> firstConfirmationPayload(
      TransactionBatch batch, String actor, long orderTypeChanges) {
    return Map.of(
        "actor", actor, "version", batch.getVersion(), "orderTypeChanges", orderTypeChanges);
  }

  private static boolean confirmsContents(TransactionAuditEvent confirmation, long contents) {
    Map<String, Object> payload = confirmation.getPayload();
    return payload.get("version") instanceof Number version
        && payload.get("orderTypeChanges") instanceof Number changes
        && version.longValue() - changes.longValue() == contents;
  }

  private static void requireTheReviewedVersion(
      TransactionBatch batch, Long reviewedVersion, String actor) {
    if (!reviewedVersion.equals(batch.getVersion())) {
      throw new ResponseStatusException(
          CONFLICT,
          "Batch changed since it was reviewed: id="
              + batch.getId()
              + ", version="
              + batch.getVersion()
              + ", reviewedVersion="
              + reviewedVersion
              + ", actor="
              + actor);
    }
  }

  private boolean isAnotherNamedOperator(@Nullable String actor, String sender) {
    return isNamedOperator(actor) && !sender.equals(actor);
  }

  private boolean isNamedOperator(@Nullable String actor) {
    return actor != null && authenticator.isNamedOperator(actor);
  }

  private static ResponseStatusException refused(
      String reason, TransactionBatch batch, String actor) {
    String message =
        reason + ": id=" + batch.getId() + ", version=" + batch.getVersion() + ", actor=" + actor;
    log.warn("Transaction batch confirmation refused: {}", message);
    return new ResponseStatusException(FORBIDDEN, message);
  }
}
