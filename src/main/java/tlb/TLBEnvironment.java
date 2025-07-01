package tlb;


import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.field.grid.ObjectGrid2D;
import sim.field.grid.SparseGrid2D;
import sim.util.Bag;
import sim.util.Int2D;
import tlb.Utils.CoordinateConverter;
import tlb.Utils.OutputWriter;
import tlb.Utils.TamariskLookup;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static tlb.Utils.DayLengthLookup.loadDayLengthCsv;

public class TLBEnvironment extends SimState {
    //input and output files path
    public String logFile = "RESET_TLB_log.csv";
    public String agentSummaryFile = "RESET_TLB_agentSummary.csv";
    public String popSummaryFile = "RESET_TLB_popSummary.csv";
    public String impactFile = "RESET_TLB_impact.csv";
    public OutputWriter logWriter;
    public OutputWriter agentSummaryWriter;
    public OutputWriter popSummaryWriter;
    public OutputWriter impactWriter;
    //Environmental parameters are global
//    DoubleGrid2D vegetationGrid;
    public SparseGrid2D agentGrid;
    public SparseGrid2D backgroundGrid;
    public ObjectGrid2D vegCellGrid;
    int displayWidth = 200;
    int displayHeight = 200;
    int initialN = 100;
    double initialLon = 377950.966434972;
    double initialLat = -492019.574922292;
    //The following four variables are used in manually converting coordinates into grid system. Just in case the crs automation conversion not works
    double xllcornerVeg = -194862 + 15;
    double yllcornerVeg = -695325 + 15;
    int vegCellSize = 30;
    int nRowsVeg = 23518;
    //veg map instance variables
    public Raster vegetationRaster;
    public GridGeometry2D gridGeometry;
    CoordinateReferenceSystem crs;
//    GridGeometry2D gg;
//    Raster tiffRaster;
    //Agent state variables
    int tlbAgentID = 0;
    //mortality
    double mpAntPre = 0.5; //the probability an agent is predated by ants
    double mpProbDiapauseDeath = 0.1;
    //reproduction
    int mpTlbSpawn = 5; //the maximum number of eggs
    int mpPatchTamariskForLaying = 2; //the patch tamarisk quantity for reproduction, double-check the number
    //Diapause
    int mpCriticalDayLength = 12; //a value representing critical day length i.e.,tlbCDL
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
    int currentYear = 0; //simulation period is 35 years from 1-35;
    int currentWeek = 0; //the week is from 0-51 in the current year
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
            //(0) debug

            //(1) create logFile
            String[] logHeader = {"currentStep", "currentWeek", "currentYear", "agentID", "Stage", "currentAge", "longitude", "latitude",
                    "vegGridX", "vegGridX", "displayX", "displayY", "actionExecuted"}; //currently collect 0 data
            String logFile = OutputWriter.getFileName(this.logFile);
            this.logWriter = new OutputWriter(logFile);
            this.logWriter.createFile(logHeader);
            //(2) create agentSummaryFile
            String[] weeklyAgentOutputHeader = {"step", "agentID", "birthday", "date of birth", "lon at birth",
                    "lat at birth", "patch at birth", "lon at death", "lat at death", "death stage", "death age"}; //currently collect 11 data
            String agentSummaryFile = OutputWriter.getFileName(this.agentSummaryFile);
            this.agentSummaryWriter = new OutputWriter(agentSummaryFile);
            this.agentSummaryWriter.createFile(weeklyAgentOutputHeader);
            //(3) create popSummaryFile
            String[] popSummaryHeader = {"year", "Pop size", "Num of birth", "Num of death", "Num deaths in ADULT",
                    "Num death in EGG", "Num deaths in LARVA", "Num deaths in PUPA"}; //currently collect 8 data
            String popSummaryFile = OutputWriter.getFileName(this.popSummaryFile);
            this.popSummaryWriter = new OutputWriter(popSummaryFile);
            this.popSummaryWriter.createFile(popSummaryHeader);
            //(4) create impactFile
            String[] impacDataHeader = {"year", "x", "y", "patchID"}; //currently collect 4 data
            String impactFile = OutputWriter.getFileName(this.impactFile);
            this.impactWriter = new OutputWriter(impactFile);
            this.impactWriter.createFile(impacDataHeader);
            //(5) import tiff vegetation raster map - main raster map and then converting it to SparseGrid2D (vegGrids)
            importTiffVegRasterMap();
            //(6) import day length table
            String dayLengthFilePath = OutputWriter.getFileName("/RESET_TLB_inputData/Weekly_dayLength.csv");
            System.out.println(dayLengthFilePath);
            loadDayLengthCsv(dayLengthFilePath);
            //(7) load the tamarisk data
            String patchTamariskFilePath = OutputWriter.getFileName("/RESET_TLB_inputData/patch_tamarisk.csv");
            this.tamariskLookup = new TamariskLookup();
            tamariskLookup.loadPatchTamariskCSV(patchTamariskFilePath);
            //(8) create a background grid
            backgroundGrid = new SparseGrid2D(displayWidth, displayHeight);
            System.out.println("background width: " + vegetationRaster.getWidth() + " height: " + vegetationRaster.getHeight());
            Object backgoundAnchor = new Object();
            backgroundGrid.setObjectLocation(backgoundAnchor, new Int2D(0,0)); //add dummy object to anchor the background image
            //debug
            Bag objs = backgroundGrid.getObjectsAtLocation(0,0);
            if(objs != null && !objs.isEmpty()) {
                System.out.println("Object at (0,0): " + objs.get(0));
            } else {
                System.out.println("No object at (0,0)");
            }
            //(9) Initiate other field s (e.g., agentGrid)
            this.vegCellGrid = new ObjectGrid2D(vegetationRaster.getWidth(), vegetationRaster.getHeight());
            this.agentGrid = new SparseGrid2D(displayWidth, displayHeight);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //(9.0) initiate timer just to update the time
        TLBTimer systemTimer = new TLBTimer();
        schedule.scheduleRepeating(Schedule.EPOCH, 0, systemTimer);
        //(9.1) make agents
        try {
            makeAgentsInSpace();
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
        //(10) initiate observer
        TLBObserver observer = new TLBObserver();
        schedule.scheduleRepeating(Schedule.EPOCH, Integer.MAX_VALUE, observer);
        System.out.println("-----------------Finish Starting TLB environment-------------------------");
    }

//    public void importTiffVegRasterMap() throws IOException {
//        String tifVegRasterMapFile = OutputWriter.getFileName("/RESET_TLB_inputData/vegRaster_Tamarisk_20240730.tif");
//        File tiffVegRaster_tamarisk = new File(tifVegRasterMapFile); // the file name is TBD
//        GeoTiffReader reader = new GeoTiffReader(tiffVegRaster_tamarisk); //read the tiff file
//        GridCoverage2D cov = reader.read(null);
//
//        tiffRaster = cov.getRenderedImage().getData();
//        //get x, y bounds
//        System.out.println ("Raster bounds = " + tiffRaster.getBounds());
//        //get lon, lat bounds (longitude supplied first)
//        System.out.println(cov.getEnvelope());
//        //making use of the coordinate reference system
//        crs = cov.getCoordinateReferenceSystem2D();
//        //return a math transform for the two-dimensional part for conversion from world to grid coordinates.
//        gg = cov.getGridGeometry();
//        //NEW
//        System.out.println ("Width = " + tiffRaster.getWidth());
//        System.out.println ("Height = " + tiffRaster.getHeight());
//        vegetationGrid = new DoubleGrid2D(tiffRaster.getWidth(), tiffRaster.getHeight());
//        for (int x = 0; x < tiffRaster.getWidth(); x++) {
//            for (int y = 0; y < tiffRaster.getHeight(); y++) {
//                double value = tiffRaster.getSampleDouble(x, y, 0); // assumes 1 band
//                vegetationGrid.set(x, y, value);
//            }
//        }
//    }
    public void importTiffVegRasterMap() throws IOException {
        String tifVegRasterMapFile = OutputWriter.getFileName("/RESET_TLB_inputData/vegRaster_Tamarisk_20240730.tif");
        File tiffVegRaster_tamarisk = new File(tifVegRasterMapFile);

        GeoTiffReader reader = new GeoTiffReader(tiffVegRaster_tamarisk); //read the tiff file
        GridCoverage2D cov = reader.read(null);

        this.vegetationRaster = cov.getRenderedImage().getData(); //just keep the raster
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
    public void makeAgentsInSpace() throws TransformException {
//        int vegGridX = CoordinateConverter.longitudeXtoGridX(initialLon, xllcornerVeg, vegCellSize);
//        int vegGridY = CoordinateConverter.latitudeYtoGridY(initialLat, yllcornerVeg, vegCellSize, nRowsVeg);
        int vegGridX = CoordinateConverter.coordToGrid(crs, gridGeometry, initialLon, initialLat)[0];
        int vegGridY = CoordinateConverter.coordToGrid(crs, gridGeometry, initialLon, initialLat)[1];

        for (int i=0; i<initialN; i++) {
            tlbAgentID ++;
            int patchID = getPatchID(this, vegGridX, vegGridY); //get the patchID based on current location
            int tlbAge = random.nextInt(3) + 5; //randomly choose a range between 5-7 for adult
            TLBAgent a = new TLBAgent(this, tlbAgentID, Stage.TLBADULT, initialLon, initialLat, patchID, tlbAge);
            System.out.println("Agent #" + tlbAgentID + " is an " + a.tlbStage + " in initiation");
            a.event = schedule.scheduleRepeating(a);
            agentGrid.setObjectLocation(a, a.displayLocation);
        }
    }

    /*
    ***************************************************************************************************
    *                          Get Values from VegRasterMap or Table
    * *************************************************************************************************
     */
    public int getPatchID(TLBEnvironment state, int vegGridX, int vegGridY) {
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
            System.out.println("patchID: " + patchID);
            return patchID;
        }
    }

//    public TLBVegCell getVegMapCell(int vegGridX, int vegGridY) {
//        String mapKey = String.join("-", String.valueOf(vegGridX), String.valueOf(vegGridY));
//        return this.vegMapCell.get(mapKey);
//    }

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

    public int getInitialN() {
        return initialN;
    }

    public void setInitialN(int initialN) {
        this.initialN = initialN;
    }

    public double getInitialLon() {
        return initialLon;
    }

    public void setInitialLon(double initialLon) {
        this.initialLon = initialLon;
    }

    public double getInitialLat() {
        return initialLat;
    }

    public void setInitialLat(double initialLat) {
        this.initialLat = initialLat;
    }

    public double getMpAntPre() {
        return mpAntPre;
    }

    public void setMpAntPre(double mpAntPre) {
        this.mpAntPre = mpAntPre;
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

    public int getMpCriticalDayLength() {
        return mpCriticalDayLength;
    }

    public void setMpCriticalDayLength(int mpCriticalDayLength) {
        this.mpCriticalDayLength = mpCriticalDayLength;
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
