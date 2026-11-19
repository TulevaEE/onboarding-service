package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TransactionBatch.HISTORICAL_IMPORT_SOURCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionBatch.TRADE_REGISTRY_SQL_IMPORT_SOURCE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TransactionBatchTest {

  @Test
  void aBatchImportedFromTheTradeRegistry_byItsSqlExportOrTheHistoricalCsv_isKnownAsSuch() {
    assertThat(
            batch(Map.of("source", TRADE_REGISTRY_SQL_IMPORT_SOURCE, "importKey", "GAS:1:TUV100"))
                .importedFromTheTradeRegistry())
        .isTrue();
    assertThat(batch(Map.of("source", HISTORICAL_IMPORT_SOURCE)).importedFromTheTradeRegistry())
        .isTrue();
  }

  @Test
  void aBatchPreparedInThisServiceOrWithoutASource_isNotATradeRegistryImport() {
    assertThat(batch(Map.of("commandId", 7L, "mode", "REBALANCE")).importedFromTheTradeRegistry())
        .isFalse();
    assertThat(batch(Map.of("source", "SOMETHING_ELSE")).importedFromTheTradeRegistry()).isFalse();
    assertThat(batch(Map.of()).importedFromTheTradeRegistry()).isFalse();
  }

  private static TransactionBatch batch(Map<String, Object> metadata) {
    return TransactionBatch.builder()
        .fund(TUV100)
        .createdBy("operator")
        .status(BatchStatus.SENT)
        .metadata(metadata)
        .build();
  }
}
