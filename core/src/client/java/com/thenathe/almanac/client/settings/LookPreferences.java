package com.thenathe.almanac.client.settings;

/**
 * Where the screen keeps the player's look choices. Each mod stores these in its own config,
 * so changing the look in one mod never restyles another's screen.
 */
public interface LookPreferences {
    SettingsStyle style();

    void setStyle(SettingsStyle style);

    boolean light();

    void setLight(boolean light);

    Accent accent();

    void setAccent(Accent accent);

    default Look look() {
        return new Look(style(), HouseColors.of(light(), accent()));
    }
}
