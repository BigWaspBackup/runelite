/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
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
package net.runelite.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.KeyProvider;
import net.runelite.cache.util.XteaKeyManager;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class SimbaHeightMapDumper
{
	private static final Logger logger = LoggerFactory.getLogger(SimbaHeightMapDumper.class);
	private static final int MAP_SCALE = 4;
	private static final float MAX_HEIGHT = 6048f;
	public static boolean exportFullMap = false;
	private static boolean exportChunks = true;
	private static final boolean exportEmptyImages = true;
	private final Store store;
	private RegionLoader regionLoader;

	public SimbaHeightMapDumper(Store store)
	{
		this.store = store;
	}

	public void load(KeyProvider keyProvider) throws IOException
	{
		regionLoader = new RegionLoader(store, keyProvider);
		regionLoader.loadRegions();
		regionLoader.calculateBounds();
	}

	public BufferedImage drawRegions(int z, ZipOutputStream zip) throws IOException
	{
		int minX = regionLoader.getLowestX().getBaseX();
		int minY = regionLoader.getLowestY().getBaseY();
		int maxX = regionLoader.getHighestX().getBaseX() + Region.X;
		int maxY = regionLoader.getHighestY().getBaseY() + Region.Y;
		int dimX = (maxX - minX) * MAP_SCALE;
		int dimY = (maxY - minY) * MAP_SCALE;

		logger.info("Map image dimensions: {}px x {}px, {}px per map square ({} MB)", dimX, dimY, MAP_SCALE, (dimX * dimY / 1024 / 1024));

		BufferedImage image = new BufferedImage(dimX, dimY, BufferedImage.TYPE_BYTE_GRAY);
		drawRegions(image, z, zip);
		return image;
	}

	public static boolean isImageEmpty(BufferedImage img)
	{
		if (exportEmptyImages) return false;
		WritableRaster r = img.getRaster();
		int w = img.getWidth();
		int h = img.getHeight();
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++)
				if (r.getSample(x, y, 0) != 0)
					return false;
		return true;
	}

	private void drawRegions(BufferedImage image, int z, ZipOutputStream zip) throws IOException
	{
		WritableRaster raster = image.getRaster();
		int max = Integer.MIN_VALUE;
		int min = Integer.MAX_VALUE;

		for (Region region : regionLoader.getRegions())
		{
			int baseX = region.getBaseX();
			int baseY = region.getBaseY();
			int drawBaseX = baseX - regionLoader.getLowestX().getBaseX();
			int drawBaseY = regionLoader.getHighestY().getBaseY() - baseY;

			for (int x = 0; x < Region.X; ++x)
			{
				int drawX = drawBaseX + x;
				for (int y = 0; y < Region.Y; ++y)
				{
					int drawY = drawBaseY + Region.Y - 1 - y;
					int tileSetting = region.getTileSetting(z, x, y);
					int height = region.getTileHeight(z, x, y);

					if ((tileSetting & 24) == 0)
						if (z == 0 && (region.getTileSetting(1, x, y) & 2) != 0)
							height = region.getTileHeight(1, x, y);

					if (height > max) max = height;
					if (height < min) min = height;

					drawMapSquare(raster, drawX, drawY, toGray(height));
				}
			}

			if (exportChunks)
			{
				BufferedImage chunk = image.getSubimage(drawBaseX * MAP_SCALE, drawBaseY * MAP_SCALE, Region.X * MAP_SCALE, Region.Y * MAP_SCALE);
				if (!isImageEmpty(chunk))
				{
					zip.putNextEntry(new ZipEntry(z + "-" + region.getRegionX() + "-" + region.getRegionY() + ".png"));
					ImageIO.write(chunk, "png", zip);
				}
			}
		}

		System.out.println("max " + max);
		System.out.println("min " + min);
	}

	private int toGray(int height)
	{
		height = -height;
		int v = Math.round((height / MAX_HEIGHT) * 255f);
		if (v < 0) v = 0;
		if (v > 255) v = 255;
		return v;
	}

	private void drawMapSquare(WritableRaster raster, int x, int y, int gray)
	{
		x *= MAP_SCALE;
		y *= MAP_SCALE;
		for (int i = 0; i < MAP_SCALE; ++i)
			for (int j = 0; j < MAP_SCALE; ++j)
				raster.setSample(x + i, y + j, 0, gray);
	}

	public static void main(String[] args) throws IOException
	{
		long start = System.currentTimeMillis();
		Options options = new Options();
		options.addOption(Option.builder().longOpt("cachedir").hasArg().required().build());
		options.addOption(Option.builder().longOpt("cachename").hasArg().required().build());
		options.addOption(Option.builder().longOpt("outputdir").hasArg().required().build());

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		try
		{
			cmd = parser.parse(options, args);
		}
		catch (ParseException ex)
		{
			System.err.println("Error parsing command line options: " + ex.getMessage());
			System.exit(-1);
			return;
		}

		final String mainDir = cmd.getOptionValue("cachedir");
		final String cacheName = cmd.getOptionValue("cachename");
		final String cacheDirectory = mainDir + File.separator + cacheName + File.separator + "cache";
		final String xteaJSONPath = mainDir + File.separator + cacheName + File.separator + cacheName.replace("cache-", "keys-") + ".json";
		final String outputDirectory = cmd.getOptionValue("outputdir") + File.separator + cacheName;
		final String outputDirectoryEx = outputDirectory + File.separator + "heightmap";

		XteaKeyManager xteaKeyManager = new XteaKeyManager();
		if (Files.exists(Path.of(xteaJSONPath)))
		{
			try (FileInputStream fin = new FileInputStream(xteaJSONPath))
			{
				xteaKeyManager.loadKeys(fin);
			}
		}

		File base = new File(cacheDirectory);
		File outDir;

		if (exportFullMap) outDir = new File(outputDirectoryEx);
		else outDir = new File(outputDirectory);

		if (!outDir.exists() && !outDir.mkdirs())
			throw new RuntimeException("Failed to create output path: " + outDir.getPath());

		if (!exportFullMap) exportChunks = true;

		try (Store store = new Store(base))
		{
			store.load();

			SimbaHeightMapDumper dumper = new SimbaHeightMapDumper(store);
			dumper.load(xteaKeyManager);

			ZipOutputStream zip = null;
			if (exportChunks)
				zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File(outputDirectory, "heightmap.zip"))));

			for (int i = 0; i < Region.Z; ++i)
			{
				BufferedImage image = dumper.drawRegions(i, zip);
				if (exportFullMap)
				{
					File imageFile = new File(outDir, "img-" + i + ".png");
					ImageIO.write(image, "png", imageFile);
					log.info("Wrote image {}", imageFile);
				}
			}

			if (zip != null) zip.close();
		}

		long end = System.currentTimeMillis();
		System.out.println("SimbaHeightMapDumper took " + (end - start) + " ms");
	}
}