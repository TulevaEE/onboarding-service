package ee.tuleva.onboarding.user.personalcode;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import static java.time.format.ResolverStyle.STRICT;
import static java.time.temporal.ChronoField.YEAR;

public class PersonalCode {

	public static int getAge(String personalCode) {
		LocalDate today = LocalDate.now();
		LocalDate birthDate = getBirthDate(personalCode);
		Period period = Period.between(birthDate, today);

		return period.getYears();
	}

	static LocalDate getBirthDate(String personalCode) {
		String century = personalCode.substring(0, 1);
		String birthDate = personalCode.substring(1, 7);
		return LocalDate.parse(birthDate, birthDateFormatter(century));
	}

	private static DateTimeFormatter birthDateFormatter(String century) {
		if (isBornInThe20thCentury(century)) {
			return formatterWithBaseYear(1900);
		}
		return DateTimeFormatter
			.ofPattern("uuMMdd")
			.withResolverStyle(STRICT);
	}

	private static boolean isBornInThe20thCentury(String centuryIndicator) {
		return "3".equals(centuryIndicator) || "4".equals(centuryIndicator);
	}

	private static DateTimeFormatter formatterWithBaseYear(int baseYear) {
		return new DateTimeFormatterBuilder()
				.appendValueReduced(YEAR, 2, 2, baseYear)
				.appendPattern("MMdd")
				.toFormatter()
				.withResolverStyle(STRICT);
	}
}
