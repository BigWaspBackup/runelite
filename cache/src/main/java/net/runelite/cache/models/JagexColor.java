/*
 * Copyright (c) 2020 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache.models;

public final class JagexColor
{
	public static final double BRIGHTNESS_MAX = .5;
	public static final double BRIGHTNESS_HIGH = .7;
	public static final double BRIGHTNESS_LOW = .8;
	public static final double BRIGHTNESS_MIN = .9;

	private static final double HUE_OFFSET = (.5 / 64.D);
	private static final double SATURATION_OFFSET = (.5 / 8.D);

	private JagexColor()
	{
	}

	public static short packHSL(int hue, int saturation, int luminance)
	{
		return (short) ((short) (hue & 63) << 10
			| (short) (saturation & 7) << 7
			| (short) (luminance & 127));
	}

	public static int packHSLFull(int hue, int saturation, int luminance)
	{
		return (hue & 0xFF) << 16
			| (saturation & 0xFF) << 8
			| (luminance & 0xFF);

	}

	public static int unpackHue(short hsl)
	{
		return hsl >> 10 & 63;
	}

	public static int unpackSaturation(short hsl)
	{
		return hsl >> 7 & 7;
	}

	public static int unpackLuminance(short hsl)
	{
		return hsl & 127;
	}

	public static int unpackHueFull(int hsl)
	{
		return (hsl >> 16 & 0xFF);
	}

	public static int unpackSaturationFull(int hsl)
	{
		return (hsl >> 8 & 0xFF);
	}

	public static int unpackLuminanceFull(int hsl)
	{
		return (hsl & 0xFF);
	}

	public static String formatHSL(short hsl)
	{
		return String.format("%02Xh%Xs%02Xl", unpackHue(hsl), unpackSaturation(hsl), unpackLuminance(hsl));
	}

	public static int HSLtoRGB(short hsl, double brightness)
	{
		double hue = (double) unpackHue(hsl) / 64.D + HUE_OFFSET;
		double saturation = (double) unpackSaturation(hsl) / 8.D + SATURATION_OFFSET;
		double luminance = (double) unpackLuminance(hsl) / 128.D;

		// This is just a standard hsl to rgb transform
		// the only difference is the offsets above and the brightness transform below
		double chroma = (1.D - Math.abs((2.D * luminance) - 1.D)) * saturation;
		double x = chroma * (1 - Math.abs(((hue * 6.D) % 2.D) - 1.D));
		double lightness = luminance - (chroma / 2);

		double r = lightness, g = lightness, b = lightness;
		switch ((int) (hue * 6.D))
		{
			case 0:
				r += chroma;
				g += x;
				break;
			case 1:
				g += chroma;
				r += x;
				break;
			case 2:
				g += chroma;
				b += x;
				break;
			case 3:
				b += chroma;
				g += x;
				break;
			case 4:
				b += chroma;
				r += x;
				break;
			default:
				r += chroma;
				b += x;
				break;
		}

		int rgb = ((int) (r * 256.0D) << 16)
			| ((int) (g * 256.0D) << 8)
			| (int) (b * 256.0D);

		rgb = adjustForBrightness(rgb, brightness);

		if (rgb == 0)
		{
			rgb = 1;
		}
		return rgb;
	}


	public static int HSLtoRGBFull(int hsl)
	{
		double hue = (double) unpackHueFull(hsl) / 256.D;
		double saturation = (double) unpackSaturationFull(hsl) / 256.D;
		double luminance = (double) unpackLuminanceFull(hsl) / 256.D;

		// This is just a standard hsl to rgb transform
		// the only difference is the offsets above and the brightness transform below
		double chroma = (1.D - Math.abs((2.D * luminance) - 1.D)) * saturation;
		double x = chroma * (1 - Math.abs(((hue * 6.D) % 2.D) - 1.D));
		double lightness = luminance - (chroma / 2);

		double r = lightness, g = lightness, b = lightness;
		switch ((int) (hue * 6.D))
		{
			case 0:
				r += chroma;
				g += x;
				break;
			case 1:
				g += chroma;
				r += x;
				break;
			case 2:
				g += chroma;
				b += x;
				break;
			case 3:
				b += chroma;
				g += x;
				break;
			case 4:
				b += chroma;
				r += x;
				break;
			default:
				r += chroma;
				b += x;
				break;
		}

		int rgb = ((((int) (r * 256.0D)) & 255) << 16)
				| ((((int) (g * 256.0D)) & 255) << 8)
				| ((int) (b * 256.0D)) & 255;

		if (rgb == 0)
		{
			rgb = 1;
		}
		return rgb;
	}

	public static int HSLtoRGBFull2(int hsl, double brightness)
	{
		double hue = (double) unpackHueFull(hsl) / 256.D;
		double saturation = (double) unpackSaturationFull(hsl) / 256.D;
		double luminance = (double) unpackLuminanceFull(hsl) / 256.D;

		// This is just a standard hsl to rgb transform
		// the only difference is the offsets above and the brightness transform below
		double chroma = (1.D - Math.abs((2.D * luminance) - 1.D)) * saturation;
		double x = chroma * (1 - Math.abs(((hue * 6.D) % 2.D) - 1.D));
		double lightness = luminance - (chroma / 2);

		double r = lightness, g = lightness, b = lightness;
		switch ((int) (hue * 6.D))
		{
			case 0:
				r += chroma;
				g += x;
				break;
			case 1:
				g += chroma;
				r += x;
				break;
			case 2:
				g += chroma;
				b += x;
				break;
			case 3:
				b += chroma;
				g += x;
				break;
			case 4:
				b += chroma;
				r += x;
				break;
			default:
				r += chroma;
				b += x;
				break;
		}

		int rgb = ((((int) (r * 256.0D)) & 255) << 16)
				| ((((int) (g * 256.0D)) & 255) << 8)
				| ((int) (b * 256.0D)) & 255;
		
		rgb = adjustForBrightness(rgb, brightness);

		if (rgb == 0)
		{
			rgb = 1;
		}
		return rgb;
	}


	public static int[] createPalette(double brightness) {
		return createPaletteHSV(brightness, 0, 65536);
	}

	public static int[] createPalette(double brightness, int min, int max)
	{
		int[] colorPalette = new int[65536];
		int var4 = min * 128;

		for (int var5 = min; var5 < max; ++var5)
		{
			double var6 = (double) (var5 >> 3) / 64.0D + 0.0078125D;
			double var8 = (double) (var5 & 7) / 8.0D + 0.0625D;

			for (int var10 = 0; var10 < 128; ++var10)
			{
				double var11 = (double) var10 / 128.0D;
				double var13 = var11;
				double var15 = var11;
				double var17 = var11;
				if (var8 != 0.0D)
				{
					double var19;
					if (var11 < 0.5D)
					{
						var19 = var11 * (1.0D + var8);
					}
					else
					{
						var19 = var11 + var8 - var11 * var8;
					}

					double var21 = 2.0D * var11 - var19;
					double var23 = var6 + 0.3333333333333333D;
					if (var23 > 1.0D)
					{
						--var23;
					}

					double var27 = var6 - 0.3333333333333333D;
					if (var27 < 0.0D)
					{
						++var27;
					}

					if (6.0D * var23 < 1.0D)
					{
						var13 = var21 + (var19 - var21) * 6.0D * var23;
					}
					else if (2.0D * var23 < 1.0D)
					{
						var13 = var19;
					}
					else if (3.0D * var23 < 2.0D)
					{
						var13 = var21 + (var19 - var21) * (0.6666666666666666D - var23) * 6.0D;
					}
					else
					{
						var13 = var21;
					}

					if (6.0D * var6 < 1.0D)
					{
						var15 = var21 + (var19 - var21) * 6.0D * var6;
					}
					else if (2.0D * var6 < 1.0D)
					{
						var15 = var19;
					}
					else if (3.0D * var6 < 2.0D)
					{
						var15 = var21 + (var19 - var21) * (0.6666666666666666D - var6) * 6.0D;
					}
					else
					{
						var15 = var21;
					}

					if (6.0D * var27 < 1.0D)
					{
						var17 = var21 + (var19 - var21) * 6.0D * var27;
					}
					else if (2.0D * var27 < 1.0D)
					{
						var17 = var19;
					}
					else if (3.0D * var27 < 2.0D)
					{
						var17 = var21 + (var19 - var21) * (0.6666666666666666D - var27) * 6.0D;
					}
					else
					{
						var17 = var21;
					}
				}

				int var29 = (int) (var13 * 256.0D);
				int var20 = (int) (var15 * 256.0D);
				int var30 = (int) (var17 * 256.0D);
				int var22 = var30 + (var20 << 8) + (var29 << 16);
				var22 = adjustForBrightness(var22, brightness);
				if (var22 == 0)
				{
					var22 = 1;
				}

				colorPalette[var4++] = var22;
			}
		}

		return colorPalette;
	}

	public static int[] createPaletteHSV(double brightness, int min, int max)
	{
		int[] colorPalette = new int[65536];

		for (int i = min; i < max; ++i)
		{
			double local40 = (double) (i >> 10 & 0x3F) / 64.0D + 0.0078125D;
			double local51 = (double) (i >> 7 & 0x7) / 8.0D + 0.0625D;
			double local58 = (double) (i & 0x7F) / 128.0D;

			double local60 = local58;
			double local62 = local58;
			double local64 = local58;

			if (local51 != 0.0D) {
				double local82;
				if (local58 < 0.5D) {
					local82 = (local51 + 1.0D) * local58;
				} else {
					local82 = local58 + local51 - local51 * local58;
				}

				double local95 = local58 * 2.0D - local82;
				double local99 = local40 + 0.3333333333333333D;
				if (local99 > 1.0D) {
					local99--;
				}

				double local113 = local40 - 0.3333333333333333D;
				if (local113 < 0.0D) {
					local113++;
				}
				if (local40 * 6.0D < 1.0D) {
					local62 = local95 + local40 * 6.0D * (local82 - local95);
				} else if (local40 * 2.0D < 1.0D) {
					local62 = local82;
				} else if (local40 * 3.0D < 2.0D) {
					local62 = (local82 - local95) * (-local40 + 0.6666666666666666D) * 6.0D + local95;
				} else {
					local62 = local95;
				}
				if (local99 * 6.0D < 1.0D) {
					local60 = local99 * 6.0D * (local82 - local95) + local95;
				} else if (local99 * 2.0D < 1.0D) {
					local60 = local82;
				} else if (local99 * 3.0D < 2.0D) {
					local60 = local95 + (local82 - local95) * 6.0D * (0.6666666666666666D - local99);
				} else {
					local60 = local95;
				}
				if (local113 * 6.0D < 1.0D) {
					local64 = local95 + (local82 - local95) * 6.0D * local113;
				} else if (local113 * 2.0D < 1.0D) {
					local64 = local82;
				} else if (local113 * 3.0D < 2.0D) {
					local64 = (0.6666666666666666D - local113) * (-local95 + local82) * 6.0D + local95;
				} else {
					local64 = local95;
				}
			}

			local60 = Math.pow(local60, brightness);
			local62 = Math.pow(local62, brightness);
			local64 = Math.pow(local64, brightness);

			int local290 = (int) (local60 * 256.0D);
			int local295 = (int) (local62 * 256.0D);
			int local300 = (int) (local64 * 256.0D);
			int local311 = local300 + (local290 << 16) + (local295 << 8);

			colorPalette[i] = local311;
		}

		return colorPalette;
	}

	public static int[] createPaletteHSL(double brightness, int min, int max)
	{
		int[] colorPalette = new int[65536];
		int local20 = 0;

		for (int local22 = min; local22 < max; local22++) {
			float local40 = ((float) (local22 >> 3) / 64.0F + 0.0078125F) * 360.0F;
			float local49 = (float) (local22 & 0x7) / 8.0F + 0.0625F;
			for (int local51 = 0; local51 < 128; local51++) {
				float local57 = (float) local51 / 128.0F;
				float local59 = 0.0F;
				float local61 = 0.0F;
				float local63 = 0.0F;
				float local67 = local40 / 60.0F;
				int local70 = (int) local67;
				int local74 = local70 % 6;
				float local79 = local67 - (float) local70;
				float local86 = local57 * (1.0F - local49);
				float local95 = local57 * (1.0F - local79 * local49);
				float local106 = (1.0F - local49 * (1.0F - local79)) * local57;
				if (local74 == 0) {
					local59 = local57;
					local63 = local86;
					local61 = local106;
				} else if (local74 == 1) {
					local63 = local86;
					local59 = local95;
					local61 = local57;
				} else if (local74 == 2) {
					local59 = local86;
					local61 = local57;
					local63 = local106;
				} else if (local74 == 3) {
					local61 = local95;
					local59 = local86;
					local63 = local57;
				} else if (local74 == 4) {
					local63 = local57;
					local59 = local106;
					local61 = local86;
				} else if (local74 == 5) {
					local61 = local86;
					local63 = local95;
					local59 = local57;
				}
				local59 = (float) Math.pow(local59, brightness);
				local61 = (float) Math.pow(local61, brightness);
				local63 = (float) Math.pow(local63, brightness);
				int local201 = (int) (local59 * 256.0F);
				int local206 = (int) (local61 * 256.0F);
				int local211 = (int) (local63 * 256.0F);
				int local224 = (local206 << 8) + (local201 << 16) + local211 - 16777216;
				colorPalette[local20++] = local224;
			}
		}

		return colorPalette;
	}

	public static int adjustForBrightness(int rgb, double brightness) {
		double var3 = (double)(rgb >> 16) / 256.0D; // L: 143
		double var5 = (double)(rgb >> 8 & 255) / 256.0D; // L: 144
		double var7 = (double)(rgb & 255) / 256.0D; // L: 145
		var3 = Math.pow(var3, brightness); // L: 146
		var5 = Math.pow(var5, brightness); // L: 147
		var7 = Math.pow(var7, brightness); // L: 148
		int var9 = (int)(var3 * 256.0D); // L: 149
		int var10 = (int)(var5 * 256.0D); // L: 150
		int var11 = (int)(var7 * 256.0D); // L: 151
		return var11 + (var10 << 8) + (var9 << 16); // L: 152
	}

	public static int getRGBFull(int hsl)
	{
		return HSLtoRGBFull(hsl);
	}

	public static int RGBtoBGR(int rgb){
		int r = (rgb >> 16) & 0xff;
		int g = (rgb >> 8) & 0xff;

        int bgr = rgb & 0xff;
		bgr = (bgr << 8) + g;
		bgr = (bgr << 8) + r;
		return bgr;
	}
}