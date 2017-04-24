package ee.tuleva.onboarding.error;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.web.ErrorAttributes;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.core.convert.converter.Converter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ErrorHandlingController implements ErrorController {

	private static final String PATH = "/error";

	private final ErrorAttributes errorAttributes;

	private final Converter<Map<String, Object>, ErrorsResponse> errorAttributesConverter;

	@RequestMapping(value = PATH)
	ErrorsResponse error(HttpServletRequest request) {
		RequestAttributes requestAttributes = new ServletRequestAttributes(request);
		Map<String, Object> errors = errorAttributes.getErrorAttributes(requestAttributes, false);
		return errorAttributesConverter.convert(errors);
	}

	@Override
	public String getErrorPath() {
		return PATH;
	}

}