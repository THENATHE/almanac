package com.thenathe.almanac.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A settings file with a typo is set aside, never saved over (E3). */
class SettingsFileTest {
    @TempDir
    Path folder;

    private SettingsFile<ServerConfig> file() {
        return new SettingsFile<>(() -> folder.resolve("almanac-server.json"), ServerConfig::fromJson, ServerConfig::toJson);
    }

    @Test
    void aReadableFileIsRead() throws IOException {
        Files.writeString(folder.resolve("almanac-server.json"), "{\"daysPerSeason\": 40}");
        SettingsFile.Reading.Read<?> read = assertInstanceOf(SettingsFile.Reading.Read.class, file().read());
        assertEquals(40, ((ServerConfig) read.settings()).daysPerSeason);
        assertInstanceOf(SettingsFile.Reading.Missing.class, new SettingsFile<>(() -> folder.resolve("none.json"),
                ServerConfig::fromJson, ServerConfig::toJson).read());
    }

    @Test
    void aBrokenFileIsSetAsideBeforeAnythingSavesOverIt() throws IOException {
        Path path = folder.resolve("almanac-server.json");
        String typo = "{\"daysPerSeason\": 40,, \"paused\": true}";
        Files.writeString(path, typo);
        SettingsFile<ServerConfig> file = file();

        SettingsFile.Reading.Unreadable<?> unreadable = assertInstanceOf(SettingsFile.Reading.Unreadable.class, file.read());
        Path aside = folder.resolve("almanac-server.json.broken");
        assertEquals(aside, unreadable.setAside().orElseThrow());
        assertEquals(typo, Files.readString(aside), "the hand edits are kept");
        assertFalse(Files.exists(path));

        // A save (a command, the settings screen) now writes a new file and leaves the broken one alone.
        file.save(new ServerConfig());
        assertTrue(Files.exists(path));
        assertEquals(typo, Files.readString(aside));
        assertEquals(24, file.readOr(ServerConfig::new).daysPerSeason);

        // A second broken file doesn't replace the first.
        Files.writeString(path, "not json");
        file.read();
        assertEquals(typo, Files.readString(aside));
        assertEquals("not json", Files.readString(folder.resolve("almanac-server.json.broken.2")));
    }

    @Test
    void anEmptyFileCountsAsBroken() throws IOException {
        Files.writeString(folder.resolve("almanac-server.json"), "");
        assertInstanceOf(SettingsFile.Reading.Unreadable.class, file().read());
        assertTrue(Files.exists(folder.resolve("almanac-server.json.broken")));
    }

    @Test
    void aBrokenFileThatCanNotBeMovedIsNotSavedOver() throws IOException {
        Path path = folder.resolve("almanac-server.json");
        Files.writeString(path, "{oops");
        SettingsFile<ServerConfig> file = file();
        // A folder nothing can be added to or removed from: the file can be read but not moved.
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            SettingsFile.Reading.Unreadable<?> unreadable = assertInstanceOf(SettingsFile.Reading.Unreadable.class, file.read());
            assertTrue(unreadable.setAside().isEmpty());
            file.save(new ServerConfig());
            assertEquals("{oops", Files.readString(path));
        } finally {
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        // Once it reads again, saving works again.
        Files.writeString(path, "{\"daysPerSeason\": 50}");
        assertEquals(50, file.readOr(ServerConfig::new).daysPerSeason);
        ServerConfig changed = new ServerConfig();
        changed.daysPerSeason = 60;
        file.save(changed);
        assertEquals(60, file.readOr(ServerConfig::new).daysPerSeason);
    }
}
