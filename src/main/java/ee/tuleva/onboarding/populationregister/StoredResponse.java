package ee.tuleva.onboarding.populationregister;

import java.util.List;
import java.util.Map;
import java.util.UUID;

record StoredResponse(UUID messageId, List<Map<String, Object>> response) {}
