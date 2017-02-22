package ee.tuleva.config;

import com.rollbar.Rollbar;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
class RollbarAdvice {
	private final Rollbar rollbar;

	public RollbarAdvice(Rollbar rollbar) {
		this.rollbar = rollbar;
	}

	@ExceptionHandler(value = Exception.class)
	public ModelAndView defaultErrorHandler(HttpServletRequest req, Exception e) throws Exception {
		if (AnnotationUtils.findAnnotation(e.getClass(), ResponseStatus.class) != null)
			throw e;

		rollbar.log(e);

		throw e;
	}
}