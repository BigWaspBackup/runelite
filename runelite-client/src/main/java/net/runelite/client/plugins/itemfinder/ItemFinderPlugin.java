/*
 * Copyright (c) 2016-2018, Seth <Sethtroll3@gmail.com>
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
package net.runelite.client.plugins.itemfinder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import org.apache.commons.lang3.tuple.MutableTriple;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@PluginDescriptor(
        name = "ItemFinder"
)

public class ItemFinderPlugin extends Plugin {
    @Inject
    private ItemManager itemManager;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;

    private boolean SameImages(BufferedImage img1, BufferedImage img2) {
        if (img1.getWidth() != img2.getWidth()) return false;
        if (img1.getHeight() != img2.getHeight()) return false;

        for (int x = 0; x < img1.getWidth(); x++) {
            for (int y = 0; y < img1.getHeight(); y++) {
                if (img1.getRGB(x, y) != img2.getRGB(x, y))
                    return false;
            }
        }
        return true;
    }

    ArrayList<MutableTriple<Integer, String, BufferedImage>> items = new ArrayList<MutableTriple<Integer, String, BufferedImage>>();
    ArrayList<MutableTriple<Integer, String, BufferedImage>> filteredItems = new ArrayList<MutableTriple<Integer, String, BufferedImage>>();

    private void LoadAll() {

        ItemFinderCache cache = new ItemFinderCache();
        cache.Load();

        for (int id = 0; id < client.getItemCount(); id++) {
            if (!cache.ids.contains(id)) continue;
            if (id >= 5292 && id <= 5304) continue; // handle herb seeds
            items.add(new MutableTriple<>(id, cache.names.get(cache.ids.indexOf(id)), itemManager.getImage(id, 0, false)));
        }

        int idCount = client.getItemCount();

        for (int id = 5292; id < 5305; id++) {
            String name = cache.names.get(cache.ids.indexOf(id));
            items.add(new MutableTriple<>(++idCount, name, itemManager.getImage(5224, 0, false)));
            items.add(new MutableTriple<>(++idCount, name, itemManager.getImage(5225, 0, false)));
            items.add(new MutableTriple<>(++idCount, name, itemManager.getImage(5226, 0, false)));
            items.add(new MutableTriple<>(++idCount, name, itemManager.getImage(5227, 0, false)));
        }
    }

    private void Filter() {
        for (MutableTriple<Integer, String, BufferedImage> item : items) {
            boolean isDuplicate = false;
            for (MutableTriple<Integer, String, BufferedImage> filteredItem : filteredItems) {
                if (item.middle.equalsIgnoreCase(filteredItem.middle) && SameImages(item.right, filteredItem.right)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                filteredItems.add(item);
            }
        }
    }

    private boolean dumped = false;

    @Schedule(
            period = 3,
            unit = ChronoUnit.SECONDS
    )

    public void Dump() {
        if ((dumped) || (client.getGameState() != GameState.LOGIN_SCREEN)) {
            return;
        }
        System.out.println("ItemFinder starting...");
        clientThread.invoke(() ->
        {
            try {
                System.out.println("ItemFinder...");
                String dir = Paths.get(System.getProperty("user.dir") + File.separator + "itemfinder" + File.separator).toString();

                Path path = Paths.get(dir);
                if (!Files.isDirectory(path)) {
                    Files.createDirectory(path);
                }

                System.out.println("Saving to " + dir);

                System.out.println("Loading items...");
                LoadAll();
                System.out.println("Loaded " + items.size() + " items");

                System.out.println("Filtering items...");
                Filter();
                System.out.println("Filtered item count: " + filteredItems.size());

                ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir, "items-imgs.zip"))));

                FileWriter item = new FileWriter(new File(dir, "item.txt"));
                FileWriter id = new FileWriter(new File(dir, "id.txt"));

                for (MutableTriple<Integer, String, BufferedImage> filteredItem : filteredItems) {
                    zip.putNextEntry(new ZipEntry(filteredItem.left + ".png"));
                    ImageIO.write(filteredItem.right, "png", zip);

                    item.write(filteredItem.middle + System.lineSeparator());
                    id.write(filteredItem.left + System.lineSeparator());
                }

                zip.close();
                item.close();
                id.close();

                System.out.println("ItemFinder completed");
            } catch (Exception e) {
                System.out.println("ItemFinder exception:");
                log.error("e: ", e);
            }
        });

        dumped = true;
    }
}