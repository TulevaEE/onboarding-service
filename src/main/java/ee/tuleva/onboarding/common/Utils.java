package ee.tuleva.onboarding.common;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;

public class Utils {

    public static Instant parseInstant(String format) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(format).toInstant();
        } catch (ParseException e) {
            e.printStackTrace();
            return Instant.now();
        }
    }
}
