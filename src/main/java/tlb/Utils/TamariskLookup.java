package tlb.Utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TamariskLookup {
    private Map<Integer, Double> tamariskMap = new HashMap<>();


    public void loadPatchTamariskCSV(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) { // Skip header
                    firstLine = false;
                    continue;
                }
                String[] parts = line.split(",");
                int patchID = Integer.parseInt(parts[0].trim());
                double pTamarisk = Double.parseDouble(parts[1].trim());

                tamariskMap.put(patchID, pTamarisk);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Double getPTamarisk(int patchID) {
        return tamariskMap.getOrDefault(patchID, 0.0); // default if not found
    }
}
