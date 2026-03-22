package tlb.Utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TamariskLookup {
    private Map<Integer, VegAttributes> tamariskInfo = new HashMap<>();


    public Map<Integer, VegAttributes> loadPatchTamariskCSV(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) { // Skip header
                    firstLine = false;
                    continue;
                }
                String[] parts = line.split(",");
                // Skip rows with NA values 2026-03-21
                boolean hasNA = false;
                for (String part : parts) { if (part.trim().equalsIgnoreCase("NA")) { hasNA = true; break; } }
                if (hasNA) continue;
                int patchID = Integer.parseInt(parts[0].trim());
                int terrID = Integer.parseInt(parts[1].trim());
                double POINT_X = Double.parseDouble(parts[2].trim());
                double POINT_Y = Double.parseDouble(parts[3].trim());
                double GISAcres = Double.parseDouble(parts[4].trim());
                double pTamarisk = Double.parseDouble(parts[5].trim());
                double terrTotalTamariskFeed = Double.parseDouble(parts[6].trim());
                boolean permanentlyDefoliated = Boolean.parseBoolean(parts[7].trim());

                VegAttributes groupInfo = new VegAttributes(
                        patchID, terrID, POINT_X, POINT_Y, GISAcres, pTamarisk, terrTotalTamariskFeed, permanentlyDefoliated
                );

                tamariskInfo.put(patchID, groupInfo);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this.tamariskInfo;
    }

    // call this to update the Tamarisk energy scores
    public void updateTamariskEnergyScores(int patchID, int numTlb, double totalFeed) {
        VegAttributes attr = tamariskInfo.get(patchID);
        if (attr != null) {
            attr.terrNumTlb = numTlb;
            attr.terrTotalTamariskFeed = totalFeed;
        }
    }

}
