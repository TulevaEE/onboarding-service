package ee.tuleva.onboarding.banking;

import java.util.Map;
import java.util.UUID;

public interface StoredTradeReferences {

  Map<UUID, String> byLedgerExternalReference();
}
