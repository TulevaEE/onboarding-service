package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import org.jspecify.annotations.NullMarked;

@NullMarked
record FtConfirmationOutcome(FtConfirmation confirmation, FtConfirmationResult result) {}
