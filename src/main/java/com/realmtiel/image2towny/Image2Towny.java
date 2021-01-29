package com.realmtiel.image2towny;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.InvalidNameException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class Image2Towny extends JavaPlugin {
    private static Image2Towny plugin;
    private static IOHandler iohandler;
    private TownyUniverse townyUniverse;
    public Image2Towny(){
        plugin=this;
    }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("Towny").isEnabled()) {
            this.townyUniverse = TownyUniverse.getInstance();
        }
        getLogger().info("[Image2Towny] Turning on..");

        iohandler=new IOHandler();

        //register command
        Objects.requireNonNull(this.getCommand("imgtowny")).setExecutor(new Image2TownyCommands());

    }
    @Override
    public void onDisable() {
        getLogger().info("[Image2Towny] Turning off..");
    }


    public static Image2Towny getPlugin() {
        return plugin;
    }
    public static IOHandler getIOHandler() {
        return iohandler;
    }
    public TownyUniverse getUniverse() {
        return this.townyUniverse;
    }
}
