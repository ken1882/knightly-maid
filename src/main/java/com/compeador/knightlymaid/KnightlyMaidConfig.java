package com.compeador.knightlymaid;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

/** Common config ({@code config/knightly_maid-common.toml}), also editable in game with config screen mods such as Configured */
public final class KnightlyMaidConfig {
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue FOLLOW_ACROSS_DIMENSIONS;
    private static final ModConfigSpec.BooleanValue VERBOSE_LOGGING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        FOLLOW_ACROSS_DIMENSIONS = builder
                .comment("Bring the owner's following maids (on foot or on their horse) along when the owner changes dimension.",
                        "Touhou Little Maid only lets them follow within a dimension. Maids seated on a dragon always come along.")
                .translation(KnightlyMaid.MODID + ".configuration.followAcrossDimensions")
                .define("followAcrossDimensions", true);

        VERBOSE_LOGGING = builder
                .comment("Log what the mod does with seated and riding maids (seating, teleports, dimension changes, blocked dismounts, saddle transfers).",
                        "Logged at INFO level, so it shows in latest.log. For troubleshooting; can be noisy.")
                .translation(KnightlyMaid.MODID + ".configuration.verboseLogging")
                .define("verboseLogging", false);

        SPEC = builder.build();
    }

    private KnightlyMaidConfig() { /* Nothing to do */ }

    static boolean followAcrossDimensions() {
        return !SPEC.isLoaded() || FOLLOW_ACROSS_DIMENSIONS.getAsBoolean();
    }

    /** Logs at INFO when verbose logging is on, so it shows without changing log levels. A trailing {@link Throwable} logs its stack trace. */
    static void verbose(final Logger logger, final String message, final Object... arguments) {
        if (SPEC.isLoaded() && VERBOSE_LOGGING.getAsBoolean()) {
            logger.info(message, arguments);
        }
    }
}
