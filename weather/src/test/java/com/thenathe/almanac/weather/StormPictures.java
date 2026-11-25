package com.thenathe.almanac.weather;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherHere;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Pictures for checking by eye, drawn from the weather field itself: maps of the storms over 16 km of land at several moments
 * and in each season, and the share of time under rain measured at places across the world against vanilla's.
 * Runs only with {@code -PalmanacPictures=<folder>}.
 */
class StormPictures {
    private static final Color SURFACE = new Color(0xfcfcfb);
    private static final Color INK = new Color(0x0b0b0b);
    private static final Color INK_SECONDARY = new Color(0x52514e);
    private static final Color GRID = new Color(0xe4e3df);
    /** Cloud shield, light rain, heavy rain: one hue, light to dark (sequential). */
    private static final Color SHIELD = new Color(0xcde2fb);
    private static final Color RAIN_LIGHT = new Color(0x86b6ef);
    private static final Color RAIN_HEAVY = new Color(0x1c5cab);
    /** Thunder, a second identity, marked in the legend too. */
    private static final Color THUNDER = new Color(0xeda100);
    private static final int SIDE = 16_384;
    private static final int PIXELS = 512;
    private static final long DAY = StormMathsV1.DAY;

    private static Path folder() {
        String folder = System.getProperty("almanac.pictures", "");
        assumeTrue(!folder.isBlank(), "pictures only when asked for");
        return Path.of(folder);
    }

    @Test
    void stormMapsAndRainShares() throws IOException {
        Path folder = folder();
        Files.createDirectories(folder);
        WeatherField spring = Worlds.field(Worlds.heldIn(SubSeason.MID_SPRING));
        long start = 30 * DAY;
        List<String> moments = new ArrayList<>();
        for (int hour = 0; hour < 4; hour++) {
            long tick = start + hour * 6_000L;
            moments.add(String.format(Locale.ROOT, "%d ticks later", hour * 6_000));
            write(map(spring, tick, "Mid spring, game tick " + tick + (hour == 0 ? "" : " (+" + hour * 6_000 + ")")),
                    folder.resolve(String.format(Locale.ROOT, "storms-spring-%d.png", hour)));
        }
        for (SubSeason subSeason : List.of(SubSeason.MID_SUMMER, SubSeason.MID_FALL, SubSeason.MID_WINTER)) {
            WeatherField field = Worlds.field(Worlds.heldIn(subSeason));
            write(map(field, start, subSeason.id().replace('_', ' ') + ", game tick " + start),
                    folder.resolve("storms-" + subSeason.season().name().toLowerCase(Locale.ROOT) + ".png"));
        }
        writeShares(folder);
    }

    /** A map of the rain and the storms over 16 km square at one tick, north up, with a scale and the wind. */
    private static BufferedImage map(WeatherField field, long tick, String title) {
        int margin = 40;
        BufferedImage image = new BufferedImage(PIXELS + 2 * margin, PIXELS + margin + 70, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(SURFACE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        double blocksPerPixel = SIDE / (double) PIXELS;
        double weatherTick = field.weatherTick(tick);
        List<Storm> storms = field.stormsNear(0, 0, SIDE, weatherTick);
        List<StormShape> shapes = storms.stream().map(StormShape::new).toList();
        for (int py = 0; py < PIXELS; py++) {
            for (int px = 0; px < PIXELS; px++) {
                double x = (px + 0.5) * blocksPerPixel - SIDE / 2.0;
                double z = (py + 0.5) * blocksPerPixel - SIDE / 2.0;
                WeatherHere here = field.sample(shapes, x, 64, z, tick, weatherTick);
                Color color = null;
                if (here.thunder() > 0) {
                    color = THUNDER;
                } else if (here.stormy()) {
                    color = blend(RAIN_LIGHT, RAIN_HEAVY, (here.storm() - 0.55) / 0.45);
                } else {
                    for (StormShape shape : shapes) {
                        if (shape.covers(x, z, weatherTick)) {
                            color = SHIELD;
                            break;
                        }
                    }
                }
                if (color != null) {
                    image.setRGB(margin + px, margin + py, color.getRGB());
                }
            }
        }
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(margin, margin, PIXELS, PIXELS);
        // Each storm's way: a short line from its centre, an hour of its drift.
        g.setColor(INK_SECONDARY);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Storm storm : storms) {
            double cx = storm.centreX(weatherTick);
            double cz = storm.centreZ(weatherTick);
            if (Math.abs(cx) > SIDE / 2.0 || Math.abs(cz) > SIDE / 2.0) {
                continue;
            }
            double sx = margin + (cx + SIDE / 2.0) / blocksPerPixel;
            double sz = margin + (cz + SIDE / 2.0) / blocksPerPixel;
            double ex = sx + storm.velocityX() * 6_000 / blocksPerPixel;
            double ez = sz + storm.velocityZ() * 6_000 / blocksPerPixel;
            g.draw(new Line2D.Double(sx, sz, ex, ez));
            g.fillOval((int) sx - 2, (int) sz - 2, 4, 4);
        }
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(title, margin, 26);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int base = margin + PIXELS + 22;
        // Scale: 2 km.
        int scale = (int) Math.round(2_000 / blocksPerPixel);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(margin, base, margin + scale, base);
        g.drawLine(margin, base - 4, margin, base + 4);
        g.drawLine(margin + scale, base - 4, margin + scale, base + 4);
        g.drawString("2 km", margin + scale + 8, base + 4);
        g.drawString("16 km square, north up; lines: where each storm moves in 6,000 ticks", margin + 140, base + 4);
        int key = base + 26;
        legend(g, margin, key, SHIELD, "cloud, nothing falls");
        legend(g, margin + 150, key, RAIN_LIGHT, "light rain or snow");
        legend(g, margin + 290, key, RAIN_HEAVY, "heavy");
        legend(g, margin + 370, key, THUNDER, "thunder");
        g.dispose();
        return image;
    }

    private static void legend(Graphics2D g, int x, int y, Color color, String text) {
        g.setColor(color);
        g.fill(new RoundRectangle2D.Double(x, y - 10, 14, 12, 4, 4));
        g.setColor(INK);
        g.drawString(text, x + 20, y);
    }

    private static Color blend(Color from, Color to, double along) {
        double t = Math.clamp(along, 0.0, 1.0);
        return new Color((int) (from.getRed() + (to.getRed() - from.getRed()) * t),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }

    /** The share of a year under rain at twelve places, against vanilla's 15.8 %, as a chart and as numbers. */
    private static void writeShares(Path folder) throws IOException {
        WeatherField year = Worlds.field(Worlds.year(8, 2));
        Random random = new Random(2026);
        List<double[]> places = new ArrayList<>();
        StringBuilder table = new StringBuilder("place x,place z,share of a year under rain (%),rain spells,mean spell (ticks)\n");
        long from = 10 * DAY;
        long to = from + 96 * DAY;
        for (int place = 0; place < 12; place++) {
            double x = (random.nextDouble() * 2 - 1) * 200_000;
            double z = (random.nextDouble() * 2 - 1) * 200_000;
            List<Spans.Span> spans = new ArrayList<>();
            year.forEachStorm(x, z, 0, from, to, storm -> spans.addAll(storm.rainSpans(x, z, from, to)));
            List<Spans.Span> joined = Spans.union(spans);
            double share = Spans.measure(joined, from, to) / (to - from);
            double meanSpell = joined.isEmpty() ? 0 : joined.stream().mapToDouble(Spans.Span::length).average().orElse(0);
            places.add(new double[] {x, z, share, joined.size(), meanSpell});
            table.append(String.format(Locale.ROOT, "%.0f,%.0f,%.2f,%d,%.0f%n", x, z, share * 100, joined.size(), meanSpell));
        }
        Files.writeString(folder.resolve("rain-share-by-place.csv"), table);
        int width = 720;
        int row = 30;
        int left = 170;
        int top = 84;
        int plot = width - left - 70;
        BufferedImage image = new BufferedImage(width, top + places.size() * row + 70, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(SURFACE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        double most = 0.30;
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString("Share of a year under rain or snow, twelve places across the world", 20, 28);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(INK_SECONDARY);
        g.drawString("Regional weather, a 96-day year of seasons, measured exactly from the storms", 20, 46);
        for (int tickMark = 0; tickMark <= 30; tickMark += 5) {
            int x = left + (int) (plot * tickMark / 100.0 / most);
            g.setColor(GRID);
            g.drawLine(x, top - 6, x, top + places.size() * row);
            g.setColor(INK_SECONDARY);
            g.drawString(tickMark + " %", x - 10, top + places.size() * row + 16);
        }
        // The reference line goes behind the bars, so nothing is drawn over a value.
        int vanilla = left + (int) (plot * StormCharacter.VANILLA_RAIN_SHARE / most);
        g.setColor(INK_SECONDARY);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {5f, 4f}, 0f));
        g.drawLine(vanilla, top - 8, vanilla, top + places.size() * row);
        g.drawString("vanilla 15.8 %", vanilla - 36, top - 14);
        g.setStroke(new BasicStroke(1f));
        for (int index = 0; index < places.size(); index++) {
            double[] place = places.get(index);
            int y = top + index * row;
            int length = (int) (plot * place[2] / most);
            g.setColor(new Color(0x2a78d6));
            g.fill(new RoundRectangle2D.Double(left, y + 6, length, row - 12, 4, 4));
            g.setColor(INK_SECONDARY);
            g.drawString(String.format(Locale.ROOT, "%.0f, %.0f", place[0], place[1]), 20, y + row / 2 + 4);
            String value = String.format(Locale.ROOT, "%.1f %%", place[2] * 100);
            int textX = Math.max(left + length + 6, vanilla + 6);
            g.setColor(INK);
            g.drawString(value, textX, y + row / 2 + 4);
        }
        g.setStroke(new BasicStroke(1f));
        g.setColor(INK_SECONDARY);
        g.drawString("place (block x, z)", 20, top + places.size() * row + 40);
        g.dispose();
        write(image, folder.resolve("rain-share-by-place.png"));
    }

    private static void write(BufferedImage image, Path path) throws IOException {
        ImageIO.write(image, "png", path.toFile());
    }
}
