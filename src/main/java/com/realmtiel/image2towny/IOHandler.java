package com.realmtiel.image2towny;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

public class IOHandler {
    public IOHandler(){
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
