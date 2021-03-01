package io.jenkins.tools.pluginmanager.cli;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import org.kohsuke.args4j.Localizable;

enum Messages implements Localizable {
    INVALID_CREDENTIALS_VALUE;

    public String formatWithLocale(Locale locale, Object... args) {
        ResourceBundle localized = ResourceBundle.getBundle(Messages.class.getName(), locale);
        return MessageFormat.format(localized.getString(name()),args);
    }

    public String format(Object... args) {
        return formatWithLocale(Locale.getDefault(),args);
    }
}
