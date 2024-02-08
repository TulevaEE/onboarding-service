package ee.tuleva.onboarding.aml.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;

public interface SanctionCheckService {
  JsonNode match(String fullName, LocalDate birthDate, String idNumber, String nationality);
}
