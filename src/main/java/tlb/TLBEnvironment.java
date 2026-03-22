package tlb;


import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.field.grid.ObjectGrid2D;
import sim.field.grid.SparseGrid2D;
import sim.util.Bag;
import sim.util.Int2D;
import tlb.Utils.*;

import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static tlb.Utils.DayLengthLookup.loadDayLengthCsv;

public class TLBEnvironment extends SimState {
    //input and output files path
    public String debugFile = "RESET_TLB_debug.txt";
    public String logFile = "RESET_TLB_log.csv";
    public String agentSummaryFile = "RESET_TLB_agentSummary.csv";
    public String popSummaryFile = "RESET_TLB_popSummary.csv";
    public String impactFile = "RESET_TLB_impact.csv";
    public OutputWriter debugWriter;
    public OutputWriter logWriter;
    public OutputWriter agentSummaryWriter;
    public OutputWriter popSummaryWriter;
    public OutputWriter impactWriter;
    //Environmental parameters are global
    public SparseGrid2D agentGrid; //UI grid
    public SparseGrid2D backgroundGrid; //UI grid
    public ObjectGrid2D territoryGrid; // spatial lookup: (vegGridX, vegGridY) → TLBTerritory
    public Map<Integer, TLBTerritory> territoriesByID = new HashMap<>(); // terrID → TLBTerritory
    int displayWidth = 100;
    int displayHeight = 100;
//    double initialLon = 377950.966434972;
//    double initialLat = -492019.574922292;
    //Coordination Converter: The following four variables are used in manually converting coordinates into grid system. Just in case the crs automation conversion not works
    double xllcornerVeg = -194862 + 15;
    double yllcornerVeg = -695325 + 15;
    int vegCellSize = 30;
    int nRowsVeg = 23518;
    //vegetation (tamarisk) attributes info
    public Map<Integer, VegAttributes> tamariskInfo;
    //veg map instance variables
    public Raster vegetationRaster;
    public GridGeometry2D gridGeometry;
    //Agent state variables
    int tlbAgentID = 0;
    //mortality
    double mpAntPredation = 0.01; //the probability an agent is predated by ants
    double mpTlbMort = 0.1; //the basic mortality of TLB agents dying at each life stage 2026-03-06
    double mpProbDiapauseDeath = 0.1;
    //reproduction
    int mpTlbSpawn = 5; //the maximum number of eggs
    int mpPatchTamariskForLaying = 2; //the patch tamarisk quantity for reproduction, double-check the number
    //Dispersal
    double mpTlbDisperse = 100; //the mean distance TLB agents travel in the environment, agents follow a negative exponential distribution with this mean
    double mpTlbInvade = 0.5; //the probability that TLB agents will enter the RESET study area each year
    int mpTlbPatchInvasion = -1; //the patch ID where TLB agents enter the environment
    //colonization & defoliate
//    Map<String, TLBVegCell> vegMapCell = new HashMap<String, TLBVegCell>(); //create a vegMapCell to contain the agent, location as the key
    int mpTlbFeed = 5; //the number of agents defoliate per time step - a threshold that reduced the patchPercentTamarisk by 1
    //vegetation state variables
    int mpTlbCarb; //how many times a cell containing tamarisk can be defoliated before it stops growing
    TamariskLookup tamariskLookup;
    //Scheduling
    int currentYear = 0; //simulation period is 35 years from 0-34;
    int currentWeek = 0; //the week is from 0-51 in the current year
    int mpTamariskBud = 8; //the week when Tamarisk start to bud from dormancy 2026-03-04
    //Population summary data
    int populationSize = 0; //current population size
    int numBirth = 0; //number of birth within a year
    int numDeath = 0; //number of death within a year
    int numDeathInADULT = 0; //number of death in adult stage
    int numDeathINEGG = 0; //number of death in EGG stage
    int numDeathInLARVA = 0; //number of death in LARVA stage
    int numDeathInPUPA = 0; //number of death in PUPA stage

    //Constructor
    public TLBEnvironment(long seed) {
        super(seed);
    }

    @Override
    public void start() {
        super.start();
        try{
            //(0) create debugFile
            String[] debugHeader = {};
            String debugFile = OutputWriter.getFileName(this.debugFile, false);
            this.debugWriter = new OutputWriter(debugFile);
            this.debugWriter.createFile(debugHeader);
            //(1) create logFile
            String[] logHeader = {"currentStep", "currentWeek", "currentYear", "agentID", "Stage", "currentAge", "longitude", "latitude",
                    "vegGridX", "vegGridX", "displayX", "displayY", "patchID", "actionExecuted"}; //currently collect 14 data
            String logFile = OutputWriter.getFileName(this.logFile, false);
            this.logWriter = new OutputWriter(logFile);
            this.logWriter.createFile(logHeader);
            //(2) create agentSummaryFile
            String[] weeklyAgentOutputHeader = {"step", "agentID", "birthday", "date of death", "lon at birth",
                    "lat at birth", "patch at birth", "lon at death", "lat at death", "death stage", "death age"}; //currently collect 11 data
            String agentSummaryFile = OutputWriter.getFileName(this.agentSummaryFile, false);
            this.agentSummaryWriter = new OutputWriter(agentSummaryFile);
            this.agentSummaryWriter.createFile(weeklyAgentOutputHeader);
            //(3) create popSummaryFile
            String[] popSummaryHeader = {"year", "Pop size", "Num of birth", "Num of death", "Num deaths in ADULT",
                    "Num death in EGG", "Num deaths in LARVA", "Num deaths in PUPA"}; //currently collect 8 data
            String popSummaryFile = OutputWriter.getFileName(this.popSummaryFile, false);
            this.popSummaryWriter = new OutputWriter(popSummaryFile);
            this.popSummaryWriter.createFile(popSummaryHeader);
            //(4) create impactFile
            String[] impacDataHeader = {"year", "week", "type", "x", "y", "patchID, numOfDefoliations"}; //currently collect 4 data
            String impactFile = OutputWriter.getFileName(this.impactFile, false);
            this.impactWriter = new OutputWriter(impactFile);
            this.impactWriter.createFile(impacDataHeader);
            //(5) import tiff vegetation raster map - main raster map and then converting it to SparseGrid2D (vegGrids)
            importTiffVegRasterMap();
            //(6) import day length table
            String dayLengthFilePath = OutputWriter.getFileName("/RESET_TLB_inputData/Weekly_dayLength.csv", true);
            System.out.println(dayLengthFilePath);
            loadDayLengthCsv(dayLengthFilePath);
            //(7) load the tamarisk data
            String patchTamariskFilePath = OutputWriter.getFileName("/RESET_TLB_inputData/TLB territory attributes.csv", true);
            this.tamariskLookup = new TamariskLookup();
            tamariskInfo = tamariskLookup.loadPatchTamariskCSV(patchTamariskFilePath);
            //(8) create a background grid
            backgroundGrid = new SparseGrid2D(displayWidth, displayHeight);
            Object backgoundAnchor = new Object();
            backgroundGrid.setObjectLocation(backgoundAnchor, new Int2D(0,0)); //add dummy object to anchor the background image
            //debug
            Bag objs = backgroundGrid.getObjectsAtLocation(0,0);
            if(objs != null && !objs.isEmpty()) {
                System.out.println("Object at (0,0): " + objs.get(0));
            } else {
                System.out.println("No object at (0,0)");
            }
            //(9) Initiate agentGrid
            this.agentGrid = new SparseGrid2D(displayWidth, displayHeight);
            //(9b) Build territory grid from territory raster (pixel value = terrID)
            // TODO: replace vegetationRaster with territory raster once territory TIF is available
            this.territoryGrid = new ObjectGrid2D(vegetationRaster.getWidth(), vegetationRaster.getHeight());
            for (int x = 0; x < vegetationRaster.getWidth(); x++) {
                for (int y = 0; y < vegetationRaster.getHeight(); y++) {
                    int terrID = getTerrIDByLoc(this, x, y);
                    if (terrID <= 0) continue;
                    if (!territoriesByID.containsKey(terrID)) {
                        // TODO: look up territory attributes by terrID from CSV once territory raster is confirmed
                        TLBTerritory terr = new TLBTerritory(this, terrID, 0, 0, 0.0, 0.0, false);
                        terr.event = schedule.scheduleRepeating(terr);
                        territoriesByID.put(terrID, terr);
                    }
                    TLBTerritory terr = territoriesByID.get(terrID);
                    terr.memberCells.add(new Int2D(x, y)); // record pixel coordinates belonging to this territory
                    territoryGrid.set(x, y, terr);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //(10) initiate timer just to update the time
        TLBTimer systemTimer = new TLBTimer();
        schedule.scheduleRepeating(Schedule.EPOCH, 0, systemTimer);
        //(11) make agents
        try {
            makeAgentsInSpace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //(12) initiate observer
        TLBObserver observer = new TLBObserver();
        schedule.scheduleRepeating(Schedule.EPOCH, Integer.MAX_VALUE, observer);
        System.out.println("-----------------Finish Starting TLB environment-------------------------");
    }

    public void importTiffVegRasterMap() throws IOException {
        String tifVegRasterMapFile = OutputWriter.getFileName("/RESET_TLB_inputData/pseudoterr_raster.tif", true); //2026-03-21
        File tiffVegRaster_tamarisk = new File(tifVegRasterMapFile); //create a file object from the raster file path

        GeoTiffReader reader = new GeoTiffReader(tiffVegRaster_tamarisk); //read the contents of Geotiff file
        GridCoverage2D cov = reader.read(null); //read the raster data as a GridCoverage2D object (which contains both image and spatial metadata)

        this.vegetationRaster = cov.getRenderedImage().getData(); //Extract only the raster image data (as a Raster object) and store it in the class field `vegetationRaster`
        // Store the grid-to-world coordinate transformation info from the coverage
        // This is useful for converting between pixel indices and geographic coordinates
        this.gridGeometry = cov.getGridGeometry(); //needed for the world --grid conversion

        System.out.println("Raster bounds: " + vegetationRaster.getBounds()); //get x, y bounds
        //get lon, lat bounds (longitude supplied first)
        System.out.println("Geo bounds:" + cov.getEnvelope());
    }

    // update week and year
    public void updateWeek(){
        this.currentWeek = (int)(schedule.getSteps() % 52);
    }

    public void updateYear(){
        this.currentYear = (int)(schedule.getSteps() / 52);
    }

    /*
    ****************************************************************************************************
    *                                        MAKE AGENTS IN THE SPACE
    * **************************************************************************************************
     */
    public void makeAgentsInSpace() throws IOException {
        String startLocations = OutputWriter.getFileName("/RESET_TLB_inputData/TLB_invasion_loc.csv", true);
        InputDataParser parser = new InputDataParser(startLocations); //initiate a new inputDataParser class
        Map<Integer, InfoIdentifier> initialInfo = parser.getDataInformation(); //get all groupInfo
        int nInitialLocations = initialInfo.size(); // # of initial location
        System.out.println("nInitialLocations: " + nInitialLocations);
        for (int i=1; i<= nInitialLocations; i++) {
            InfoIdentifier info = initialInfo.get(i);
            double initialLon = info.getInputX();
            double initialLat = info.getInputY();
            int patchID = info.getPatchID();
            int terrID = info.getTerrID();
            int nAgentsAtInitLocation = 10;
            for(int j=0; j<nAgentsAtInitLocation; j++) {
                tlbAgentID ++;
                int tlbAge = random.nextInt(3) + 5; //randomly choose a range between 5-7 for adult
                TLBAgent a = new TLBAgent(this, tlbAgentID, Stage.TLBADULT, initialLon, initialLat, patchID, terrID, tlbAge, 0);
                System.out.println("Agent #" + tlbAgentID + " is an " + a.tlbStage + " in initiation");
                a.event = schedule.scheduleRepeating(a);
                agentGrid.setObjectLocation(a, a.displayLocation);
            }
        }
    }

    /*
    ***************************************************************************************************
    *                          Get Values from VegRasterMap or Table
    * *************************************************************************************************
     */
    public int getPatchIDByLoc(TLBEnvironment state, int vegGridX, int vegGridY) {
        int[] hostRasterData = new int[1];
        int patchID = 0;
        //check Coordinates before access the raster info
        if (vegGridX < 0 || vegGridX >= vegetationRaster.getWidth() || vegGridY < 0 || vegGridY >= vegetationRaster.getHeight()) {
            System.err.println("Coordinate out of bounds: x=" + vegGridX + ", y=" + vegGridY);
            return -1; // or some default/fallback value
        }
        state.vegetationRaster.getPixel(vegGridX, vegGridY, hostRasterData);
        if (hostRasterData[0] >= 100000) { //not a patch (the patch # is between 1-22384)
            return 0;
        } else { //it's a patch, return the patchID
            patchID = hostRasterData[0];
//            System.out.println("patchID: " + patchID);
            return patchID;
        }
    }

    public int getTerrIDByLoc(TLBEnvironment state, int vegGridX, int vegGridY) {
        int[] hostRasterData = new int[1];
        int terrID = 0;
        //check Coordinates before access the raster info
        if (vegGridX < 0 || vegGridX >= vegetationRaster.getWidth() || vegGridY < 0 || vegGridY >= vegetationRaster.getHeight()) {
            System.err.println("Coordinate out of bounds: x=" + vegGridX + ", y=" + vegGridY);
            return -1; // or some default/fallback value
        }
        state.vegetationRaster.getPixel(vegGridX, vegGridY, hostRasterData);
        if (hostRasterData[0] >= 100000) { //not a patch (the patch # is between 1-22384)
            return 0;
        } else { //it's a patch, return the patchID
            terrID = hostRasterData[0];
            System.out.println("terrID: " + terrID);
            return terrID;
        }
    }

    /*
    ************************************************************************************************
    *                                      Getters and Setters
    * **********************************************************************************************
     */

    public String getLogFile() {
        return logFile;
    }

    public void setLogFile(String logFile) {
        this.logFile = logFile;
    }

    public String getAgentSummaryFile() {
        return agentSummaryFile;
    }

    public void setAgentSummaryFile(String agentSummaryFile) {
        this.agentSummaryFile = agentSummaryFile;
    }

    public String getPopSummaryFile() {
        return popSummaryFile;
    }

    public void setPopSummaryFile(String popSummaryFile) {
        this.popSummaryFile = popSummaryFile;
    }

    public String getImpactFile() {
        return impactFile;
    }

    public void setImpactFile(String impactFile) {
        this.impactFile = impactFile;
    }

//    public int getInitialN() {
//        return initialN;
//    }
//
//    public void setInitialN(int initialN) {
//        this.initialN = initialN;
//    }

//    public double getInitialLon() {
//        return initialLon;
//    }
//
//    public void setInitialLon(double initialLon) {
//        this.initialLon = initialLon;
//    }
//
//    public double getInitialLat() {
//        return initialLat;
//    }
//
//    public void setInitialLat(double initialLat) {
//        this.initialLat = initialLat;
//    }

    public double getMpAntPredation() {
        return mpAntPredation;
    }

    public void setMpAntPredation(double mpAntPredation) {
        this.mpAntPredation = mpAntPredation;
    }

    public double getMpProbDiapauseDeath() {
        return mpProbDiapauseDeath;
    }

    public void setMpProbDiapauseDeath(double mpProbDiapauseDeath) {
        this.mpProbDiapauseDeath = mpProbDiapauseDeath;
    }

    public int getMpTlbSpawn() {
        return mpTlbSpawn;
    }

    public void setMpTlbSpawn(int mpTlbSpawn) {
        this.mpTlbSpawn = mpTlbSpawn;
    }

    public int getMpPatchTamariskForLaying() {
        return mpPatchTamariskForLaying;
    }

    public void setMpPatchTamariskForLaying(int mpPatchTamariskForLaying) {
        this.mpPatchTamariskForLaying = mpPatchTamariskForLaying;
    }

    public double getMpTlbDisperse() {
        return mpTlbDisperse;
    }

    public void setMpTlbDisperse(double mpTlbDisperse) {
        this.mpTlbDisperse = mpTlbDisperse;
    }

    public int getMpTlbFeed() {
        return mpTlbFeed;
    }

    public void setMpTlbFeed(int mpTlbFeed) {
        this.mpTlbFeed = mpTlbFeed;
    }

    public int getMpTlbCarb() {
        return mpTlbCarb;
    }

    public void setMpTlbCarb(int mpTlbCarb) {
        this.mpTlbCarb = mpTlbCarb;
    }

}
