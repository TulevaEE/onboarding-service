package ee.tuleva.onboarding.aml.sanctions;

import com.fasterxml.jackson.databind.node.ArrayNode;

public interface SanctionCheckService {
  ArrayNode match(String fullName, String idNumber, String country);
}
