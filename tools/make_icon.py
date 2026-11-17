"""Draws Almanac's icon: a 32 x 32 pixel-art tree with one season per quarter of its canopy.

Usage: python3 tools/make_icon.py <icon.png> <preview.png>
Writes the 128 x 128 icon (src/main/resources/assets/almanac/icon.png) and a 384 x 384 preview.
"""
import math
import random
import sys
from PIL import Image

SIZE = 32
SCALE = 4
random.seed(7)

def hexc(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)

def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(4))

pixels = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]

def inside_rounded(x, y, radius=3):
    for cx, cy in ((radius, radius), (SIZE - 1 - radius, radius), (radius, SIZE - 1 - radius), (SIZE - 1 - radius, SIZE - 1 - radius)):
        if (x < radius and y < radius and cx == radius and cy == radius) or \
           (x > SIZE - 1 - radius and y < radius and cx != radius and cy == radius) or \
           (x < radius and y > SIZE - 1 - radius and cx == radius and cy != radius) or \
           (x > SIZE - 1 - radius and y > SIZE - 1 - radius and cx != radius and cy != radius):
            return (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2 + 1
    return True

# Sky: cool winter blue on the left fading to a warm summer blue on the right, paler toward the ground.
sky_left_top, sky_right_top = hexc(0x6f8fb8), hexc(0x4fa3e0)
sky_bottom = hexc(0xcfe6f5)
for y in range(SIZE):
    for x in range(SIZE):
        if inside_rounded(x, y):
            top = mix(sky_left_top, sky_right_top, x / (SIZE - 1))
            pixels[y][x] = mix(top, sky_bottom, (y / 22) ** 1.4 if y < 22 else 1)

def put(x, y, colour):
    if 0 <= x < SIZE and 0 <= y < SIZE and pixels[y][x][3]:
        pixels[y][x] = colour

# Sun, top right.
sun, sun_edge = hexc(0xffe36b), hexc(0xffb43c)
for dy in range(-2, 3):
    for dx in range(-2, 3):
        d = abs(dx) + abs(dy)
        if d <= 3:
            put(26 + dx, 5 + dy, sun if d <= 2 else sun_edge)

# Snowflakes, top left.
flake = hexc(0xffffff)
for fx, fy in ((3, 4), (8, 6), (4, 10), (2, 15), (9, 2), (6, 19), (12, 22)):
    put(fx, fy, flake)
    if (fx + fy) % 3 == 0:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(fx + dx, fy + dy, hexc(0xe4f0fa, 255))

# Ground: snow on the left half, grass on the right, dirt below.
snow_top, snow_shade = hexc(0xf4f8fc), hexc(0xc9d9e8)
grass_top, grass_shade = hexc(0x6cbf3e), hexc(0x4a9a2c)
dirt, dirt_dark = hexc(0x8a5a36), hexc(0x6b4428)
for y in range(24, SIZE):
    for x in range(SIZE):
        if not pixels[y][x][3]:
            continue
        snowy = x < 16
        if y == 24:
            colour = snow_top if snowy else grass_top
        elif y == 25:
            dripping = random.random() < 0.45
            colour = (snow_shade if snowy else grass_shade) if dripping else dirt
        else:
            colour = dirt_dark if random.random() < 0.3 else dirt
        put(x, y, colour)

# Trunk.
bark, bark_dark = hexc(0x6e4a2a), hexc(0x4e321b)
for y in range(15, 24):
    for x in range(14, 18):
        put(x, y, bark_dark if x in (14, 17) else bark)

# Canopy, one season per quarter: spring top left, summer top right, fall bottom right, winter bottom left.
seasons = {
    "spring": (hexc(0x93d66a), hexc(0x6fb648), hexc(0xb8ea8c), [hexc(0xf7a8cb), hexc(0xffd3e6)]),
    "summer": (hexc(0x3f9b3a), hexc(0x2b7a2a), hexc(0x5cb84d), []),
    "fall":   (hexc(0xe2802c), hexc(0xb9561f), hexc(0xf4a548), [hexc(0xc2361e), hexc(0xf2c84a)]),
    "winter": (hexc(0xeaf3fa), hexc(0xb7cadb), hexc(0xffffff), [hexc(0x8aa4bc), hexc(0x5e4630)]),
}
cx, cy = 15.5, 10.5
for y in range(0, 20):
    for x in range(SIZE):
        dx, dy = x - cx, (y - cy) * 1.15
        wobble = 0.55 * math.sin(math.atan2(dy, dx) * 6 + 0.4)
        distance = math.hypot(dx, dy)
        if distance > 10.2 + wobble:
            continue
        left, top = x < 16, y < 11
        season = "spring" if left and top else "summer" if top else "winter" if left else "fall"
        base, shade, light, accents = seasons[season]
        colour = base
        if distance > 8.6 + wobble or (dy > 0 and distance > 7 + wobble and dy > abs(dx) * 0.5):
            colour = shade
        elif dx + dy < -7 and distance < 6.5:
            colour = light
        if accents and random.random() < 0.16:
            colour = random.choice(accents)
        put(x, y, colour)


# A one-pixel darker frame like the other suite icons.
frame = hexc(0x1f2a36)
for y in range(SIZE):
    for x in range(SIZE):
        if not pixels[y][x][3]:
            continue
        edge = any(not (0 <= x + dx < SIZE and 0 <= y + dy < SIZE) or not pixels[y + dy][x + dx][3]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            pixels[y][x] = frame

image = Image.new("RGBA", (SIZE, SIZE))
for y in range(SIZE):
    for x in range(SIZE):
        image.putpixel((x, y), pixels[y][x])
image.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST).save(sys.argv[1])
image.resize((SIZE * 12, SIZE * 12), Image.NEAREST).save(sys.argv[2])
