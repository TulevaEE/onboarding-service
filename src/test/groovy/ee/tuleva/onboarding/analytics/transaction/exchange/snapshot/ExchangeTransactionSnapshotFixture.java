package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionFixture;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDateTime;

public class ExchangeTransactionSnapshotFixture {

  public static ExchangeTransactionSnapshot.ExchangeTransactionSnapshotBuilder
      exampleSnapshotBuilderFromTransaction(
          ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransaction originalTx) {
    return ExchangeTransactionSnapshot.builder()
        .snapshotTakenAt(LocalDateTime.now(ClockHolder.clock()).minusHours(1))
        .createdAt(LocalDateTime.now(ClockHolder.clock()))
        .reportingDate(originalTx.getReportingDate())
        .securityFrom(originalTx.getSecurityFrom())
        .securityTo(originalTx.getSecurityTo())
        .fundManagerFrom(originalTx.getFundManagerFrom())
        .fundManagerTo(originalTx.getFundManagerTo())
        .code(originalTx.getCode())
        .firstName(originalTx.getFirstName())
        .name(originalTx.getName())
        .percentage(originalTx.getPercentage())
        .unitAmount(originalTx.getUnitAmount())
        .sourceDateCreated(originalTx.getDateCreated());
  }

  public static ExchangeTransactionSnapshot exampleSnapshot() {
    return exampleSnapshotBuilderFromTransaction(ExchangeTransactionFixture.exampleTransaction())
        .build();
  }

  public static ExchangeTransactionSnapshot anotherExampleSnapshot() {
    return exampleSnapshotBuilderFromTransaction(
            ExchangeTransactionFixture.anotherExampleTransaction())
        .code("SNAPSHOT_CODE_XYZ")
        .build();
  }
}
