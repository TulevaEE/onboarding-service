package ee.tuleva.onboarding.banking;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * What the payment checks need to know about one debit seen on a bank statement.
 *
 * <p>The statement rows are built in savings and the checks live in banking. Passing the savings
 * entity across would make banking depend on savings and close a module cycle, so this is the
 * boundary type: savings maps into it, banking never sees anything else.
 *
 * <p>{@code entryId} is the bank's own reference for the statement entry, which is what identifies
 * the debit across the re-reads of the same statement. {@code amount} is the signed statement
 * amount, so a debit is negative — the caller passes the row as the bank stated it rather than
 * pre-interpreting it.
 */
public record StatementDebit(
    @Nullable String entryId,
    BigDecimal amount,
    String beneficiaryIban,
    @Nullable String endToEndId) {}
