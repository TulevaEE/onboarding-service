package ee.tuleva.onboarding.aml.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevSanctionCheckService implements SanctionCheckService {

  private final ObjectMapper objectMapper;

  @Override
  public JsonNode match(String fullName, LocalDate birthDate, String idNumber, String nationality) {
    return objectMapper.createArrayNode();
  }
}
