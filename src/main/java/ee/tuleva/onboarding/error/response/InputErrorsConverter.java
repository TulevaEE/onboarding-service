package ee.tuleva.onboarding.error.response;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Component
public class InputErrorsConverter implements Converter<Errors, ErrorsResponse> {

	@Override
	public ErrorsResponse convert(Errors source) {
		List<ErrorResponse> globalErrors = source.getGlobalErrors().stream().map(this::convert).collect(toList());
		List<ErrorResponse> fieldErrors = source.getFieldErrors().stream().map(this::convert).collect(toList());

		List<ErrorResponse> allErrors = new ArrayList<>(globalErrors);
		allErrors.addAll(fieldErrors);
		return new ErrorsResponse(allErrors);
	}

	private ErrorResponse convert(ObjectError error) {
		return genericConvert(error).build();
	}

	private ErrorResponse convert(FieldError error) {
		ErrorResponse.ErrorResponseBuilder builder = genericConvert(error);
		builder.path(error.getField());
		return builder.build();
	}

	private ErrorResponse.ErrorResponseBuilder genericConvert(ObjectError error) {
		ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder();

		builder.arguments(Arrays.stream(getArguments(error)).map(Object::toString).collect(toList()));
		builder.code(error.getCode());
		builder.message(error.getDefaultMessage());
		return builder;
	}

	/**
	 * For some reason Spring puts MessageSourceResolvable as argument so need to remove it
	 */
	private Object[] getArguments(ObjectError error) {
		if (error.getArguments() == null) {
			return new Object[0];
		}
		return Arrays.stream(error.getArguments()).filter(arg -> !(arg instanceof MessageSourceResolvable)).collect(Collectors.toList()).toArray();
	}

}
