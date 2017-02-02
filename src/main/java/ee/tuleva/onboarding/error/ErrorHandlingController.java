package ee.tuleva.onboarding.error;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.web.ErrorAttributes;
import org.springframework.boot.autoconfigure.web.ErrorController;
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

	@RequestMapping(value = PATH)
	Map<String, Object> error(HttpServletRequest request) {
		RequestAttributes requestAttributes = new ServletRequestAttributes(request);
		Map<String, Object> errors = errorAttributes.getErrorAttributes(requestAttributes, false);
		renameMessageToErrorDescription(errors);
		return errors;
	}

	private void renameMessageToErrorDescription(Map<String, Object> errors) {
		Object message = errors.remove("message");
		errors.put("error_description", message);
	}

	@Override
	public String getErrorPath() {
		return PATH;
	}

}