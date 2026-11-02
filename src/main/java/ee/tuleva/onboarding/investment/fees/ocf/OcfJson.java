package ee.tuleva.onboarding.investment.fees.ocf;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
@Component
@RequiredArgsConstructor
class OcfJson {

  private final JsonMapper jsonMapper;

  String checks(List<OcfGap> gaps) {
    return jsonMapper.writeValueAsString(
        new Checks(
            gaps.stream().map(Enum::name).toList(),
            Arrays.stream(OcfExclusion.values()).map(Enum::name).toList()));
  }

  String navDates(List<LocalDate> navDates) {
    return jsonMapper.writeValueAsString(navDates.stream().map(LocalDate::toString).toList());
  }

  @JsonPropertyOrder({"gaps", "excluded"})
  record Checks(List<String> gaps, List<String> excluded) {}
}
