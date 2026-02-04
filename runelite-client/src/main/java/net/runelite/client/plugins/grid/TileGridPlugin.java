package net.runelite.client.plugins.grid;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
        name = "Grid"
)
public class TileGridPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private TileGridConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private TileGridOverlay overlay;

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(overlay);
    }

    @Provides
    TileGridConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(TileGridConfig.class);
    }
}