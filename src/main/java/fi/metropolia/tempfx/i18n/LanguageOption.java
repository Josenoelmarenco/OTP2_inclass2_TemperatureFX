package fi.metropolia.tempfx.i18n;

import java.util.Locale;

/**
 * One entry of the language selector. The label is written in the language
 * itself, so a user can always recognise their own language.
 *
 * @param locale      the locale to switch to
 * @param nativeLabel the language name in that language
 */
public record LanguageOption(Locale locale, String nativeLabel) {

    @Override
    public String toString() {
        return nativeLabel;
    }
}
