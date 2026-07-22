# Fund Value / Index Values

## `index_values` table is immutable

Never UPDATE or UPSERT rows in the `index_values` table. It is append-only by design (`INSERT IF NOT EXISTS`).

If a data source returns incorrect data, fix the retriever's timing or cutoff logic so bad data is never inserted. Do not add update-on-conflict mechanisms to `JdbcFundValueRepository`.

## Savings fund retrievers skip non-working days

Retrievers for exchange-traded fund data (`requiresWorkingDay() = true`) are skipped on weekends and public holidays. This prevents saving preliminary closing prices that exchanges correct later (e.g., Deutsche Börse corrects Friday closes around noon CET Saturday).

## Deutsche Börse quote_box lastPrice is not always the official close

Xetra runs retail early/late trading sessions outside regular hours whose prints update `quote_box.lastPrice` and carry `lastPriceIndicator: "R"`. Only a print with a null `lastPriceIndicator` is an official Xetra price determination (e.g., the closing auction) — anything else must fall back to the `price_history` bar close.
