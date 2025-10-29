package ee.tuleva.onboarding.testsupport;

public class H2JsonFunctions {
  public static String jsonBuildObject(String... args) {
    if (args == null || args.length == 0) {
      return "{}";
    }

    if (args.length % 2 != 0) {
      throw new IllegalArgumentException("json_build_object requires an even number of arguments");
    }

    StringBuilder json = new StringBuilder("{");
    for (int i = 0; i < args.length; i += 2) {
      if (i > 0) {
        json.append(",");
      }
      String key = args[i];
      String value = args[i + 1];

      json.append("\"").append(key).append("\":");

      if (value == null || "null".equalsIgnoreCase(value)) {
        json.append("null");
      } else if (value.startsWith("\"") || value.startsWith("{") || value.startsWith("[")) {
        json.append(value);
      } else if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
        json.append(value.toLowerCase());
      } else if (isInteger(value)) {
        json.append(value);
      } else {
        json.append("\"").append(value).append("\"");
      }
    }
    json.append("}");
    return json.toString();
  }

  private static boolean isInteger(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    try {
      Integer.parseInt(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
