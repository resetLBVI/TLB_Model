package tlb.Utils;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class DayLengthLookup {

    // Map from latitude to list of 52 values (week-based)
    private static Map<Double, List<Double>> dayLengthTable = new TreeMap<>();

    public static void loadDayLengthCsv(String filePath) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String line;
        // Read header
        String[] headers = br.readLine().split(",");
        if (headers.length != 53) {
            throw new IllegalArgumentException("Expected 52 weeks + 1 latitude column");
        }

        while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");
            double latitude = Double.parseDouble(parts[0]);
            List<Double> weekValues = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                weekValues.add(Double.parseDouble(parts[i]));
            }
            dayLengthTable.put(latitude, weekValues);
        }
        br.close();
    }

    public static double getDayLength(double latitude, int weekNumber) {
        // Find the nearest latitude
        double nearestLat = dayLengthTable.keySet().stream()
                .min(Comparator.comparingDouble(l -> Math.abs(l - latitude)))
                .orElseThrow(() -> new IllegalStateException("No latitudes available"));
        List<Double> values = dayLengthTable.get(nearestLat);
        return values.get(weekNumber); // week starts with 0
    }


//        public static void main(String[] args) throws IOException {
//            String filePath = "path/to/daylength.csv";
//            loadCsv(filePath);
//
//            double latitude = 34.05; // Example: Los Angeles
//            int currentWeek = state.week;
//            double dayLength = getDayLength(latitude, currentWeek);
//
//            System.out.println("Latitude: " + latitude + ", Week: " + currentWeek + ", Day Length: " + dayLength);
//        }

}
