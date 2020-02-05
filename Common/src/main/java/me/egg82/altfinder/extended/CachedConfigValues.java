package me.egg82.altfinder.extended;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import me.egg82.altfinder.messaging.Messaging;
import me.egg82.altfinder.storage.Storage;

public class CachedConfigValues {
    private CachedConfigValues() {}

    private ImmutableList<Storage> storage = ImmutableList.of();
    public ImmutableList<Storage> getStorage() { return storage; }

    private ImmutableList<Messaging> messaging = ImmutableList.of();
    public ImmutableList<Messaging> getMessaging() { return messaging; }

    private boolean debug = false;
    public boolean getDebug() { return debug; }

    private Locale language = Locale.US;
    public Locale getLanguage() { return language; }

    private String totalKickMessage = "&cPlease do not use more than {max} alt accounts!";
    public String getTotalKickMessage() { return totalKickMessage; }

    private ImmutableList<String> totalActionCommands = ImmutableList.of();
    public ImmutableList<String> getTotalActionCommands() { return totalActionCommands; }

    private String currentKickMessage = "&cPlease disconnect from one of your alts before re-joining!";
    public String getCurrentKickMessage() { return currentKickMessage; }

    private ImmutableList<String> currentActionCommands = ImmutableList.of();
    public ImmutableList<String> getCurrentActionCommands() { return currentActionCommands; }

    public static CachedConfigValues.Builder builder() { return new CachedConfigValues.Builder(); }

    public static class Builder {
        private final CachedConfigValues values = new CachedConfigValues();

        private Builder() {}

        public CachedConfigValues.Builder debug(boolean value) {
            values.debug = value;
            return this;
        }

        public CachedConfigValues.Builder language(Locale value) {
            values.language = value;
            return this;
        }

        public CachedConfigValues.Builder storage(List<Storage> value) {
            values.storage = ImmutableList.copyOf(value);
            return this;
        }

        public CachedConfigValues.Builder messaging(List<Messaging> value) {
            values.messaging = ImmutableList.copyOf(value);
            return this;
        }

        public CachedConfigValues.Builder totalKickMessage(String value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.totalKickMessage = value;
            return this;
        }

        public CachedConfigValues.Builder totalActionCommands(Collection<String> value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.totalActionCommands = ImmutableList.copyOf(value);
            return this;
        }

        public CachedConfigValues.Builder currentKickMessage(String value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.currentKickMessage = value;
            return this;
        }

        public CachedConfigValues.Builder currentActionCommands(Collection<String> value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.currentActionCommands = ImmutableList.copyOf(value);
            return this;
        }

        public CachedConfigValues build() { return values; }
    }
}
