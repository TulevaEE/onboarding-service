package ee.tuleva.onboarding.error;

import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.MESSAGE;
import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.STACK_TRACE;
import static org.springframework.boot.web.error.ErrorAttributeOptions.of;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
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
        errorAttributes.getErrorAttributes(webRequest, of(STACK_TRACE, MESSAGE));
    return errorAttributesConverter.convert(errors);
  }

  @Override
  public String getErrorPath() {
    return PATH;
  }
}
