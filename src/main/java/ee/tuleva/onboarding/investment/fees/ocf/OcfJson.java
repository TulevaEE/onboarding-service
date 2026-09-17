package ee.tuleva.onboarding.investment.fees.ocf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
class OcfJson {

  private final ObjectMapper objectMapper;

  String checks(List<OcfGap> gaps) {
    return write(Map.of("gaps", gaps.stream().map(Enum::name).toList()));
  }

  String navDates(List<LocalDate> navDates) {
    return write(navDates.stream().map(LocalDate::toString).toList());
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialise OCF snapshot diagnostics", e);
    }
  }
}
