# Fund Value / Index Values

## `index_values` table is immutable

Never UPDATE or UPSERT rows in the `index_values` table. It is append-only by design (`INSERT IF NOT EXISTS`).

If a data source returns incorrect data, fix the retriever's timing or cutoff logic so bad data is never inserted. Do not add update-on-conflict mechanisms to `JdbcFundValueRepository`.

## Savings fund retrievers skip non-working days

Retrievers for exchange-traded fund data (`requiresWorkingDay() = true`) are skipped on weekends and public holidays. This prevents saving preliminary closing prices that exchanges correct later (e.g., Deutsche Börse corrects Friday closes around noon CET Saturday).

## Deutsche Börse: dated bars from the chart feed, precision from price_information

The open `mobile-api.live.deutsche-boerse.com/v1/data/price_history` returns empty `data` for every ISIN since 2026-11-12, and the website now reads history from `api.live.deutsche-boerse.com` with signed headers. Do not reproduce that signature. `DeutscheBoerseValueRetriever` reads two unsigned endpoints instead:

- `api.live.deutsche-boerse.com/v1/tradingview/history?symbol=XETR:<ISIN>&resolution=1D&from=&to=` gives dated daily bars (bar timestamps are 02:00 Europe/Berlin, so convert in that zone). Closes match the official Xetra close but are rounded to 3 decimals (`pricescale` 1000, no parameter changes it). A window longer than about ten years returns `no_data`, so the request never reaches back more than nine years. It has transiently returned fewer bars than exist for a window; a missing day then stays a gap.
- `api.live.deutsche-boerse.com/v1/data/price_information/single` gives `lastPrice` at full precision. Xetra runs retail early/late sessions whose prints update `lastPrice` and carry `lastPriceIndicator: "R"`; only a null indicator is an official price determination (e.g., the closing auction).

Every chart bar is stored: `BenchmarkCheckBuilder` reads `IE00BDBRDM35.XETR` directly as the BOND_GLOBAL proxy, so a gap would read as a 0% benchmark return. An official `lastPrice` replaces the bar for its date at full precision; other days keep the 3-decimal close, which only differs for an instrument quoted finer than that (today only the sub-€5 `IE00BDBRDM35`). `FundValueIntegrityChecker` therefore lets a Deutsche Börse close sit up to 0.0005 (half a unit of the chart feed, which rounds half up) from the other source on top of the strict 0.001% exchange threshold. `closingPricePrevTradingDay` is the official close at full precision but carries no date, so it is never stored.
