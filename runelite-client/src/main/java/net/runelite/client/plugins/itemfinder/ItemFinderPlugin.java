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
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import org.apache.commons.lang3.tuple.MutableTriple;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static net.runelite.api.Constants.CLIENT_DEFAULT_ZOOM;

@Slf4j
@PluginDescriptor(
        name = "ItemFinder"
)

public class ItemFinderPlugin extends Plugin {
    private static final int BATCH_SIZE = 250;
    private static final int NULL_SPRITE_SKIP_TRIES = 30;
    // Last good dump had ~20k sprites with real pixels. Empty async placeholders are ~400.
    private static final int MIN_NONEMPTY_SPRITES = 5000;

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;

    private final ArrayList<MutableTriple<Integer, String, BufferedImage>> items = new ArrayList<>();
    private final ArrayList<MutableTriple<Integer, String, BufferedImage>> filteredItems = new ArrayList<>();
    private final ArrayList<PendingItem> pending = new ArrayList<>();

    private boolean started;
    private boolean pendingBuilt;
    private boolean seenSprite;
    private int nextIndex;
    private int nullTries;

    private static class PendingItem {
        final int outId;
        final int spriteId;
        final String name;

        PendingItem(int outId, int spriteId, String name) {
            this.outId = outId;
            this.spriteId = spriteId;
            this.name = name;
        }
    }

    private boolean sameImages(BufferedImage img1, BufferedImage img2) {
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

    private static boolean hasOpaquePixel(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void buildPending() {
        ItemFinderCache cache = new ItemFinderCache();
        cache.Load();

        for (int id = 0; id < client.getItemCount(); id++) {
            if (!cache.ids.contains(id)) continue;
            if (id >= 5292 && id <= 5304) continue; // handle herb seeds
            pending.add(new PendingItem(id, id, cache.names.get(cache.ids.indexOf(id))));
        }

        int idCount = client.getItemCount();
        for (int id = 5292; id < 5305; id++) {
            String name = cache.names.get(cache.ids.indexOf(id));
            pending.add(new PendingItem(++idCount, 5224, name));
            pending.add(new PendingItem(++idCount, 5225, name));
            pending.add(new PendingItem(++idCount, 5226, name));
            pending.add(new PendingItem(++idCount, 5227, name));
        }
    }

    private BufferedImage copySprite(SpritePixels sprite) {
        BufferedImage img = new BufferedImage(
                Constants.ITEM_SPRITE_WIDTH,
                Constants.ITEM_SPRITE_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        sprite.toBufferedImage(img);
        return img;
    }

    private BufferedImage emptySprite() {
        return new BufferedImage(
                Constants.ITEM_SPRITE_WIDTH,
                Constants.ITEM_SPRITE_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
    }

    private boolean loadBatch() {
        try {
            if (!pendingBuilt) {
                System.out.println("Loading items...");
                buildPending();
                pendingBuilt = true;
                System.out.println("Loaded " + pending.size() + " items");
            }

            if (pending.isEmpty()) {
                System.out.println("ItemFinder failed: no items from cache");
                return true;
            }

            int budget = BATCH_SIZE;
            while (budget-- > 0 && nextIndex < pending.size()) {
                PendingItem entry = pending.get(nextIndex);
                SpritePixels sprite = client.createItemSprite(
                        entry.spriteId, 0, 1, SpritePixels.DEFAULT_SHADOW_COLOR,
                        ItemQuantityMode.NEVER, false, CLIENT_DEFAULT_ZOOM);

                if (sprite == null) {
                    // Cache not ready yet: keep retrying the same item.
                    // After sprites have started resolving, skip ids that never get a sprite.
                    if (seenSprite && ++nullTries >= NULL_SPRITE_SKIP_TRIES) {
                        System.out.println("ItemFinder skip id " + entry.outId + " (no sprite)");
                        items.add(new MutableTriple<>(entry.outId, entry.name, emptySprite()));
                        nextIndex++;
                        nullTries = 0;
                    }
                    return false;
                }

                seenSprite = true;
                nullTries = 0;
                items.add(new MutableTriple<>(entry.outId, entry.name, copySprite(sprite)));
                nextIndex++;
            }

            if (nextIndex < pending.size()) {
                if (nextIndex % 2000 == 0) {
                    System.out.println("ItemFinder sprites: " + nextIndex + "/" + pending.size());
                }
                return false;
            }

            System.out.println("ItemFinder sprites: " + items.size() + "/" + pending.size());
            new Thread(this::filterAndWrite, "ItemFinder-writer").start();
            return true;
        } catch (Exception e) {
            System.out.println("ItemFinder exception:");
            log.error("e: ", e);
            return true;
        }
    }

    private void filter() {
        for (MutableTriple<Integer, String, BufferedImage> item : items) {
            boolean isDuplicate = false;
            for (MutableTriple<Integer, String, BufferedImage> filteredItem : filteredItems) {
                if (item.middle.equalsIgnoreCase(filteredItem.middle) && sameImages(item.right, filteredItem.right)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                filteredItems.add(item);
            }
        }
    }

    private void filterAndWrite() {
        try {
            int nonempty = 0;
            for (MutableTriple<Integer, String, BufferedImage> item : items) {
                if (hasOpaquePixel(item.right)) {
                    nonempty++;
                }
            }
            System.out.println("ItemFinder opaque sprites: " + nonempty + "/" + items.size());
            if (nonempty < MIN_NONEMPTY_SPRITES) {
                System.out.println("ItemFinder failed: only " + nonempty + "/" + items.size()
                        + " sprites have pixels (need >= " + MIN_NONEMPTY_SPRITES + ")");
                return;
            }

            System.out.println("Filtering items...");
            filter();
            System.out.println("Filtered item count: " + filteredItems.size());

            String dir = Paths.get(System.getProperty("user.dir") + File.separator + "itemfinder" + File.separator).toString();
            Path path = Paths.get(dir);
            if (!Files.isDirectory(path)) {
                Files.createDirectory(path);
            }
            System.out.println("Saving to " + dir);

            File zipFile = new File(dir, "items-imgs.zip");
            ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
            FileWriter item = new FileWriter(new File(dir, "item.txt"));
            FileWriter id = new FileWriter(new File(dir, "id.txt"));

            for (MutableTriple<Integer, String, BufferedImage> filteredItem : filteredItems) {
                zip.putNextEntry(new ZipEntry(filteredItem.left + ".png"));
                ImageIO.write(filteredItem.right, "png", zip);
                zip.closeEntry();

                item.write(filteredItem.middle + System.lineSeparator());
                id.write(filteredItem.left + System.lineSeparator());
            }

            zip.close();
            item.close();
            id.close();

            System.out.println("ItemFinder zip bytes: " + zipFile.length());
            System.out.println("ItemFinder completed");
        } catch (Exception e) {
            System.out.println("ItemFinder exception:");
            log.error("e: ", e);
        }
    }

    @Schedule(
            period = 3,
            unit = ChronoUnit.SECONDS
    )
    public void Dump() {
        if (started || client.getGameState() != GameState.LOGIN_SCREEN) {
            return;
        }
        started = true;
        System.out.println("ItemFinder starting...");
        clientThread.invoke(this::loadBatch);
    }
}
