package com.mmwtl.atlaslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class BoxBlurTest {
    @Test public void keepsAPlainImage() {
        int[] pixels = new int[6 * 4];
        Arrays.fill(pixels, 0xFF336699);
        BoxBlur.blur(pixels, 6, 4, 2);
        for (int pixel : pixels) assertEquals(0xFF336699, pixel);
    }

    @Test public void spreadsABrightPixelEvenly() {
        int width = 9, height = 7;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xFF000000);
        pixels[3 * width + 4] = 0xFFFFFFFF;
        BoxBlur.blur(pixels, width, height, 1);
        int center = pixels[3 * width + 4] & 0xFF;
        assertTrue(center > 0 && center < 255);
        assertEquals(pixels[3 * width + 3], pixels[3 * width + 5]);
        assertEquals(pixels[2 * width + 4], pixels[4 * width + 4]);
        assertTrue((pixels[3 * width + 3] & 0xFF) < center);
        for (int pixel : pixels) assertEquals(0xFF, pixel >>> 24);
    }
}
