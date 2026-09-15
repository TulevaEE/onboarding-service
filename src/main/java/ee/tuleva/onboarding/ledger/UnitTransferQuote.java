package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;

/**
 * What a transfer would do, answered without writing anything.
 *
 * <p>{@code giverPaidIn} and {@code giverUnitsOwned} are the giver's figures as they stand now, so
 * that a transfer can carry them as evidence. They are not moved anywhere: a spouse who inherits
 * joint marital property may count the transferor's acquisition cost, and without a record taken at
 * the time nobody can produce that figure years later. {@code giverPaidIn} is money the fund
 * received over the giver's lifetime and redemption never reduces it, so it is a record of payments
 * rather than the cost of the units still held.
 */
public record UnitTransferQuote(
    BigDecimal fundUnits,
    BigDecimal giverUnitsAfter,
    BigDecimal receiverUnitsAfter,
    BigDecimal giverPaidIn,
    BigDecimal giverUnitsOwned) {}
