package ee.tuleva.onboarding.error.converter;

import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorResponse.ErrorResponseBuilder;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Component
public class InputErrorsConverter implements Converter<Errors, ErrorsResponse> {

	@Override
	public ErrorsResponse convert(Errors source) {
		List<ErrorResponse> globalErrors = source.getGlobalErrors().stream().map(this::convert).collect(toList());
		List<ErrorResponse> fieldErrors = source.getFieldErrors().stream().map(this::convert).collect(toList());
		List<ErrorResponse> allErrors = concat(globalErrors, fieldErrors);

		return new ErrorsResponse(allErrors);
	}

	private ErrorResponse convert(ObjectError error) {
		return genericConvert(error).build();
	}

	private ErrorResponse convert(FieldError error) {
		return genericConvert(error)
				.path(error.getField())
				.build();
	}

	private ErrorResponseBuilder genericConvert(ObjectError error) {
		return ErrorResponse.builder()
				.arguments(getArguments(error))
				.code(error.getCode())
				.message(error.getDefaultMessage());
	}

	/**
	 * For some reason Spring puts MessageSourceResolvable as argument so need to remove it
	 */
	private List<String> getArguments(ObjectError error) {
		if (error.getArguments() == null) {
			return emptyList();
		}
		return Arrays.stream(error.getArguments())
				.filter(arg -> !(arg instanceof MessageSourceResolvable))
				.map(Object::toString)
				.collect(toList());
	}

	private <E> List<E> concat(List<E> list1, List<E> list2) {
		List<E> concat = new ArrayList<>(list1);
		concat.addAll(list2);
		return concat;
	}

}
