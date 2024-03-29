package ee.tuleva.onboarding.error.converter;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ErrorAttributesConverter implements Converter<Map<String, Object>, ErrorsResponse> {

  @Override
  public ErrorsResponse convert(@NotNull Map<String, Object> errorAttributes) {
    return ErrorsResponse.ofSingleError(code(errorAttributes), message(errorAttributes));
  }

  private String code(Map<String, Object> errorAttributes) {
    return (String) errorAttributes.get("error");
  }

  private String message(Map<String, Object> errorAttributes) {
    return (String) errorAttributes.get("message");
  }
}
