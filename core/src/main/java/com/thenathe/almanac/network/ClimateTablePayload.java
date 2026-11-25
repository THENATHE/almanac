package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.ClimateTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

/**
 * S2C, from 0.2.0: the server's {@link ClimateTable}, so the client's climate checks give the server's answers. Biomes
 * go by their ID in the biome registry, which the server syncs to its clients. Sent on joining, before the first
 * season sync, and again whenever the table is built anew (a climate setting changed, or the tags were reloaded).
 * About 5 KB for vanilla's 57 overworld biomes.
 */
public record ClimateTablePayload(List<Row> rows) implements CustomPacketPayload {
    private static final int FORMAT = 1;
    private static final int SNOW_SURFACE = 1;
    private static final int SEASONAL_ICE = 2;
    private static final int NO_SEASONAL_SNOW = 4;
    private static final int CAN_FALL = 1;
    private static final int OWN_TEMPERATURE = 2;

    public static final Type<ClimateTablePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "climate_table"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClimateTablePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(FORMAT);
                buffer.writeVarInt(payload.rows().size());
                for (Row row : payload.rows()) {
                    buffer.writeVarInt(row.biomeId());
                    buffer.writeByte(row.base().ordinal());
                    buffer.writeByte((row.snowSurface() ? SNOW_SURFACE : 0) | (row.seasonalIce() ? SEASONAL_ICE : 0)
                            | (row.noSeasonalSnow() ? NO_SEASONAL_SNOW : 0));
                    for (ClimateTable.Cell cell : row.cells()) {
                        buffer.writeByte((cell.canFall() ? CAN_FALL : 0) | (cell.ownTemperature() ? OWN_TEMPERATURE : 0));
                        buffer.writeFloat(cell.temperature());
                    }
                }
            },
            buffer -> {
                buffer.readVarInt();
                int count = buffer.readVarInt();
                List<Row> rows = new ArrayList<>(Math.min(count, 4096));
                for (int index = 0; index < count; index++) {
                    int biomeId = buffer.readVarInt();
                    Climate base = Climate.values()[Math.clamp(buffer.readByte(), 0, Climate.values().length - 1)];
                    int flags = buffer.readByte();
                    List<ClimateTable.Cell> cells = new ArrayList<>(SubSeason.COUNT);
                    for (int cell = 0; cell < SubSeason.COUNT; cell++) {
                        int cellFlags = buffer.readByte();
                        cells.add(new ClimateTable.Cell((cellFlags & CAN_FALL) != 0, buffer.readFloat(),
                                (cellFlags & OWN_TEMPERATURE) != 0));
                    }
                    rows.add(new Row(biomeId, base, (flags & SNOW_SURFACE) != 0, (flags & SEASONAL_ICE) != 0,
                            (flags & NO_SEASONAL_SNOW) != 0, cells));
                }
                PayloadFormat.skipNewerFields(buffer);
                return new ClimateTablePayload(rows);
            });

    /** One biome's row; see {@link ClimateTable.Entry}. */
    public record Row(int biomeId, Climate base, boolean snowSurface, boolean seasonalIce, boolean noSeasonalSnow,
                      List<ClimateTable.Cell> cells) {
    }

    public ClimateTablePayload {
        rows = List.copyOf(rows);
    }

    public static ClimateTablePayload of(ClimateTable table, Registry<Biome> registry) {
        List<Row> rows = new ArrayList<>(table.size());
        for (ClimateTable.Entry entry : table.entries()) {
            rows.add(new Row(registry.getId(entry.biome().value()), entry.base(), entry.snowSurface(),
                    entry.seasonalIce(), entry.noSeasonalSnow(), entry.cells()));
        }
        return new ClimateTablePayload(rows);
    }

    /** The table for the client's own biome registry; a biome the client doesn't know is left out. */
    public ClimateTable resolve(Registry<Biome> registry) {
        List<ClimateTable.Entry> entries = new ArrayList<>(rows.size());
        for (Row row : rows) {
            Optional<Holder.Reference<Biome>> biome = registry.get(row.biomeId());
            biome.ifPresent(holder -> entries.add(new ClimateTable.Entry(holder, row.base(), row.snowSurface(),
                    row.seasonalIce(), row.noSeasonalSnow(), row.cells())));
        }
        return ClimateTable.of(entries);
    }

    @Override
    public Type<ClimateTablePayload> type() {
        return TYPE;
    }
}
