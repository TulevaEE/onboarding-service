package ee.tuleva.onboarding.banking.processor;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.banking.StoredTradeReferences;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class StoredStatementTradeReferences implements StoredTradeReferences {

  private static final Set<String> TRADE_CODES = Set.of("TRAD", "SUBS", "REDM");

  private final BankingMessageRepository messageRepository;
  private final BankStatementExtractor statementExtractor;
  private final TradeSettlementParser tradeSettlementParser;

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, String> byLedgerExternalReference() {
    Map<UUID, String> references = new HashMap<>();
    for (UUID messageId : messageRepository.findIdsOfProcessedMessages()) {
      messageRepository
          .findById(messageId)
          .ifPresent(message -> collectTradeReferences(message, references));
    }
    log.info("Read trade references from stored statements: references={}", references.size());
    return references;
  }

  private void collectTradeReferences(BankingMessage message, Map<UUID, String> references) {
    BankStatement statement;
    try {
      statement = statement(message);
    } catch (RuntimeException e) {
      log.warn("Stored statement could not be read: messageId={}", message.getId(), e);
      return;
    }
    String iban = statement.getBankStatementAccount().iban();
    for (BankStatementEntry entry : statement.getEntries()) {
      if (entry.subFamilyCode() != null && TRADE_CODES.contains(entry.subFamilyCode())) {
        tradeSettlementParser
            .parse(entry.remittanceInformation())
            .ifPresent(
                trade ->
                    references.putIfAbsent(
                        UUID.nameUUIDFromBytes((iban + ":" + entry.externalId()).getBytes(UTF_8)),
                        trade.details().brokerReference()));
      }
    }
  }

  private BankStatement statement(BankingMessage message) {
    return switch (message.getMessageType()) {
      case INTRA_DAY_REPORT ->
          statementExtractor.extractFromIntraDayReport(
              message.getRawResponse(), message.getTimezoneId());
      case HISTORIC_STATEMENT ->
          statementExtractor.extractFromHistoricStatement(
              message.getRawResponse(), message.getTimezoneId());
      case null ->
          throw new IllegalStateException(
              "Stored message has no type: messageId=" + message.getId());
    };
  }
}
