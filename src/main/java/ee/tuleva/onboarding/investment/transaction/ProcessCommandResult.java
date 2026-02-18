package ee.tuleva.onboarding.investment.transaction;

import java.util.List;

record ProcessCommandResult(TransactionBatch batch, List<TransactionOrder> orders) {}
