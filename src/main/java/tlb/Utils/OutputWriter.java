package tlb.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;

public class OutputWriter {
    public String outputFilePath;

    /*
    Constructor
     */
    public OutputWriter(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    public void createFile(String[] header) {
        //create file
        try {
           File myObj = new File(this.outputFilePath);
           if(myObj.createNewFile()) {
               if(header.length > 0) {
                   this.addToFile(String.join(",", header));
                   System.out.println("File create: " + myObj.getAbsolutePath());
               } else {
                   System.out.println("File already exists.");
               }
           }
        } catch (IOException e) {
            System.out.println("An error occurred while creating the output file.");
            e.printStackTrace();
        }
    }

    public void addToFile(String text) {
        //try block to check for exception
        try {
            //open given file in append mode by creating an object of BufferedWriter class
            BufferedWriter out = new BufferedWriter(new FileWriter(this.outputFilePath, true));
            //writing on output stream
            out.write (text + "\n");
            //close the connection
            out.close();
        }
        //catch block to handle the exception
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("exception occurred" + e);
        }
    }

    public static String getFileName(String fileName) throws IOException {
        try {
            File codeSource = new File(
                    OutputWriter.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );
            File currentDir = codeSource.isFile()
                    ? codeSource.getParentFile() //Running from JAR
                    :codeSource;                 //Running in IDE
            //Traverse up until we find the folder that contains "src" (RootFolder), then go up one level
            while (currentDir != null && !containSrc(currentDir)) {
                currentDir = currentDir.getParentFile();
            }
            if (currentDir == null) {
                throw new IOException("Couldn't find project root or its parent directory.");
            }
            File parentOfRoot = currentDir.getParentFile();
            return new File(parentOfRoot, fileName).getAbsolutePath();

        } catch (URISyntaxException e) {
            throw new IOException("Failed to resolve path", e);
        }
    }

    //Helper: detects if a folder is the project root by looking for "src"
    private static boolean containSrc(File dir) {
        File[] childern = dir.listFiles();
        if(childern == null) {return false;}
        for(File child : childern) {
            if(child.getName().equals("src") && child.isDirectory()) {
                return true;
            }
        }
        return false;
    }
}
