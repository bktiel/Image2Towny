package com.realmtiel.image2towny;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.InvalidNameException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class TerritoryAssign {
    private static final int DEFAULT_REGION_SIZE=512;
    private final Image2Towny pluginInstance;


    public TerritoryAssign(String imageFileName, String listFileName) throws AlreadyRegisteredException, NotRegisteredException{
        this(0,0,imageFileName,listFileName,DEFAULT_REGION_SIZE);
    }
    public TerritoryAssign(int x, int z, String imageFileName, String listFileName) throws AlreadyRegisteredException, NotRegisteredException{
        this(x,z,imageFileName,listFileName,DEFAULT_REGION_SIZE);
    }
    /*
    Given starting X and Z coordinates, read passed imageFileName in chunks of regionSize and match hex colored pixels
    to towns mapped to those colors in listFileName
    Creates a Town of each object and prepares an SQL statement for all related townblocks per Town.
     */
    public TerritoryAssign(int startX, int startZ, String imageFileName, String listFileName, int regionSize) throws AlreadyRegisteredException, NotRegisteredException {
        //grab pluginInstance items
        this.pluginInstance=Image2Towny.getPlugin();
        imageFileName=pluginInstance.getDataFolder().getPath()+File.separator+imageFileName;
        listFileName=pluginInstance.getDataFolder().getPath()+File.separator+listFileName;
        TownyWorld world=pluginInstance.getUniverse().getDataSource().getWorld("world");

        //assessment starts from top left on the image
        //set this to the coords that pixel should be in the map so the right coordinates are grabbed
        Point startingPoint = new Point(startX,startZ);

        //map names to hex colors by loading from listfile
        HashMap<String,String> mapTerritories =Image2Towny.getIOHandler().loadTerritories(listFileName);
        //create another hashmap to record all townblocks assoc w/ea town created
        HashMap<Town,List<TownBlock>> newTowns = new HashMap<>();

        //slice image into manageable list of rects
        File imageFile=new File(imageFileName);
        List<Rectangle> slices = null;
        try {
            slices = Image2Towny.getIOHandler().subdivideImage(imageFile,startingPoint,regionSize);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //keep tabs on invalid names to avoid trying to look them up
        List<String> invalidNames = new ArrayList<>();

        //now for every slice, process
        for (int sliceIndex=0;sliceIndex<slices.size();sliceIndex++){
            Rectangle slice=slices.get(sliceIndex);
            BufferedImage sliceImage= Image2Towny.getIOHandler().readImageSlice(imageFile, slice);

            //actual processing, go chunk by chunk
            for (int y = 0; y < regionSize;y+=16) {
                List<Town> sliceTowns= new ArrayList<>();
                for (int x = 0; x < regionSize; x+=16) {
                    //get majority color within this chunk
                    HashMap<Integer,Integer> coloration=new HashMap<>();
                    int majorityColor=0;
                    for (int chunky=0; chunky<16; chunky++) {
                        for (int chunkx = 0; chunkx < 16; chunkx++) {
                            int thisColor;
                            try {
                                thisColor = Integer.parseInt(Integer.toHexString(sliceImage.getRGB(x + chunkx, y + chunky)).substring(2));
                            } catch (Exception e) {
                                continue;
                            }
                            //create entry in colors
                            coloration.put(thisColor, coloration.getOrDefault(thisColor, 0) + 1);
                            if (coloration.getOrDefault(majorityColor, 0) < coloration.get(thisColor)) {
                                majorityColor = thisColor;
                            }
                        }
                    }
                    //find thiscolor in the territories map to get a town name
                    String townName = null;
                    try {
                        if (mapTerritories.containsKey(String.valueOf(majorityColor))) {
                            townName = mapTerritories.get(String.valueOf(majorityColor));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //skip if not a known color, or if a known invalid name (don't bother processing)
                    if (townName == null || invalidNames.contains(townName)) {
                        continue;
                    }
                    //otherwise try to get town associated with name
                    Town currentTown = pluginInstance.getUniverse().getTown(townName);
                    //no town, make one
                    if (currentTown == null) {
                        //create town if doesn't exist
                        try {
                            pluginInstance.getUniverse().newTown(townName);
                        } catch (AlreadyRegisteredException | InvalidNameException e) {
                            e.printStackTrace();
                            invalidNames.add(townName);
                            continue;
                        }

                        currentTown = pluginInstance.getUniverse().getTown(townName);
                        //if had to create town object, create entry in hashmap
                        newTowns.put(currentTown, new ArrayList<>());
                        pluginInstance.getUniverse().getDataSource().saveTown(currentTown);
                        //add to towns that were worked this slice
                        if(!sliceTowns.contains(currentTown)){
                            sliceTowns.add(currentTown);
                        }
                    }
                    //now take this pixel, create new worldcoord, and add to list
                    List<TownBlock> targetList = newTowns.get(currentTown);
                    //need to add slice's coords on map to account for previous slices processed
                    //also need to /16 here to match Towny's TownBlock coordinate system, which is /not/ abs world coords
                    TownBlock newBlock=new TownBlock(
                            (((int) slice.getX()) + x + (int) startingPoint.getX())/16,
                            (((int) slice.getY()) + y + (int) startingPoint.getY())/16,
                            world);
                    if(!targetList.contains(newBlock)){
                        targetList.add(newBlock);
                    }
                }
            }
        }
        //tasks to perform on all
        try{
            writeTownBlocksQuery(newTowns,world.getName());
            prepareTowns(newTowns);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    For every town created by the class, set mayor and other properties to comply with Towny logic
     */
    public void prepareTowns(HashMap<Town,List<TownBlock>> newTowns) throws AlreadyRegisteredException, NotRegisteredException {
        for (Town validTown : newTowns.keySet()) {
            //set mayor
            setMayor(validTown);
            //set miscellaneous properties
            validTown.setHasUpkeep(false);
            validTown.setRuined(false);
            validTown.setPublic(true);
            validTown.setOpen(true);
            validTown.setRegistered(System.currentTimeMillis());
            //find and locate homeblock from townblocks registered with towns (halfway)
            List<TownBlock> coordList = newTowns.get(validTown);
            TownBlock homeblock = coordList.get(Math.floorDiv(coordList.size(), 2));
            //need to convert to location here to get highest block at a point using Bukkit api
            Location spawnLoc = (Objects.requireNonNull(Bukkit.getWorld("world")).
                    getHighestBlockAt(homeblock.getX() * 16, homeblock.getZ() * 16).getLocation()
            );
            //one block higher just to be safe
            spawnLoc.setY(spawnLoc.getY() + 1);
            homeblock.setTown(validTown);
            homeblock.save();
            //set spawn and homeblock
            try {
                validTown.setHomeBlock(homeblock);
                validTown.forceSetSpawn(spawnLoc);
            } catch (TownyException e) {
                e.printStackTrace();
            }
            //debug message
            pluginInstance.getLogger().info(String.format("[Serfdom] saving new town %s", validTown.getName()));
            validTown.save();
        }
    }

    /*
    Sets the NPC mayor of a town. Uses web endpoint as applicable TODO: add load from file as an option?
     */
    public void setMayor(Town targetTown) throws AlreadyRegisteredException, NotRegisteredException {
        //mayor stuff
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        TownyUniverse universe = pluginInstance.getUniverse();
        String newname = null;
        while (newname == null || universe.getResident(newname) != null) {

            Character randomLetter = letters.charAt((int) (Math.random() * letters.length()));
            Document NPCNames;
            try {
                NPCNames = Jsoup.connect(
                        String.format("https://www.mithrilandmages.com/utilities/MedievalBrowse.php?letter=%c&fms=M",
                                randomLetter)
                ).get();
                Element resultBox = NPCNames.select("body div#wrap div#content div#medNameColumns").get(0);
                String[] newnames = resultBox.text().split("\n<br>")[0].split(" ");
                newname = newnames[(int) (Math.random() * newnames.length)];
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            }

        }
        final UUID npcUUID = UUID.randomUUID();
        universe.getDataSource().newResident(newname, npcUUID);
        Resident newMayor = universe.getResident(npcUUID);
        assert newMayor != null;

        newMayor.setRegistered(System.currentTimeMillis());
        newMayor.setLastOnline(0);
        newMayor.setNPC(true);
        targetTown.setMayor(newMayor);
        newMayor.setTown(targetTown);
        newMayor.save();
    }

    /*
    Write townblocks calculated to townblocks.sql in the plugin folder (consolidated SQL query)
     */
    public void writeTownBlocksQuery(HashMap<Town,List<TownBlock>> newTowns,String worldName) throws IOException {
        //write sql for townblocks
        String townBlocksQuery="INSERT INTO towny_townblocks (world,x,z,price,town,type,outpost,permissions,locked,changed) VALUES\n";
        String component="(\"%s\",%d,%d,0,\"%s\",0,0,\"\",0,0)";
        FileWriter writer = new FileWriter(pluginInstance.getDataFolder()+File.separator+"townblocks.sql", StandardCharsets.UTF_8);
        for(Town town:newTowns.keySet()){
            writer.write(String.format("\n\n/* %s */\n",town.getName()));
            writer.write(townBlocksQuery+'\n');
            List<TownBlock> blocklist = newTowns.get(town);
            for(int i=0;i< blocklist.size();i++){
                //write every block as part of this insert query
                TownBlock block=blocklist.get(i);
                writer.write(String.format(component,worldName,block.getX(),block.getZ(),town.getName()));
                //add comma, don't if last VALUE in the query
                if(i!=blocklist.size()-1){
                    writer.write(",\n");
                }
                else
                {
                    writer.write(";\n");
                }
            }
        }
        writer.close();
        pluginInstance.getLogger().info("[Image2Towny] Write to ./image2towny/townblocks.sql complete. Remember to run it against your towny database!");
    }
}
