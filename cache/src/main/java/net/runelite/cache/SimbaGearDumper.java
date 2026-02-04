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

import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.exporters.ItemExporter;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.providers.ItemProvider;
import net.runelite.cache.fs.*;
import net.runelite.cache.util.IDClass;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


public class SimbaGearDumper implements ItemProvider
{
	private final Store store;
	private final Map<Integer, ItemDefinition> items = new HashMap<>();

	public SimbaGearDumper(Store store)
	{
		this.store = store;
	}

	public void load() throws IOException
	{
		ItemLoader loader = new ItemLoader();

		Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.CONFIGS);
		Archive archive = index.getArchive(ConfigType.ITEM.getId());

		byte[] archiveData = storage.loadArchive(archive);
		ArchiveFiles files = archive.getFiles(archiveData);

		for (FSFile f : files.getFiles())
		{
			ItemDefinition def = loader.load(f.getFileId(), f.getContents());
			items.put(f.getFileId(), def);
		}
	}

	public void link()
	{
		for (ItemDefinition oc : items.values())
		{
			link(oc);
		}
	}

	private void link(ItemDefinition item)
	{
		if (item.notedTemplate != -1)
		{
			item.linkNote(getItem(item.notedTemplate), getItem(item.notedID));
		}
		if (item.boughtTemplateId != -1)
		{
			item.linkBought(getItem(item.boughtTemplateId), getItem(item.boughtId));
		}
		if (item.placeholderTemplateId != -1)
		{
			item.linkPlaceholder(getItem(item.placeholderTemplateId), getItem(item.placeholderId));
		}
	}

	public Collection<ItemDefinition> getItems()
	{
		return Collections.unmodifiableCollection(items.values());
	}

	public ItemDefinition getItem(int itemId)
	{
		return items.get(itemId);
	}

	public void export(File out) throws IOException
	{
		out.mkdirs();

		for (ItemDefinition def : items.values())
		{
			ItemExporter exporter = new ItemExporter(def);

			File targ = new File(out, def.id + ".json");
			exporter.exportTo(targ);
		}
	}

	public void java(File java) throws IOException
	{
		java.mkdirs();
		try (IDClass ids = IDClass.create(java, "ItemID");
			 IDClass nulls = IDClass.create(java, "NullItemID"))
		{
			for (ItemDefinition def : items.values())
			{
				if (def.name.equalsIgnoreCase("NULL"))
				{
					nulls.add(def.name, def.id);
				}
				else
				{
					ids.add(def.name, def.id);
				}
			}
		}
	}

	@Override
	public ItemDefinition provide(int itemId)
	{
		return getItem(itemId);
	}

	private void exportGearJson(File outDir) throws IOException
	{
		Map<String, List<String>> slots = new LinkedHashMap<>();
		slots.put("head", new ArrayList<>());
		slots.put("cape", new ArrayList<>());
		slots.put("neck", new ArrayList<>());
		slots.put("ammo", new ArrayList<>());
		slots.put("weapon", new ArrayList<>());
		slots.put("body", new ArrayList<>());
		slots.put("shield", new ArrayList<>());
		slots.put("legs", new ArrayList<>());
		slots.put("hands", new ArrayList<>());
		slots.put("feet", new ArrayList<>());
		slots.put("ring", new ArrayList<>());
		slots.put("2h", new ArrayList<>());

		for (ItemDefinition def : items.values())
		{
			int w1 = def.wearPos1;
			int w2 = def.wearPos2;
			int w3 = def.wearPos3;

			// skip non-equippable
			if (w1 == -1 && w2 == -1 && w3 == -1)
				continue;

			String name = def.name.toLowerCase();
			if (name.isBlank() || name.equalsIgnoreCase("null"))
				continue;

			List<Integer> positions = Arrays.asList(w1, w2, w3);

			boolean isWeapon = positions.contains(3);
			boolean isShield = positions.contains(5);

			// 2h = weapon + shield
			if (isWeapon && isShield)
			{
				slots.get("2h").add(name);
				continue;
			}

			for (int pos : positions)
			{
				switch (pos)
				{
					case 0: // Head
					case 8: // Hair
					case 11: // Jaw
						slots.get("head").add(name);
						break;

					case 1: // Cape
						slots.get("cape").add(name);
						break;

					case 2: // Amulet
						slots.get("neck").add(name);
						break;

					case 3: // Weapon
						slots.get("weapon").add(name);
						break;

					case 4: // Torso
						slots.get("body").add(name);
						break;

					case 5: // Shield
						slots.get("shield").add(name);
						break;

					case 7: // Legs
						slots.get("legs").add(name);
						break;

					case 9: // Hands
						slots.get("hands").add(name);
						break;

					case 10: // Boots
						slots.get("feet").add(name);
						break;

					case 12: // Ring
						slots.get("ring").add(name);
						break;

					case 13: // Ammo
						slots.get("ammo").add(name);
						break;

					default:
						break;
				}
			}
		}

		File file = new File(outDir, "gear.json");

		for (Map.Entry<String, List<String>> entry : slots.entrySet()) {
			List<String> list = entry.getValue();
			entry.setValue(new ArrayList<>(new LinkedHashSet<>(list)));
		}
		
		for (List<String> list : slots.values()) {
			Collections.sort(list);
		}

		try (FileWriter fw = new FileWriter(file))
		{
			fw.write("{\n");
			int i = 0;
			int size = slots.size();

			for (var entry : slots.entrySet())
			{
				fw.write("  \"" + entry.getKey() + "\": [");

				List<String> list = entry.getValue();
				for (int j = 0; j < list.size(); j++)
				{
					fw.write("\"" + list.get(j).replace("\"", "\\\"") + "\"");
					if (j < list.size() - 1) fw.write(", ");
				}

				fw.write("]");
				if (i++ < size - 1) fw.write(",");
				fw.write("\n");
			}

			fw.write("}\n");
		}
	}

	private static void dumpItems(Store store, File itemdir) throws IOException
	{
		SimbaGearDumper dumper = new SimbaGearDumper(store);
		dumper.load();
		dumper.exportGearJson(itemdir);
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
		final String outputDirectory = cmd.getOptionValue("outputdir") + File.separator + cacheName;

		File base = new File(cacheDirectory);
		File outDir = new File(outputDirectory);
		outDir.mkdirs();

		try (Store store = new Store(base))
		{
			store.load();
			System.out.println("Dumping items to " + outDir);
			dumpItems(store, outDir);
		}
	}
}