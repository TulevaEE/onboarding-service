package ee.tuleva.onboarding.error.converter;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ErrorAttributesConverter implements Converter<Map<String, Object>, ErrorsResponse> {

  @Override
  public ErrorsResponse convert(Map<String, Object> errorAttributes) {
    return ErrorsResponse.ofSingleError(code(errorAttributes), message(errorAttributes));
  }

  private String code(Map<String, Object> errorAttributes) {
    String exception = (String) errorAttributes.get("exception");
    return StringUtils.substringAfterLast(exception, ".");
  }

  private String message(Map<String, Object> errorAttributes) {
    return (String) errorAttributes.get("message");
  }
}
