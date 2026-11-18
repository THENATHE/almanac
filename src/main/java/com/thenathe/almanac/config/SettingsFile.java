package com.thenathe.almanac.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One settings file in the config folder, read and written as JSON. A file that can't be read is moved aside as
 * {@code <name>.broken} (or {@code .broken.2} and so on, never over an older one) before anything is saved, so hand
 * edits with a typo are never overwritten; the log says where it went. If it can't be moved, saving is held back
 * until the file reads again.
 *
 * @param <T> the settings it holds
 */
public final class SettingsFile<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger("almanac");

    private final Supplier<Path> location;
    private final Function<String, @Nullable T> parse;
    private final Function<T, String> write;
    private boolean saveHeldBack;

    /**
     * @param parse returns null for text that isn't settings
     */
    public SettingsFile(Supplier<Path> location, Function<String, @Nullable T> parse, Function<T, String> write) {
        this.location = location;
        this.parse = parse;
        this.write = write;
    }

    public Path path() {
        return location.get();
    }

    /** What reading the file found. */
    public sealed interface Reading<T> {
        /** The file read as settings. */
        record Read<T>(T settings) implements Reading<T> {
        }

        /** There is no file. */
        record Missing<T>() implements Reading<T> {
        }

        /** The file couldn't be read; it is now at {@code setAside}, or still in place if moving it failed. */
        record Unreadable<T>(Optional<Path> setAside) implements Reading<T> {
        }
    }

    /** Reads the file; one that doesn't read is set aside first, and logged. */
    public Reading<T> read() {
        Path file = path();
        if (!Files.isRegularFile(file)) {
            saveHeldBack = false;
            return new Reading.Missing<>();
        }
        String problem;
        Exception failure = null;
        try {
            T read = parse.apply(Files.readString(file));
            if (read != null) {
                saveHeldBack = false;
                return new Reading.Read<>(read);
            }
            problem = "it isn't settings (empty, or not JSON)";
        } catch (IOException | RuntimeException exception) {
            problem = exception.toString();
            failure = exception;
        }
        try {
            Path aside = setAside(file);
            saveHeldBack = false;
            // The caller decides what is used instead (the defaults when starting, the live settings on a reload).
            LOGGER.warn("Couldn't read {}: {}. Moved it to {} so nothing overwrites it; fix it and put it back to use "
                    + "it.", file, problem, aside.getFileName(), failure);
            return new Reading.Unreadable<>(Optional.of(aside));
        } catch (IOException moveFailure) {
            saveHeldBack = true;
            LOGGER.warn("Couldn't read {}: {}, and couldn't move it aside ({}). Not saving over it until it reads "
                    + "again.", file, problem, moveFailure.toString(), failure);
            return new Reading.Unreadable<>(Optional.empty());
        }
    }

    /** The settings in the file, or {@code defaults} when there is none or it doesn't read. */
    public T readOr(Supplier<T> defaults) {
        return read() instanceof Reading.Read<T> read ? read.settings() : defaults.get();
    }

    /** Writes the settings, through a file beside it so a crash mid-write can't leave it half written. */
    public void save(T settings) {
        Path file = path();
        if (saveHeldBack) {
            LOGGER.warn("Not saving {}: it didn't read and couldn't be moved aside, so saving would lose it", file);
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, write.apply(settings));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            LOGGER.warn("Couldn't save {}", file, failure);
        }
    }

    /** Moves the file to the first free {@code .broken} name beside it. */
    private static Path setAside(Path file) throws IOException {
        String name = file.getFileName() + ".broken";
        Path aside = file.resolveSibling(name);
        for (int copy = 2; Files.exists(aside); copy++) {
            aside = file.resolveSibling(name + "." + copy);
        }
        return Files.move(file, aside);
    }
}
