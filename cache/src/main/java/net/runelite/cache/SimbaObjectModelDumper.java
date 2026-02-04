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

import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.exporters.ObjectExporter;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.*;
import net.runelite.cache.models.ObjExporter;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class SimbaObjectModelDumper
{
	private final Store store;
	private static Index index;
	private static TextureManager textureManager;
	private final ModelLoader modelLoader;
	private final Map<Integer, ObjectDefinition> objects = new HashMap<>();

	public SimbaObjectModelDumper(Store store) throws IOException {
		this.store = store;
		this.modelLoader = new ModelLoader();
	}

	public void load() throws IOException
	{
		ObjectLoader loader = new ObjectLoader();

		Storage storage = store.getStorage();
		Index configsIndex = store.getIndex(IndexType.CONFIGS);
		Archive archive = configsIndex.getArchive(ConfigType.OBJECT.getId());

		index = store.getIndex(IndexType.MODELS);
		textureManager = new TextureManager(store);
		textureManager.load();

		byte[] archiveData = storage.loadArchive(archive);
		ArchiveFiles files = archive.getFiles(archiveData);

		for (FSFile f : files.getFiles())
		{
			ObjectDefinition def = loader.load(f.getFileId(), f.getContents());
			objects.put(f.getFileId(), def);
		}
	}

	public Collection<ObjectDefinition> getObjects()
	{
		return Collections.unmodifiableCollection(objects.values());
	}

	public ObjectDefinition getObject(int objectID)
	{
		return objects.get(objectID);
	}

	public void export(File out) throws IOException
	{
		out.mkdirs();

		for (ObjectDefinition def : objects.values())
		{
			int height = 0;

			if (def.getObjectModels() != null) {
				for (int i = 0; i < def.getObjectModels().length; i++) {
					int modelId = def.getObjectModels()[i];
					Archive archive = index.getArchive(modelId);
					if (archive == null)
						continue;

					byte[] contents = archive.decompress(store.getStorage().loadArchive(archive));
					ModelDefinition model = modelLoader.load(archive.getArchiveId(), contents);

					ObjExporter exporter = new ObjExporter(textureManager, model);


					File objFile = new File(out, def.getId() + ".obj");
					File mtlFile = new File(out, def.getId() + ".mtl");
					PrintWriter objPrinter = new PrintWriter(objFile);
					PrintWriter mtlPrinter = new PrintWriter(mtlFile);
					exporter.export(objPrinter, mtlPrinter);
					objPrinter.close();
					mtlPrinter.close();
				}
			}

			ObjectExporter exporter = new ObjectExporter(def);

			File targ = new File(out, def.getId() + ".json");
			exporter.simbaExportTo(targ);
		}
	}

	public ObjectDefinition provide(int objectID)
	{
		return getObject(objectID);
	}

	private static void dumpObjects(Store store, File objdir) throws IOException
	{
		SimbaObjectModelDumper dumper = new SimbaObjectModelDumper(store);
		dumper.load();
		dumper.export(objdir);
	}

	public static void main(String[] args) throws IOException
	{
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
		final String outputDirectory = cmd.getOptionValue("outputdir") + File.separator + cacheName + File.separator + "Objects";

		File base = new File(cacheDirectory);
		File outDir = new File(outputDirectory);
		outDir.mkdirs();

		try (Store store = new Store(base))
		{
			store.load();

			System.out.println("Dumping Objects to " + outDir);
			dumpObjects(store, outDir);
		}
	}
}