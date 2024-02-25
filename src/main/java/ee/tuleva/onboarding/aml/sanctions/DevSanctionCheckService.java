package ee.tuleva.onboarding.aml.sanctions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevSanctionCheckService implements SanctionCheckService {

  private final ObjectMapper objectMapper;

  @Override
  public ArrayNode match(String fullName, String idNumber, String country) {
    return objectMapper.createArrayNode();
  }
}
