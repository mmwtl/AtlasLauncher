package com.mmwtl.atlaslauncher;

/** Blur of a small ARGB image: RenderEffect needs Android 12, the head unit runs Android 11. */
final class BoxBlur {
    private BoxBlur() {
    }

    /** Three box passes in each direction come close to a Gaussian blur; the border pixels repeat past the edges. */
    static void blur(int[] pixels, int width, int height, int radius) {
        int[] buffer = new int[pixels.length];
        for (int pass = 0; pass < 3; pass++) {
            blurRows(pixels, buffer, width, height, radius);
            blurRows(buffer, pixels, height, width, radius);
        }
    }

    /** Writes each blurred row of source as a column of target, so a second call blurs the other direction. */
    private static void blurRows(int[] source, int[] target, int width, int height, int radius) {
        int size = 2 * radius + 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int a = 0, r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int pixel = source[row + clamp(i, width)];
                a += pixel >>> 24;
                r += (pixel >> 16) & 0xFF;
                g += (pixel >> 8) & 0xFF;
                b += pixel & 0xFF;
            }
            for (int x = 0; x < width; x++) {
                target[x * height + y] = (a + size / 2) / size << 24 | (r + size / 2) / size << 16
                        | (g + size / 2) / size << 8 | (b + size / 2) / size;
                int out = source[row + clamp(x - radius, width)];
                int in = source[row + clamp(x + radius + 1, width)];
                a += (in >>> 24) - (out >>> 24);
                r += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                g += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                b += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    private static int clamp(int index, int length) {
        return Math.max(0, Math.min(length - 1, index));
    }
}
