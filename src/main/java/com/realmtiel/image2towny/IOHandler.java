package com.realmtiel.image2towny;

import com.palmergames.util.FileMgmt;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class IOHandler {
    public IOHandler(){
        //create directories
        FileMgmt.checkOrCreateFolder(Image2Towny.getPlugin().getDataFolder().toString());

    }

    /*
    Given a filepath with region:ID keypairs, return a hashmap with all pairs
    File should be UTF-8 Encoded.
     */
    public HashMap<String,String> loadTerritories(String filepath) {
        File fileRef= new File(filepath);
        if (fileRef.exists() && fileRef.isFile()) {
            HashMap<String,String> territories = new HashMap<>();
            try {
                Scanner reader = new Scanner(fileRef);
                while(reader.hasNextLine()){
                    String thisLine=reader.nextLine();
                    String[] items =thisLine.split(":");
                    //skip names too long
                    if(items[1].length()>32) {continue;}
                    territories.put(items[0],items[1].replaceAll("[ ']",""));
                }
                reader.close();
                return territories;

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return null;
    }

    /*
    Process a given image into rectangles by given edge size, return list of them
     */
    List<Rectangle> subdivideImage(File imageFile, Point startingPoint, int regionSize) throws IOException {
        //divide map into manageable bites
        ImageInputStream img = null;
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
        for (int y = 0; y < dimensions[1];y+=regionSize) {
            for (int x = 0; x < dimensions[0]; x+=regionSize) {
                //tentatively go for slice of size region_size
                int[] adjustment= new int[]{regionSize, regionSize};
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
                                Math.min(regionSize,dimensions[0]-x),
                                Math.min(regionSize,dimensions[1]-y))
                );

            }
        }
        //cleanup
        reader.dispose();
        img.close();
        //return slices list
        return slices;
    }

    /*
    * Return part of an UNCOMPRESSED image given by passed File obj img and region defined by rectangle obj.
    * */
    BufferedImage readImageSlice(File img, Rectangle rect){
        ImageInputStream imageStream = null;
        try {
            imageStream = ImageIO.createImageInputStream(img);
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert imageStream != null;
        ImageReader reader = ImageIO.getImageReaders(imageStream).next();
        ImageReadParam param = reader.getDefaultReadParam();
        //set param to only read specified region
        param.setSourceRegion(rect);
        reader.setInput(imageStream, true, true);
        BufferedImage slice = null;
        try {
            slice = reader.read(0,param);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return slice;
    }
}
