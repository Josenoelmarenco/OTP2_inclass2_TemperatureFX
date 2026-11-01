package fi.metropolia.tempfx.i18n;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Internationalization support for the JavaFX application.
 *
 * <p>Holds the five supported languages, loads the matching UTF-8 resource
 * bundle and formats numbers following the conventions of each locale. Since
 * Java 9 {@link ResourceBundle} reads {@code .properties} files as UTF-8, so
 * the Japanese and Arabic texts are stored directly, without escaping.
 */
public final class LocalizationService {

    /** Base name shared by every resource bundle file. */
    public static final String BUNDLE_BASE_NAME = "MessagesBundle";

    /** Languages that are written from right to left. */
    private static final Set<String> RIGHT_TO_LEFT_LANGUAGES = Set.of("ar", "he", "fa", "ur");

    /** The five languages required by the assignment, in menu order. */
    private static final List<LanguageOption> OPTIONS = List.of(
            new LanguageOption(Locale.of("en", "US"), "English"),
            new LanguageOption(Locale.of("fi", "FI"), "Suomi"),
            new LanguageOption(Locale.of("sv", "SE"), "Svenska"),
            new LanguageOption(Locale.of("ja", "JP"), "日本語"),
            new LanguageOption(Locale.of("ar", "SA"), "العربية"));

    private LocalizationService() {
        // Utility class - not meant to be instantiated.
    }

    /** @return the five language options, in the order shown in the selector */
    public static List<LanguageOption> options() {
        return OPTIONS;
    }

    /** @return the five supported locales, in the same order */
    public static List<Locale> supportedLocales() {
        return OPTIONS.stream().map(LanguageOption::locale).toList();
    }

    /** @return the locale used when the application starts */
    public static Locale defaultLocale() {
        return OPTIONS.get(0).locale();
    }

    /**
     * Loads the resource bundle of one locale.
     *
     * @param locale the chosen locale
     * @return the bundle holding every translated text
     */
    public static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }

    /**
     * Finds the option belonging to a locale.
     *
     * @param locale the locale to look for
     * @return the matching option, or empty when the locale is not supported
     */
    public static Optional<LanguageOption> optionFor(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        return OPTIONS.stream()
                .filter(option -> option.locale().getLanguage().equals(locale.getLanguage()))
                .findFirst();
    }

    /**
     * Resolves a locale from a language code, so the application can also be
     * started in a given language from the command line or an environment
     * variable, for example {@code ar} or {@code ja_JP}.
     *
     * @param code the language tag
     * @return the matching locale, or empty when the code is unknown
     */
    public static Optional<Locale> byLanguageCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String language = code.trim().replace('-', '_').split("_")[0].toLowerCase(Locale.ROOT);
        return supportedLocales().stream()
                .filter(candidate -> candidate.getLanguage().equals(language))
                .findFirst();
    }

    /**
     * Tells whether the user interface has to be mirrored for this language.
     *
     * @param locale the locale to check
     * @return {@code true} for right-to-left languages such as Arabic
     */
    public static boolean isRightToLeft(Locale locale) {
        return locale != null && RIGHT_TO_LEFT_LANGUAGES.contains(locale.getLanguage());
    }

    /**
     * Looks up a translated text and fills in its placeholders.
     *
     * @param bundle    the bundle of the active locale
     * @param key       the key present in every MessagesBundle file
     * @param arguments the values replacing {0}, {1}, ...
     * @return the formatted, translated text
     */
    public static String message(ResourceBundle bundle, String key, Object... arguments) {
        String pattern = bundle.getString(key);
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, bundle.getLocale()).format(arguments);
    }

    /**
     * Formats a number the way the given locale writes it. Finnish, Swedish and
     * Arabic use a decimal comma, English and Japanese a decimal point.
     *
     * @param value  the number to display
     * @param locale the active locale
     * @return the localized representation, with at most two decimals
     */
    public static String formatNumber(double value, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(1);
        return format.format(value);
    }

    /**
     * Reads a number typed by the user, accepting both the locale notation
     * (for example "12,5" in Finnish) and the plain notation ("12.5").
     *
     * @param text   the raw text
     * @param locale the active locale
     * @return the parsed value
     * @throws NumberFormatException when the text is not a number
     */
    public static double parseNumber(String text, Locale locale) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new NumberFormatException("Empty input");
        }
        try {
            java.text.ParsePosition position = new java.text.ParsePosition(0);
            Number parsed = NumberFormat.getNumberInstance(locale).parse(trimmed, position);
            if (parsed != null && position.getIndex() == trimmed.length()) {
                return parsed.doubleValue();
            }
        } catch (RuntimeException ignored) {
            // Fall through to the plain notation below.
        }
        return Double.parseDouble(trimmed.replace(',', '.'));
    }
}
