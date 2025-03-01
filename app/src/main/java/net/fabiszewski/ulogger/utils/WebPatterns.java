/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.utils;

import java.util.regex.Pattern;

/**
 * This is based on java.android.util.Patterns
 * with WEB_URL pattern relaxed
 */

@SuppressWarnings({"RegExpUnnecessaryNonCapturingGroup,RegExpRedundantNestedCharacterClass", "RegExpSimplifiable", "UnnecessaryUnicodeEscape"})
class WebPatterns {

    /**
     * Protocols limited to http(s).
     */
    private static final String PROTOCOL = "(?i:https?)://";

    private static final String USER_INFO = "(?:[-a-zA-Z0-9$_.+!*'(),;?&=]|(?:%[a-fA-F0-9]{2})){1,64}"
            + "(?::(?:[-a-zA-Z0-9$_.+!*'(),;?&=]|(?:%[a-fA-F0-9]{2})){1,25})?@";

    /**
     * Valid UCS characters defined in RFC 3987. Excludes space characters.
     */
    private static final String UCS_CHAR = "[" +
            "\u00A0-\uD7FF" +
            "\uF900-\uFDCF" +
            "\uFDF0-\uFFEF" +
            "\uD800\uDC00-\uD83F\uDFFD" +
            "\uD840\uDC00-\uD87F\uDFFD" +
            "\uD880\uDC00-\uD8BF\uDFFD" +
            "\uD8C0\uDC00-\uD8FF\uDFFD" +
            "\uD900\uDC00-\uD93F\uDFFD" +
            "\uD940\uDC00-\uD97F\uDFFD" +
            "\uD980\uDC00-\uD9BF\uDFFD" +
            "\uD9C0\uDC00-\uD9FF\uDFFD" +
            "\uDA00\uDC00-\uDA3F\uDFFD" +
            "\uDA40\uDC00-\uDA7F\uDFFD" +
            "\uDA80\uDC00-\uDABF\uDFFD" +
            "\uDAC0\uDC00-\uDAFF\uDFFD" +
            "\uDB00\uDC00-\uDB3F\uDFFD" +
            "\uDB44\uDC00-\uDB7F\uDFFD" +
            "&&[^\u00A0[\u2000-\u200A]\u2028\u2029\u202F\u3000]]";

    /**
     * Valid characters for IRI label defined in RFC 3987.
     */
    private static final String LABEL_CHAR = "a-zA-Z0-9" + UCS_CHAR;

    /**
     * RFC 1035 Section 2.3.4 limits the labels to a maximum 63 octets.
     */
    private static final String IRI_LABEL = "[" + LABEL_CHAR + "]"
            + "(?:[" + LABEL_CHAR + "\\-]{0,61}[" + LABEL_CHAR + "])?";

    /**
     * Regular expression that matches domain names without a TLD, also IP addresses
     */
    private static final String RELAXED_DOMAIN_NAME =
            "(?:(?:" + IRI_LABEL + "(?:\\.(?=[" + LABEL_CHAR + "]))?)+)";

    private static final String PORT_NUMBER = ":\\d{1,5}";

    /**
     * A word boundary or end of input. This is to stop foo.sure from matching as foo.su
     */
    private static final String WORD_BOUNDARY = "(?:\\b|$|^)";

    /**
     * Path segment, exclude repeated slashes to rule out common error (http//example.com)
     */
    private static final String PATH_SEGMENT =
            "/(?:(?:[" + LABEL_CHAR + ";:@&=~\\-.+!*'(),_])|(?:%[a-fA-F0-9]{2})|" + WORD_BOUNDARY + ")+";

    /**
     * Regular expression pattern to match most part of RFC 3987
     * Internationalized URLs, aka IRIs.
     * Relaxed to accept domains without a TLD.
     * Will not accept query part.
     * Only http and https protocols.
     */
    static final Pattern WEB_URL_RELAXED = Pattern.compile(
            "(?:" + PROTOCOL + "(?:" + USER_INFO + ")?" + ")?"
                    + RELAXED_DOMAIN_NAME
                    + "(?:" + PORT_NUMBER + ")?"
                    + "(?:" + PATH_SEGMENT + ")*"
                    + WORD_BOUNDARY);

}