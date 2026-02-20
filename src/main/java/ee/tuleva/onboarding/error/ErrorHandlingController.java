package ee.tuleva.onboarding.error;

import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.*;
import static org.springframework.boot.web.error.ErrorAttributeOptions.of;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.core.convert.converter.Converter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequiredArgsConstructor
public class ErrorHandlingController implements ErrorController {

  private static final String PATH = "/error";

  private final ErrorAttributes errorAttributes;

  private final Converter<Map<String, Object>, ErrorsResponse> errorAttributesConverter;

  @RequestMapping(value = PATH)
  ErrorsResponse error(HttpServletRequest request) {
    WebRequest webRequest = new ServletWebRequest(request);
    Map<String, Object> errors =
        errorAttributes.getErrorAttributes(webRequest, of(STACK_TRACE, MESSAGE, STATUS, ERROR));
    return errorAttributesConverter.convert(errors);
  }
}
