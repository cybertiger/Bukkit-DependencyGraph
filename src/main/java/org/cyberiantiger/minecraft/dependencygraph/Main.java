package org.cyberiantiger.minecraft.dependencygraph;

import com.google.common.base.Charsets;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    private DependencyGraph graph;

    @Override
    public void onEnable() {
        graph = new DependencyGraph();
        getDataFolder().mkdirs();
        for (Plugin plugin : getServer().getPluginManager().getPlugins()) {
            if (plugin.isEnabled()) {
                graph.addPluginDescription(plugin.getDescription());
            }
        }
        writeGraphs();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        graph.addPluginDescription(e.getPlugin().getDescription());
        writeGraphs();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) {
        graph.removePluginDescription(e.getPlugin().getDescription());
        writeGraphs();
    }

    private void writeGraphs() {
        File file;
        file = new File(getDataFolder(), "plugins.dot");
        try {
            Writer out = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);
            try {
                out.write(graph.toDot());
            } finally {
                out.close();
            }
        } catch (IOException ex) {
            getLogger().info("Failed to write dependency graph");
        }
        file = new File(getDataFolder(), "plugins_simple.dot");
        try {
            Writer out = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);
            try {
                out.write(graph.toSimpleDot());
            } finally {
                out.close();
            }
        } catch (IOException ex) {
            getLogger().info("Failed to write dependency graph");
        }
        file = new File(getDataFolder(), "plugins_circular.dot");
        try {
            Writer out = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);
            try {
                out.write(graph.toCircularDot());
            } finally {
                out.close();
            }
        } catch (IOException ex) {
            getLogger().info("Failed to write dependency graph");
        }
    }
}