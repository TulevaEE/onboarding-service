package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.account.transaction.Transaction;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TransactionsWithCounterparties(
    List<Transaction> transactions, Map<UUID, String> counterpartyIbans) {}
