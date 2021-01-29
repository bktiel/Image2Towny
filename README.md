# Image2Towny
Converts an image to Towny towns on a Minecraft map. 

First, create an uncompressed image file (bmp or tga) of the same size as the target map. Color in desired regions with desired color, then take the hex of that color and map it to a town name in a text file.
Examples for this are in the /examples folder

After installing the plugin, run /imgtowny with arguments to start processing. This may take a while.

Lastly, once everything is done, you'll need to run townblocks.sql in the output folder against the Towny install's MySQL database. 
Everything else should populate automatically (there are two many transactions in townblocks for larger maps to do this through Towny's SQL prepared statements).

Restarting the server or doing /ta reload all will load the generated Towns. 
