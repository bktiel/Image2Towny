package com.realmtiel.image2towny;

import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.InvalidNameException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.util.Colors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.realmtiel.image2towny.TerritoryAssign;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class Image2TownyCommands implements CommandExecutor, TabCompleter {
    private static final List<String> tabCompletes = Arrays.asList(
            "load"
    );
    private Image2Towny plugin;
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        this.plugin=Image2Towny.getPlugin();

        if (sender instanceof Player) {
            parseCommand((Player) sender, args);
        }
        return true;
    }

    //gives tab completions
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Player player=null;
        if (sender instanceof Player) {
            player = (Player) sender;
        }
        this.plugin=Image2Towny.getPlugin();
        //return list of possible commands if no passed args
        if(args.length==1){
            return tabCompletes;
        }

        if ("load".equalsIgnoreCase(args[0])) {
            try {
                List<String> dirContents = Files.list(this.plugin.getDataFolder().toPath())
                        .filter(file -> !Files.isDirectory(file))
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
                //TODO: plugin.yml should allow specification of valid formats, filter probably shouldn't be hardcoded
                switch(args.length){
                    case 2:
                        //suggest coords of player
                        if(player!=null){
                            return Arrays.asList(String.valueOf((int)(player).getLocation().getX()));
                        }
                    case 3:
                        if(player!=null) {
                            return Arrays.asList(String.valueOf((int)(player).getLocation().getZ()));
                        }
                    case 4:
                        //spicy lambda filter, get only valid uncompressed images
                        return dirContents.stream()
                                .filter(p->p.endsWith(".bmp") || p.endsWith(".tif"))
                                .collect(Collectors.toList());
                    case 5:
                        return dirContents.stream()
                                .filter(p->p.endsWith(".txt"))
                                .collect(Collectors.toList());
                    default:
                        return Collections.emptyList();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //if execution reaches here, nothing was found
        return Collections.emptyList();
    }

    private void parseCommand(final Player player, String[] split){
        try {
            if (split[0].equalsIgnoreCase("load") && split.length == 5) {
                //show load
                plugin.getLogger().info(
                        String.format("[Image2Towny] Beginning load of %s using list %s at start point (%s,%s)",
                                split[3],
                                split[4],
                                split[1],
                                split[2])
                );
                new TerritoryAssign(Integer.parseInt(split[1]), Integer.parseInt(split[2]), split[3], split[4]);
            }
            //print usage
            else {
                throw new Exception(String.format("Usage: /imgtowny load <x> <z> <list file> <image file>"));
            }
        } catch (Exception e) {
            player.sendMessage("Usage: /imgtowny load <x> <z> <list file> <image file>");
            e.printStackTrace();
        }
    }
}
