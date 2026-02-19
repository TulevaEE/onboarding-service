package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerTransactionTypeIsolationTest {

  @Mock private LedgerTransactionService ledgerTransactionService;

  private final Clock clock = Clock.fixed(Instant.parse("2026-02-01T12:00:00Z"), ZoneId.of("UTC"));

  private final LedgerAccount stubAccount =
      LedgerAccount.builder()
          .owner(LedgerParty.builder().build())
          .assetType(EUR)
          .accountType(ASSET)
          .build();

  private final Set<String> recorded = new HashSet<>();

  @BeforeEach
  void setUp() {
    when(ledgerTransactionService.existsByExternalReferenceAndTransactionType(any(), any()))
        .thenAnswer(
            invocation -> {
              UUID uuid = invocation.getArgument(0);
              TransactionType type = invocation.getArgument(1);
              return recorded.contains(uuid + ":" + type);
            });
    when(ledgerTransactionService.createTransaction(
            any(), any(), any(), any(), any(LedgerEntryDto[].class)))
        .thenAnswer(
            invocation -> {
              TransactionType type = invocation.getArgument(0);
              UUID uuid = invocation.getArgument(2);
              recorded.add(uuid + ":" + type);
              return LedgerTransaction.builder().transactionType(type).build();
            });
  }

  @Test
  void sameExternalReferenceWithDifferentTypesDoesNotCollide() {
    UUID paymentId = UUID.randomUUID();

    checkAndRecord(paymentId, POSITION_UPDATE);
    checkAndRecord(paymentId, FEE_ACCRUAL);
    checkAndRecord(paymentId, FEE_SETTLEMENT);

    verify(ledgerTransactionService, times(3))
        .createTransaction(any(), any(), any(), any(), any(LedgerEntryDto[].class));
  }

  @Test
  void sameExternalReferenceWithSameTypeIsDetectedAsDuplicate() {
    UUID paymentId = UUID.randomUUID();

    checkAndRecord(paymentId, POSITION_UPDATE);
    checkAndRecord(paymentId, POSITION_UPDATE);

    verify(ledgerTransactionService, times(1))
        .createTransaction(any(), any(), any(), any(), any(LedgerEntryDto[].class));
  }

  private void checkAndRecord(UUID externalReference, TransactionType type) {
    if (!ledgerTransactionService.existsByExternalReferenceAndTransactionType(
        externalReference, type)) {
      ledgerTransactionService.createTransaction(
          type,
          Instant.now(clock),
          externalReference,
          Map.of(),
          new LedgerEntryDto(stubAccount, new BigDecimal("100.00")),
          new LedgerEntryDto(stubAccount, new BigDecimal("-100.00")));
    }
  }
}
