package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * The weather sky behind the board (design/home-redesign.md, the approved scenes): clear, cloudy, rain or snow, each
 * with its day and night colours, blended by `night` (0 day, 1 night). Clouds and stars are alpha masks at half the
 * screen's width, made once per kind off the main thread (`build`) and drawn in the scene's colour, each depth sliding
 * sideways at its own speed; rain and snow are a few hundred lines and dots in three depths. The scenes were drawn at
 * 880 × 495: every position here is in those units.
 */
final class WeatherSky {
    // Which sky goes with the weather, and how dark it is when: Rules (tested on a computer).
    static final int CLEAR = Rules.CLEAR, CLOUDY = Rules.CLOUDY, RAIN = Rules.RAIN, SNOW = Rules.SNOW;
    private static final float SW = 880, SH = 495, MASK_H = 300;

    // Gradients: four stops top to bottom, day and night (cloudy, rain and snow share the night one).
    private static final int[][] DAY = {
        {0xFF7CC0F5, 0xFF5EAEF0, 0xFF4499E3, 0xFF3283D6},   // clear
        {0xFF66768C, 0xFF4C5A6E, 0xFF262F3D, 0xFF0B0F17},   // cloudy
        {0xFF6F7B8D, 0xFF626E80, 0xFF525D6E, 0xFF434C5A},   // rain (dark enough at the top for white text)
        {0xFF8C97A6, 0xFF737E8E, 0xFF3E4756, 0xFF0B0F17},   // snow
    };
    private static final float[][] DAY_AT = {{0, 0.35f, 0.7f, 1}, {0, 0.28f, 0.55f, 0.8f}, {0, 0.3f, 0.62f, 1}, {0, 0.26f, 0.56f, 0.84f}};
    private static final int[] CLEAR_NIGHT = {0xFF0F1B3D, 0xFF0C1530, 0xFF080C1C, 0xFF05070B};
    private static final int[] DARK_NIGHT = {0xFF141A28, 0xFF0E131E, 0xFF090C14, 0xFF05070B};
    private static final float[] NIGHT_AT = {0, 0.35f, 0.62f, 0.85f};

    // Clouds: {cx, cy, width, opacity, layer}, and each layer's blur, day colour and drift (px/s at 1920).
    private static final float[][][] CLOUDS = {
        {},
        {{150, 60, 380, 0.5f, 0}, {560, 40, 460, 0.45f, 0}, {820, 110, 300, 0.4f, 1}, {330, 150, 420, 0.35f, 2}, {700, 190, 360, 0.3f, 2}},
        {{180, 40, 460, 0.55f, 0}, {620, 30, 520, 0.55f, 0}, {430, 110, 420, 0.35f, 1}},
        {{200, 30, 480, 0.55f, 0}, {640, 50, 460, 0.5f, 0}},
    };
    private static final float[] LAYER_BLUR = {14, 12, 10};
    private static final float[] LAYER_SPEED = {5, 9, 14};
    private static final int[][] LAYER_DAY = {
        {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF},
        {0xFFC0C9D6, 0xFF9AA6B6, 0xFFCED6E0},
        {0xFF98A2B1, 0xFFAAB3C0, 0xFFAAB3C0},
        {0xFFC4CCD7, 0xFFC4CCD7, 0xFFC4CCD7},
    };
    private static final int[] LAYER_NIGHT = {0xFF3A4456, 0xFF323B4C, 0xFF3A4456};
    // Clear: high wisps {cx, cy, width, opacity}, by day only.
    private static final float[][] WISPS = {{120, 60, 300, 0.32f}, {420, 34, 220, 0.22f}, {300, 128, 260, 0.18f}, {610, 150, 200, 0.14f}};

    /** What `build` makes: one kind's masks. */
    static final class Masks {
        int kind;
        Bitmap[] layers = new Bitmap[3];   // clouds by depth (clear: wisps in 0), ALPHA_8
        Bitmap stars;                      // clear only
        // Rain or snow (far and near): tiles a quarter of the screen, at full resolution, that repeat across it and
        // slide. Lines drawn on every frame kept the TV's render thread at 41% of a core; tiles, ~10%. Two depths, not
        // three: each covers the whole screen, and the graphics chip pays per layer.
        Bitmap[] fall = new Bitmap[2];
        int width;
    }

    /** Rain: share of 260 drops, shortest, longest, width, faintest, brightest, speed (px/s at 1920). */
    private static final float[][] RAIN_LAYERS = {
        {0.55f, 10, 28, 0.9f, 0.14f, 0.36f, 800}, {0.45f, 24, 60, 1.8f, 0.24f, 0.55f, 1500}};
    /** Snow: flakes, smallest and largest radius, faintest, brightest, speed (px/s at 1920). */
    private static final float[][] SNOW_LAYERS = {{110, 0.8f, 2.2f, 0.35f, 0.8f, 40}, {18, 4, 8, 0.25f, 0.5f, 90}};
    private static final float WIND = 0.22f;   // the rain's slant: sideways per pixel down

    /** The masks for `kind` on a `w` × `h` screen: off the main thread (software drawing and blurs). */
    static Masks build(int kind, int w, int h) {
        Masks m = buildSky(kind, w);
        if (kind == RAIN || kind == SNOW) buildFall(m, kind, w, h);
        return m;
    }

    private static void buildFall(Masks m, int kind, int w, int h) {
        int tw = Math.max(1, w / 2), th = Math.max(1, h / 2);
        float sc = w / SW;
        Random rnd = new Random(909 + kind);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        float[][] layers = kind == RAIN ? RAIN_LAYERS : SNOW_LAYERS;
        for (int l = 0; l < layers.length; l++) {
            float[] d = layers[l];
            Bitmap tile = Bitmap.createBitmap(tw, th, Bitmap.Config.ALPHA_8);
            Canvas c = new Canvas(tile);
            // A quarter of the screen holds a quarter of the drops; each is drawn again past every edge it crosses,
            // so the tiles meet without a seam.
            int n = Math.round((kind == RAIN ? 260 * d[0] : d[0]) / 4);
            for (int i = 0; i < Math.max(1, n); i++) {
                float x = r(rnd, 0, tw), y = r(rnd, 0, th);
                p.setAlpha(Math.round(255 * r(rnd, kind == RAIN ? d[4] : d[3], kind == RAIN ? d[5] : d[4])));
                if (kind == RAIN) {
                    float len = r(rnd, d[1], d[2]) * sc;
                    p.setStrokeWidth(Math.max(1, d[3] * sc));
                    for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) {
                        float x0 = x + ox * tw, y0 = y + oy * th;
                        c.drawLine(x0, y0, x0 + len * WIND, y0 + len, p);
                    }
                } else {
                    float rad = r(rnd, d[1], d[2]) * sc * (l == 0 ? 0.6f : 1);
                    for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) c.drawCircle(x + ox * tw, y + oy * th, rad, p);
                }
            }
            if (l == 1) blur(tile, Math.round((kind == SNOW ? 3 : 0.7f) * sc));   // the near ones: out of focus
            m.fall[l] = tile;
        }
    }

    private static Masks buildSky(int kind, int w) {
        Masks m = new Masks();
        m.kind = kind;
        int mw = Math.max(1, w / 2), mh = Math.round(MASK_H * mw / SW);
        float s = mw / SW;
        m.width = mw;
        Random rnd = new Random(808 + kind);
        Bitmap scratch = Bitmap.createBitmap(mw, mh, Bitmap.Config.ALPHA_8);
        Canvas sc = new Canvas(scratch);
        Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint over = new Paint(Paint.FILTER_BITMAP_FLAG);
        RectF oval = new RectF();
        if (kind == CLEAR) {
            Bitmap layer = Bitmap.createBitmap(mw, mh, Bitmap.Config.ALPHA_8);
            Canvas lc = new Canvas(layer);
            for (float[] wisp : WISPS) {
                scratch.eraseColor(0);
                float cx = wisp[0], cy = wisp[1], ww = wisp[2];
                for (int i = 0; i < 5; i++) {
                    float x = cx + r(rnd, -ww * 0.3f, ww * 0.3f), y = cy + r(rnd, -8, 8);
                    float rx = r(rnd, ww * 0.25f, ww * 0.5f), ry = r(rnd, 3, 9), angle = r(rnd, -6, 2);
                    for (int copy = -1; copy <= 1; copy++) {
                        sc.save();
                        sc.rotate(angle, x * s + copy * mw, y * s);
                        oval.set((x - rx) * s + copy * mw, (y - ry) * s, (x + rx) * s + copy * mw, (y + ry) * s);
                        sc.drawOval(oval, solid);
                        sc.restore();
                    }
                }
                over.setAlpha(Math.round(255 * wisp[3]));
                lc.drawBitmap(scratch, 0, 0, over);
            }
            blur(layer, Math.round(8 * s));
            m.layers[0] = layer;
            m.stars = Bitmap.createBitmap(mw, mh, Bitmap.Config.ALPHA_8);
            Canvas st = new Canvas(m.stars);
            for (int i = 0; i < 90; i++) {
                float x = r(rnd, 0, SW), y = r(rnd, 0, SH * 0.55f), rad = r(rnd, 0.5f, 1.6f);
                solid.setAlpha(Math.round(255 * Math.max(0, r(rnd, 0.2f, 0.85f) * (1 - y / (SH * 0.7f)))));
                st.drawCircle(x * s, y * s, Math.max(0.6f, rad * s), solid);
            }
        } else {
            for (float[] cl : CLOUDS[kind]) {
                int li = (int) cl[4];
                if (m.layers[li] == null) m.layers[li] = Bitmap.createBitmap(mw, mh, Bitmap.Config.ALPHA_8);
                scratch.eraseColor(0);
                solid.setAlpha(255);
                float cx = cl[0], cy = cl[1], ww = cl[2];
                int n = 7 + rnd.nextInt(5);
                for (int i = 0; i <= n; i++) {
                    float x, y, rx, ry;
                    if (i == n) {   // the flat base that holds the puffs together
                        x = cx; y = cy + ww * 0.05f; rx = ww * 0.55f; ry = ww * 0.1f;
                    } else {
                        x = cx + r(rnd, -ww / 2, ww / 2);
                        rx = r(rnd, ww * 0.12f, ww * 0.28f);
                        y = cy + r(rnd, -ww * 0.06f, ww * 0.05f) - rx * 0.3f * (1 - Math.abs(x - cx) / (ww / 2 + 1));
                        ry = rx * r(rnd, 0.55f, 0.8f);
                    }
                    for (int copy = -1; copy <= 1; copy++) {   // drawn a screen to each side too: the layer wraps around
                        oval.set((x - rx) * s + copy * mw, (y - ry) * s, (x + rx) * s + copy * mw, (y + ry) * s);
                        sc.drawOval(oval, solid);
                    }
                }
                over.setAlpha(Math.round(255 * cl[3]));
                new Canvas(m.layers[li]).drawBitmap(scratch, 0, 0, over);
            }
            for (int li = 0; li < 3; li++) if (m.layers[li] != null) blur(m.layers[li], Math.round(LAYER_BLUR[li] * s));
        }
        scratch.recycle();
        return m;
    }

    private static float r(Random rnd, float a, float b) {
        return a + rnd.nextFloat() * (b - a);
    }

    /** About a Gaussian of `sigma`: three box blurs each way, wrapping sideways (the layer repeats) and not up and down. */
    private static void blur(Bitmap b, int sigma) {
        if (sigma < 1) return;
        int w = b.getWidth(), h = b.getHeight(), stride = b.getRowBytes();
        ByteBuffer buf = ByteBuffer.allocate(stride * h);
        b.copyPixelsToBuffer(buf);
        byte[] px = buf.array();
        int[] line = new int[Math.max(w, h)];
        int radius = sigma;
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < h; y++) {
                int row = y * stride, sum = 0;
                for (int i = -radius; i <= radius; i++) sum += px[row + Math.floorMod(i, w)] & 255;
                for (int x = 0; x < w; x++) {
                    line[x] = sum / (2 * radius + 1);
                    sum += (px[row + Math.floorMod(x + radius + 1, w)] & 255) - (px[row + Math.floorMod(x - radius, w)] & 255);
                }
                for (int x = 0; x < w; x++) px[row + x] = (byte) line[x];
            }
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int i = -radius; i <= radius; i++) sum += px[Math.max(0, Math.min(h - 1, i)) * stride + x] & 255;
                for (int y = 0; y < h; y++) {
                    line[y] = sum / (2 * radius + 1);
                    sum += (px[Math.min(h - 1, y + radius + 1) * stride + x] & 255) - (px[Math.max(0, y - radius) * stride + x] & 255);
                }
                for (int y = 0; y < h; y++) px[y * stride + x] = (byte) line[y];
            }
        }
        buf.rewind();
        b.copyPixelsFromBuffer(buf);
    }

    // MARK: - Drawing

    private Masks masks;
    private int w, h;
    private float sc, night = -1;
    private double time;   // seconds of motion: a double, so hours of drift stay smooth
    // Thunderstorms: when the next flash comes and when the last one started (seconds of motion; -1 = none).
    private boolean storm;
    private double nextFlash = -1, flashAt = -1;
    private final Random flashRandom = new Random(505);
    private final Paint sky = new Paint(), layer = new Paint(Paint.FILTER_BITMAP_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] layerColor = new int[3];
    private final Matrix matrix = new Matrix();
    private final Path crescent = new Path();
    private Shader sun, moonGlow, mist, city;
    private final int[] stops = new int[4];
    private final float[] at = new float[4];

    int kind() {
        return masks == null ? -1 : masks.kind;
    }

    /** The screen's size: the shaders. The masks follow with `build` at that size. */
    void setSize(int width, int height) {
        if (width == w && height == h) return;
        w = width;
        h = height;
        sc = w / SW;
        float ry = 300f / 420;
        sun = new RadialGradient(0, 0, 420 * sc, new int[] {0x99FFF4D2, 0x38FFE7A8, 0x00FFE7A8}, new float[] {0, 0.3f, 1}, Shader.TileMode.CLAMP);
        matrix.setScale(1, ry);
        matrix.postTranslate(820 * sc, -30 * sc);
        sun.setLocalMatrix(matrix);
        moonGlow = new RadialGradient(720 * sc, 80 * sc, 120 * sc, 0x59C9D6FF, 0x00C9D6FF, Shader.TileMode.CLAMP);
        crescent.reset();
        Path cut = new Path();
        crescent.addCircle(720 * sc, 80 * sc, 28 * sc, Path.Direction.CW);
        cut.addCircle(734 * sc, 72 * sc, 26 * sc, Path.Direction.CW);
        crescent.op(cut, Path.Op.DIFFERENCE);
        mist = new LinearGradient(0, 330 * h / SH, 0, h, 0x009AA6B6, 0x1F9AA6B6, Shader.TileMode.CLAMP);
        city = new LinearGradient(0, 380 * h / SH, 0, h, 0x00FF9F4A, 0x2EFF9F4A, Shader.TileMode.CLAMP);
        night = -1;
    }

    /** New masks (from `build`, on the main thread): the old ones are dropped. */
    void setMasks(Masks m) {
        masks = m;
        night = -1;
    }

    /** True when the colours changed (redraw). Makes the gradient: never while drawing. */
    boolean setNight(float n) {
        if (masks == null || w == 0 || Math.abs(n - night) < 0.002f) return false;
        night = n;
        int k = masks.kind;
        int[] dark = k == CLEAR ? CLEAR_NIGHT : DARK_NIGHT;
        for (int i = 0; i < 4; i++) {
            stops[i] = blend(DAY[k][i], dark[i], n);
            at[i] = DAY_AT[k][i] + (NIGHT_AT[i] - DAY_AT[k][i]) * n;
        }
        sky.setShader(new LinearGradient(0, 0, 0, h, stops, at, Shader.TileMode.CLAMP));
        for (int i = 0; i < 3; i++) layerColor[i] = blend(LAYER_DAY[k][i], k == CLEAR ? 0xFFFFFFFF : LAYER_NIGHT[i], n);
        return true;
    }

    /** Whether anything moves in this sky (rain, snow or clouds; the clear night is still). */
    boolean moves() {
        return masks != null && !(masks.kind == CLEAR && night > 0.99f);
    }

    /** Advances the drift and the rain or snow by `dt` seconds. */
    void step(float dt) {
        time += dt;
        if (storm && masks != null && masks.kind == RAIN) {
            if (nextFlash < 0) nextFlash = time + r(flashRandom, 3, 10);
            if (time >= nextFlash) {
                flashAt = time;
                nextFlash = time + r(flashRandom, 12, 45);   // now and then, never on a beat
            }
        }
    }

    /** Lightning now and then while it storms (Rules.isStorm). */
    void setStorm(boolean on) {
        if (on == storm) return;
        storm = on;
        nextFlash = flashAt = -1;
    }

    void draw(Canvas c) {
        if (masks == null || night < 0) {
            c.drawColor(0xFF0B0F17);
            return;
        }
        int k = masks.kind;
        float day = 1 - night;
        c.drawRect(0, 0, w, h, sky);
        if (k == CLEAR) {
            if (day > 0.01f) {
                glow.setShader(sun);
                glow.setAlpha(Math.round(255 * day));
                c.drawRect(0, 0, w, 300 * sc, glow);
            }
            if (night > 0.01f) {
                drawMask(c, masks.stars, 0xFFFFFFFF, night, 0);
                glow.setShader(moonGlow);
                glow.setAlpha(Math.round(255 * night));
                c.drawCircle(720 * sc, 80 * sc, 120 * sc, glow);
                glow.setShader(null);
                glow.setColor(Ui.alpha(0xFFF1F3F8, night));
                c.drawPath(crescent, glow);
            }
            if (day > 0.01f) drawMask(c, masks.layers[0], 0xFFFFFFFF, day, LAYER_SPEED[0] * 0.5f);
        } else {
            for (int i = 0; i < 3; i++) drawMask(c, masks.layers[i], layerColor[i], 1, LAYER_SPEED[i]);
        }
        if (k == RAIN && day > 0.01f) {
            glow.setShader(mist);
            glow.setAlpha(Math.round(255 * day));
            c.drawRect(0, 330 * h / SH, w, h, glow);
        }
        if (k != CLEAR && night > 0.01f) {
            glow.setShader(city);
            glow.setAlpha(Math.round(255 * night));
            c.drawRect(0, 380 * h / SH, w, h, glow);
        }
        glow.setShader(null);
        float px = w / 1920f;
        if (k == RAIN) {
            int color = blend(0xFFF2F6FB, 0xFF9FB2CC, night);
            for (int l = 0; l < 2; l++) {
                double v = RAIN_LAYERS[l][6] * px * time;
                // The wind pushes the near rain further than the far (parallax).
                drawFall(c, masks.fall[l], color, v * WIND + Rules.gust(time) * (l == 0 ? 50 : 90) * px, v);
            }
        } else if (k == SNOW) {
            for (int l = 0; l < 2; l++) {
                double v = SNOW_LAYERS[l][5] * px * time;
                drawFall(c, masks.fall[l], 0xFFFFFFFF, Math.sin(time * 0.4f + l) * 10 * sc + Rules.gust(time + 7) * (l == 0 ? 40 : 70) * px, v);
            }
        }
        if (flashAt >= 0) {
            float f = Rules.flash((float) (time - flashAt));
            if (f > 0) c.drawColor(Ui.alpha(0xFFE8EEFF, f * (0.2f + 0.22f * night)));   // stronger in the dark
        }
    }

    /** A rain or snow tile repeated over the screen, moved by (`dx`, `dy`) and wrapping around. */
    private void drawFall(Canvas c, Bitmap tile, int color, double dx, double dy) {
        if (tile == null) return;
        int tw = tile.getWidth(), th = tile.getHeight();
        float ox = (float) (((dx % tw) + tw) % tw), oy = (float) (((dy % th) + th) % th);
        layer.setColor(color);
        for (float y = oy - th; y < h; y += th) {
            for (float x = ox - tw; x < w; x += tw) c.drawBitmap(tile, x, y, layer);
        }
    }

    /** A mask stretched to the screen's width, in `color`, slid sideways by `speed` px/s (wrapping around). */
    private void drawMask(Canvas c, Bitmap mask, int color, float alpha, float speed) {
        if (mask == null) return;
        int mw = masks.width;
        float scale = (float) w / mw;
        float off = (float) ((time * speed * (w / 1920f) / scale) % mw);
        layer.setColor(Ui.alpha(color, alpha));
        c.save();
        c.scale(scale, scale);
        c.drawBitmap(mask, -off, 0, layer);
        c.drawBitmap(mask, mw - off, 0, layer);
        c.restore();
    }

    private static int blend(int a, int b, float f) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }
}
