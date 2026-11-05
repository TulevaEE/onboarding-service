package ee.tuleva.onboarding.investment.transaction.ingest;

sealed interface ReconciliationAlert
    permits QuantityAmountMismatchEvent,
        ExecutionPriceConsistencyEvent,
        PossibleReportTruncationEvent {}
