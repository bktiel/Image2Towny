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

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class TerritoryAssign {
    private final int REGION_SIZE=512;
    private final Image2Towny pluginInstance;
    public TerritoryAssign(String imagePath, String listFileName) throws InvalidNameException, AlreadyRegisteredException, NotRegisteredException {
        this.pluginInstance=Image2Towny.getPlugin();
        imagePath=pluginInstance.getDataFolder().getPath()+File.separator+imagePath;
        listFileName=pluginInstance.getDataFolder().getPath()+File.separator+listFileName;

//        BufferedImage territories =getImage(imagePath);

//        BufferedImage territories = null;
//        try {
//            territories = BigBufferedImage.create(new File(imagePath), BufferedImage.TYPE_INT_RGB);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        //assessment starts from top left on the image
        //set this to the coords that pixel should be in the map so the right coordinates are grabbed
        Point startingPoint = new Point(512,0);

        //create storage object for every town
        HashMap<String,String> mapTerritories =Image2Towny.getIOHandler().loadTerritories(listFileName);
//
//        for (String terrName : mapTerritories.values()) {
//            //create town objects for each territory
//            TownCommand.newTown(null,terrName,);
//        }
        //create another hashmap to record
        HashMap<Town,List<TownBlock>> newTowns = new HashMap<Town,List<TownBlock>>();

        //divide map into manageable bites
        ImageInputStream img = null;
        File imageFile=new File(imagePath);
        try {
            img = ImageIO.createImageInputStream(imageFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //get reader for this kind of stream
        ImageReader reader = ImageIO.getImageReaders(img).next();
        reader.setInput(img);
        //get dimensions by reading file header
        int[] dimensions= new int[0];
        try {
            dimensions = new int[]{reader.getWidth(0), reader.getHeight(0)};
        } catch (IOException e) {
            e.printStackTrace();
        }
        //separate image into slices (rectangles)
        List<Rectangle> slices = new ArrayList<>();
        for (int y = 0; y < dimensions[1];y+=REGION_SIZE) {
            for (int x = 0; x < dimensions[0]; x+=REGION_SIZE) {
                //tentatively go for slice of size region_size
                int[] adjustment= new int[]{REGION_SIZE, REGION_SIZE};
                //check if adding region size throws off x16 coordinates
                //if so, subtract modulo iot get what number will restore
                if ((startingPoint.x+x+adjustment[0])%16>0) {
                    adjustment[0]=adjustment[0]-((x+startingPoint.x)%16);
                }
                if ((startingPoint.y+y+adjustment[1])%16>0) {
                    adjustment[1]=adjustment[1]-((y+startingPoint.y)%16);
                }
                slices.add(
                        new Rectangle(x,y,
                                Math.min(REGION_SIZE,dimensions[0]-x),
                                Math.min(REGION_SIZE,dimensions[1]-y))
                );

            }
        }

        TownyWorld world=pluginInstance.getUniverse().getDataSource().getWorld("world");

        //keep tabs on invalid names
        List<String> invalidNames = new ArrayList<>();
        //now for every slice, process

        for (int sliceIndex=0;sliceIndex<slices.size();sliceIndex++){
            Rectangle slice=slices.get(sliceIndex);
            BufferedImage sliceImage= Image2Towny.getIOHandler().readImageSlice(imageFile, slice);

            //actual processing, go chunk by chunk
            for (int y = 0; y < REGION_SIZE;y+=16) {
                List<Town> sliceTowns= new ArrayList<>();
                for (int x = 0; x < REGION_SIZE; x+=16) {
                    //get majority color within this chunk
                    HashMap<Integer,Integer> coloration=new HashMap<>();
                    int majorityColor=0;
                    for (int chunky=0; chunky<16; chunky++) {
                        for (int chunkx = 0; chunkx < 16; chunkx++) {
                            int thisColor = 0;
                            try {
                                thisColor = Integer.parseInt(Integer.toHexString(sliceImage.getRGB(x + chunkx, y + chunky)).substring(2)) - 251337;
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
                    TownBlock newBlock=new TownBlock(
                            (((int) slice.getX()) + x + (int) startingPoint.getX())/16,
                            (((int) slice.getY()) + y + (int) startingPoint.getY())/16,
                            world);
                    if(!targetList.contains(newBlock)){
                        targetList.add(newBlock);
                    }
//                                new WorldCoord("world",
//                                        ((int) slice.getX()) + x + (int) startingPoint.getX(),
//                                        ((int) slice.getY()) + y + (int) startingPoint.getY()
//                                )
//                        );
                    //create claim objects for all towns in this slice
//                    for(Town sliceTown: sliceTowns){
//                        new TownClaim(
//                                Towny.getPlugin(),
//                                null,
//                                sliceTown,
//                                newTowns.get(sliceTown),
//                                false,
//                                true,
//                                true
//                        ).start();
//                    }
                }
            }
        }
        //cleanup
        reader.dispose();
        try {
            img.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(Town validTown:newTowns.keySet()){
            //mayor stuff
            String letters="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            TownyUniverse universe= pluginInstance.getUniverse();
            String newname=null;
            while(newname==null || universe.getResident(newname)!=null)
            {

                Character randomLetter=letters.charAt((int) (Math.random()*letters.length()));
                Document NPCNames= null;
                try {
                    NPCNames = Jsoup.connect(
                            String.format("https://www.mithrilandmages.com/utilities/MedievalBrowse.php?letter=%c&fms=M",
                                    randomLetter)
                    ).get();
                    Element resultBox=NPCNames.select("body div#wrap div#content div#medNameColumns").get(0);
                    String newnames[] = resultBox.text().split("\n<br>")[0].split(" ");
                    newname=newnames[(int) (Math.random()*newnames.length)];
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
            validTown.setMayor(newMayor);
            newMayor.setTown(validTown);
            newMayor.save();
            validTown.setHasUpkeep(false);

//            TownyWorld world=pluginInstance.getUniverse().getDataSource().getWorld("world");
            validTown.setRegistered(System.currentTimeMillis());
            List<TownBlock> coordList=newTowns.get(validTown);
            TownBlock homeblock= coordList.get(Math.floorDiv(coordList.size(), 2));
            //coordList.remove(homeblock);
            Location spawnLoc;
            spawnLoc=(Objects.requireNonNull(Bukkit.getWorld("world")).
                    getHighestBlockAt(homeblock.getX()*16,homeblock.getZ()*16).getLocation());
            spawnLoc.setY(spawnLoc.getY()+1);
//            //set homeblock
//            TownBlock homeblock=new TownBlock(spawnLoc.getBlockX(),
//                    spawnLoc.getBlockZ(),
//                    world
//            );
            homeblock.setTown(validTown);
            homeblock.save();

            //grab the townblock closest to desired spawn locations
//            TownBlock homeblock=pluginInstance.getUniverse().getTownBlock(spawnCoord);
            //set spawn
            try {
                validTown.setHomeBlock(homeblock);
                validTown.forceSetSpawn(spawnLoc);
            } catch (TownyException e) {
                e.printStackTrace();
            }
            validTown.setHasUpkeep(false);
            //remove homeblock from being claimed again
//            coordList.remove(spawnCoord);
            //debug message
            pluginInstance.getLogger().info(String.format("[Serfdom] Loading and saving %s", validTown.getName()));
            //for every block, claim

//            for (WorldCoord claim : coordList) {
//                //skip spawn
//                if(claim==spawnCoord) {continue;}
//
//                TownBlock newBlock = new TownBlock(claim.getX(),claim.getZ(),world);
//                newBlock.setTown(validTown);
//                newBlock.setPlotPrice(500);
//                newBlock.save();
//            }
            validTown.setRuined(false);
            validTown.setPublic(true);
            validTown.setOpen(true);
            validTown.save();

//            new TownClaim(Towny.getPlugin(),
//                    null,
//                    validTown,
//                    newTowns.get(validTown),
//                false,
//                true,
//                true).start();
        }
    }

    //claim for one region right now
//        Town targetTown=pluginInstance.getUniverse().getTown("Prague");
//        new TownClaim(Towny.getPlugin(),
//                null,
//                targetTown,
//                newTowns.get(targetTown),
//                false,
//                true,
//                true);
//    }

    public void writeTownBlocksQuery(HashMap<Town,List<TownBlock>> newTowns,String worldName){
        //write sql for townblocks
        String townBlocksQuery="INSERT INTO towny_townblocks (world,x,z,price,town,type,outpost,permissions,locked,changed) VALUES\n";
        String component="(\"%s\",%d,%d,0,\"%s\",0,0,\"\",0,0)";
        try {
            FileWriter writer = new FileWriter(pluginInstance.getDataFolder()+File.separator+"townblocks.sql", StandardCharsets.UTF_8);
            for(Town town:newTowns.keySet()){
                writer.write(String.format("\n\n/* %s */\n",town.getName()));
                writer.write(townBlocksQuery+'\n');
                List<TownBlock> blocklist = newTowns.get(town);
                for(int i=0;i< blocklist.size();i++){
                    //write every block as part of this insert query
                    TownBlock block=blocklist.get(i);
                    writer.write(String.format(component,worldName,block.getX(),block.getZ(),town.getName()));
                    //add comma
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
