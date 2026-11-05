package com.chesslens.app.vision;

import android.graphics.Bitmap;
import java.util.Arrays;

/**
 * Finds an 8x8 checkerboard from screen captures.
 *
 * The detector scores the whole 64-square pattern rather than relying on a
 * single light/dark average. Per-square medians make it less sensitive to
 * pieces, move highlights and small UI details.
 */
public final class BoardDetector {
    public static final class Board {
        public final int left, top, side;
        public final float square;
        public final double confidence;

        Board(int x, int y, int s, double c) {
            left = x;
            top = y;
            side = s;
            square = s / 8f;
            confidence = c;
        }
    }

    public static Board locate(Bitmap src) {
        if (src == null || src.isRecycled()) return null;

        int ow = src.getWidth(), oh = src.getHeight();
        float scale = Math.min(1f, 420f / Math.max(ow, oh));
        int w = Math.max(64, Math.round(ow * scale));
        int h = Math.max(64, Math.round(oh * scale));
        Bitmap scaled = scale < .999f
                ? Bitmap.createScaledBitmap(src, w, h, true) : src;

        int[] pixels = new int[w * h];
        scaled.getPixels(pixels, 0, w, 0, 0, w, h);
        if (scaled != src) scaled.recycle();

        long[] integral = new long[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            long row = 0;
            for (int x = 0; x < w; x++) {
                int q = pixels[y * w + x];
                int lum = (int)(.299 * ((q >> 16) & 255)
                        + .587 * ((q >> 8) & 255) + .114 * (q & 255));
                row += lum;
                integral[(y + 1) * (w + 1) + x + 1] =
                        integral[y * (w + 1) + x + 1] + row;
            }
        }

        Candidate best = null;
        int minSide = Math.max(112, Math.min(w, h) / 3);
        int maxSide = (int)(Math.min(w, h) * .97f);
        int sizeStep = Math.max(3, (maxSide - minSide) / 28);

        for (int proposed = minSide; proposed <= maxSide; proposed += sizeStep) {
            int cell = proposed / 8;
            int side = cell * 8;
            if (cell < 8) continue;
            int posStep = Math.max(2, cell / 3);

            for (int y = 0; y + side <= h; y += posStep) {
                for (int x = 0; x + side <= w; x += posStep) {
                    double score = score(integral, w + 1, x, y, cell);
                    if (best == null || score > best.score) {
                        best = new Candidate(x, y, side, score);
                    }
                }
            }
        }

        // Confidence is a checker-pattern score in grayscale levels.
        if (best == null || best.score < 10.0) return null;
        return new Board(
                Math.round(best.x / scale),
                Math.round(best.y / scale),
                Math.round(best.side / scale),
                best.score
        );
    }

    private static double mean(long[] ii, int stride, int x, int y, int size) {
        int x2 = x + size, y2 = y + size;
        long sum = ii[y2 * stride + x2] - ii[y * stride + x2]
                - ii[y2 * stride + x] + ii[y * stride + x];
        return (double)sum / (size * size);
    }

    private static double median(double[] a) {
        Arrays.sort(a);
        int n = a.length;
        return n % 2 == 0 ? (a[n / 2 - 1] + a[n / 2]) * .5 : a[n / 2];
    }

    private static double score(long[] ii, int stride, int x, int y, int cell) {
        double[] even = new double[32], odd = new double[32];
        int ei = 0, oi = 0;
        int patch = Math.max(3, cell / 7);
        int inset = Math.max(patch, cell / 5);

        // Multiple samples per square reduce the effect of a piece centered
        // on one sample and avoid the borders between adjacent UI elements.
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                double[] samples = new double[5];
                int[][] offsets = {
                        {0, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
                };
                int cx = x + f * cell + cell / 2;
                int cy = y + r * cell + cell / 2;
                for (int k = 0; k < offsets.length; k++) {
                    int px = cx + offsets[k][0] * inset - patch / 2;
                    int py = cy + offsets[k][1] * inset - patch / 2;
                    samples[k] = mean(ii, stride, px, py, patch);
                }
                double value = median(samples);
                if (((r + f) & 1) == 0) even[ei++] = value;
                else odd[oi++] = value;
            }
        }

        double a = median(even), b = median(odd);
        double contrast = Math.abs(a - b);
        if (contrast < 1.0) return 0;

        // Count how consistently all squares agree with the two checker
        // colors. A few occupied/highlighted squares are allowed to disagree.
        double midpoint = (a + b) * .5;
        int agree = 0;
        for (double v : even) if ((a > b && v > midpoint) || (a < b && v < midpoint)) agree++;
        for (double v : odd) if ((b > a && v > midpoint) || (b < a && v < midpoint)) agree++;
        double consistency = agree / 64.0;

        // Penalize weak patterns, but keep tolerance for piece-heavy boards.
        return contrast * (.35 + .65 * consistency);
    }

    private static final class Candidate {
        final int x, y, side;
        final double score;
        Candidate(int x, int y, int side, double score) {
            this.x = x; this.y = y; this.side = side; this.score = score;
        }
    }
}
