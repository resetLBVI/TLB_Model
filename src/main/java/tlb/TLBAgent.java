package tlb;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.util.Bag;
import sim.util.Int2D;
import tlb.Utils.CoordinateConverter;
import tlb.Utils.TamariskLookup;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static tlb.Utils.DayLengthLookup.getDayLength;

public class TLBAgent implements Steppable {
    //agent state variable
    int tlbAgentID; // a unique ID for each super agent
    Stage tlbStage; //state an agent's current stage
    double tlbLonX; //longitude i.e., tlblong
    double tlbLatY; //latitude i.e., tlblat
    int vegGridX; //x loation on the vegetation grid map
    int vegGridY; //y location on the vegetation grid map
    int displayX; //x location on the display window
    int displayY; //y location on the display window
    Int2D displayLocation; //agent's current location in UI
    int tlbAge; //indicate the current age (in weeks/ticks)
    //diapause
    boolean tlbDiapause; //determine the agent involve in the diapause or not
    double tlbCurrDayLeng; //the current daylength for the week and at their (tlbLat)
    //movement (dispersal)
    double tlbDispDist; //dispersal distance (m)
    double tlbDispDir; //dispersal direction (degree)
    ExponentialDistribution exponentialGenerator; //randomly generate a number for moving distance following an exponential distribution
    //reproduction
    int tlbSpawn; //indicate the number of eggs an agent can produce
    //veg defoliation
    TLBVegCell tlbHostCell; //an agent claim a veg cell when they feed on the foliage
    int patchID; //the patch ID an agent occupied
    TamariskLookup tamariskLookup;
    double currentPTamarisk;
    //other schedule variables
    Stoppable event; //schedule to stop the event
    Long currentStep; //identify current step
    //life history data collection
    Map<String, Double> locationData; //a hashmap to record the location data
    Map<String, Long> dateData; //a hashmap to record the date data. e.g., birthday, death date, etc
    Map<String, Enum> stageData; //a hashmap to record the stage when an event occurs. e.g., death stage
    Map<String, Integer> ageData; //a hashmap to record the age when an event occurs. e.g., death age
    String actionExecuted;

    /*
    *************************************************************************
    *                      Constructor
    * ***********************************************************************
     */
    public TLBAgent(TLBEnvironment state, int tlbAgentID, Stage tlbStage, double tlbLonX, double tlblatY, int patchID, int tlbAge) {
        this.tlbAgentID = tlbAgentID;
        this.tlbStage = tlbStage;
        this.tlbLonX = tlbLonX; //tlb longitude
        this.tlbLatY = tlblatY; //tlb latitude
        //get vegetation grid coordinates based on the tiff file grids
        this.vegGridX = CoordinateConverter.longitudeXtoGridX(tlbLonX, state.xllcornerVeg, state.vegCellSize);
        this.vegGridY = CoordinateConverter.latitudeYtoGridY(tlblatY, state.yllcornerVeg, state.vegCellSize, state.nRowsVeg);
        //convert to smaller display grid
        displayX = vegGridX * state.agentGrid.getWidth() / state.vegetationRaster.getWidth();
        displayY = vegGridY * state.agentGrid.getHeight() / state.vegetationRaster.getHeight();
        this.displayLocation = new Int2D(displayX, displayY);
        this.tlbAge = tlbAge;
        this.tlbDiapause = false;
        this.tlbSpawn = state.random.nextInt(state.mpTlbSpawn);
        this.patchID = patchID;
        this.currentPTamarisk = 0;
        // CH fix -- adding hashmap constructor
        this.dateData = new HashMap<>();
        this.locationData = new HashMap<>();
        this.stageData = new HashMap<>();
        this.ageData = new HashMap<>();
        this.actionExecuted = "null";

        System.out.println("tlbAgentID: " + tlbAgentID);
        System.out.println("tlbLonX: " + tlbLonX + ", tlbLatY: " + tlbLatY);
        System.out.println("vegGridX: " + vegGridX + ", vegGridY: " + vegGridY);
        System.out.println("displayLocation: " + displayLocation);

    }

    @Override
    public void step(SimState state) {
        TLBEnvironment eState = (TLBEnvironment) state; //Downcasting the PSHB Environment
        currentStep = eState.schedule.getSteps(); //get the current step, each step represents a week
        System.out.println("========Agent " + this.tlbAgentID + " has started current step = " + currentStep + "========"); //print in console
        //Step 1: check the age, if the age hit the maxAge, the agent died
        if (tlbAge == 0) { //egg stage
            tlbStage = Stage.TLBEGG;
        } else if (tlbAge >= 1 && tlbAge <= 3) { //larva stage
            tlbStage = Stage.TLBLARVA;
        } else if (tlbAge == 4) { //pupa stage
            tlbStage = Stage.TLBPUPA;
        } else if (tlbAge >= 5 && tlbAge < 8) { //adult stage
            tlbStage = Stage.TLBADULT;
        } else { //when the tlbAge >=8, this agent die
            death(eState);
            return;
        }
        System.out.println("this tlbStage: " + tlbStage);
        //Step 2: Take actions, perform the actions according to their stage
        takeAction(this.tlbStage, eState); //taking actions according to their stages
        System.out.println("action executed: " + this.actionExecuted); //debug
        //Step 3: testing and logging
        String agentStepLog = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", currentStep, eState.currentWeek, eState.currentYear, this.tlbAgentID,
                this.tlbStage, this.tlbAge, this.tlbLonX, this.tlbLatY, this.vegGridX, this.vegGridY, this.displayX, this.displayY,
                this.actionExecuted);
        eState.logWriter.addToFile(agentStepLog);
        this.actionExecuted = "null"; //reset the action executed
        //Step 4: Age increment by one
        tlbAge ++;
    }
    /*
    *******************************************************************************
    *                               TAKE ACTIONS
    * *****************************************************************************
     */
    public void takeAction(Stage tlbStage, TLBEnvironment state) {
        switch (tlbStage) {
            case TLBADULT:
                //check diapause
                if (checkDiapause(state)) { //if diapause == true, there is 50% die and 50% pause all actions
                    if(state.random.nextBoolean()) {
                        System.out.println ("this ADULT agent is dead"); //debug
                        this.actionExecuted = "ADULT:diapause_death"; //log
                        state.numDeath ++;
                        death(state);
                    } else { //if not died, do nothing for the step
                        System.out.println("this ADULT agent is during diapause");
                        this.actionExecuted = "ADULT:diapause_survived"; //log
                        return;
                    }
                } else { //if the diapause == false, the agent can execute actions
                    //During the ADULT stage, agents disperse, feed, and lay eggs
                    dispersal (state); //execute dispersal action
                    if (this.currentPTamarisk > 1) {
                        feed_colonizeACell(state);
                        laying(state,this);
                        this.actionExecuted = "ADULT:dispersal_feed_laying"; //log
                    } else if (this.currentPTamarisk == 1) {
                        feed_colonizeACell(state);
                        this.actionExecuted = "ADULT:dispersal_feed"; //log
                    }
                }
                break;
            case TLBEGG:
                //mortality: (1) diapause (2) ant predation
                if(checkDiapause(state) || state.random.nextBoolean(state.mpAntPre)) { //diapause and ant predation cause the death
                    this.actionExecuted = "EGG:mortality"; //log
                    state.numDeath ++;
                    death(state);
                    return;
                }
                //During the EGG stage, the agents that survive to hatch enter the LARVA stage
                this.actionExecuted = "EGG:survived"; //log
                break;
            case TLBLARVA:
                //mortality: (1) diapause (2) ant predation
                if(checkDiapause(state) || state.random.nextBoolean(state.mpAntPre)) { //diapause and ant predation cause the death
                    this.actionExecuted = "LARVA:mortality"; //log
                    state.numDeath ++;
                    death(state);
                }
                //check the vegetation quality to see if the agent will die
                if(this.currentPTamarisk == 0) {
                    this.actionExecuted = "LARVA:poorTamariskPatch_death"; //log
                    state.numDeath ++;
                    death(state);
                } else { //if the patchPercentTamarisk >=1
                    this.actionExecuted = "LARVA:feed"; //log
                    feed_colonizeACell(state);
                }
                break;
            case TLBPUPA:
                //mortality: (1) diapause (2) ant predation
                if(checkDiapause(state) || state.random.nextBoolean(state.mpAntPre)) { //diapause and ant predation cause the death
                    this.actionExecuted = "PUPA:mortality"; //log
                    state.numDeath ++;
                    death(state);
                }
                this.actionExecuted = "PUPA:survived";
                break;
            default:
                System.out.println("Unknown TLB stage");
                break;
        }
    }

    /*
    *******************************************************************************
    *                                   Diapause
    * ******************************************************************************
     */

    /**
     * This method determines whether an agent should enter diapause (a form of dormancy or pause in development) based on
     * the day length from a lookup table
     * @param state
     * @return
     */
    public boolean checkDiapause(TLBEnvironment state) {
        //check current day length by using look-up table
        double currentLat = this.tlbLatY;
//        System.out.println(" currentWeek: " + state.currentWeek);
        tlbCurrDayLeng = getDayLength(currentLat, state.currentWeek);
//        System.out.println("check Diapause - Latitude: " + currentLat + ", Week: " + state.currentWeek + ", Day Length: " + tlbCurrDayLeng);
        //check whether they are at the autumnal equinox (week > 39) and the day length
        if(state.currentWeek > 39 && tlbCurrDayLeng <= state.mpCriticalDayLength) { //if the current day length is not long enough, go diapause
            System.out.println("diapause == true");
            return true;
        } else {
            System.out.println("diapause == false");
            return false;
        }
    }

    /*
    ********************************************************************************
    *                                   Dispersal
    * The agent in a cell where there is no tamarisk (patchPercentTamarisk = 0)
    * will move a random distance and direction until reaching a cell with tamarisk
    *
    * *******************************************************************************
     */
    public void dispersal(TLBEnvironment state) {
        //determine the distance, direction, and move to the new location
        System.out.println("This agent starts to disperse, before location vegGridX: " + vegGridX + ", vegGridY: " + vegGridY); //debug
        findANewLocation(state); //provide a new location (vegGridX, vegGridY)
//        System.out.println("dispersal new location vegGridX: " + vegGridX + " vegGridY: " + vegGridY); //print the current location
//        System.out.println("new display location: " + displayX + ", " + displayY); //debug
        // Read raster value, which is the patch ID if there is one
        this.patchID = state.getPatchID(state, vegGridX, vegGridY);
        if (this.patchID == -1 || this.patchID == 0) {// if agents disperse to a place beyond RESET (patches), the agent die
            System.out.println("patch ID= " + this.patchID + "this agent is out of RESET area and dies."); //the value is the patch ID
            state.numDeath ++;
            this.actionExecuted = "ADULT: disperseOutsideRESET_death";
            death(state);
            return;
        }
        System.out.println("patch ID in the RESET: " + this.patchID); //the value is the patch ID
        // (2) read the patch quality from the vegetation patch
        this.currentPTamarisk = tamariskLookup.getPTamarisk(this.patchID);
        //(3) return current percentage of tamarisk (0-9)
    }
    public void findANewLocation (TLBEnvironment state) {
        //get current location vegGridX and vegGrids
        //determine the dispersal distance and direction
        exponentialGenerator = new ExponentialDistribution(state.mpTlbDisperse*500); //create a negative exponential distribution
        tlbDispDist = exponentialGenerator.sample(); //the movement distance follows an exponential distribution, sample from the distribution
        Random random = new Random();
        this.tlbDispDir = random.nextDouble() * 2 * Math.PI; //randomly choose a direction in degrees
        //debug
//        System.out.println("tlbDispDir: " + tlbDispDir + "; tlbDispDist: " + tlbDispDist);
        //move to the new location
        this.tlbLonX = this.tlbLonX + tlbDispDist * Math.cos(tlbDispDir); //new coordinate X
        this.tlbLatY = this.tlbLatY + tlbDispDist * Math.sin(tlbDispDir); //new coordinate Y
        vegGridX = CoordinateConverter.longitudeXtoGridX(tlbLonX, state.xllcornerVeg, state.vegCellSize); //new Grid X
        vegGridY = CoordinateConverter.latitudeYtoGridY(tlbLatY, state.yllcornerVeg, state.vegCellSize, state.nRowsVeg); //new Grid Y
        //convert to display location
        displayX = CoordinateConverter.getVegToDisplayX(state, vegGridX);
        displayY = CoordinateConverter.getVegToDisplayY(state, vegGridY);
        state.agentGrid.setObjectLocation(this, displayX, displayY); //set the agent location on the display
    }


    /*
    *********************************************************************************
    *                                   Feeding
    * *******************************************************************************
     */


    public void feed_colonizeACell(TLBEnvironment state) {
        System.out.println("This agent colonize a cell and feed.");//debug
        if(this.patchID != 0 ) { //this grid is within RESET area
            TLBVegCell cell = (TLBVegCell) state.vegCellGrid.get(vegGridX, vegGridY);
            //add the agent into the cell, activate a new cell or join a current active cell
            if (cell == null) {
                //activate a new cell
                Bag members = new Bag();
                TLBVegCell newActiveCell = new TLBVegCell(members, vegGridX, vegGridY, this.patchID, 0, false); //activate a new veg cell
                state.vegCellGrid.set(vegGridX, vegGridY, newActiveCell); //set the cell on the vegCellGrid
                newActiveCell.addCellMembers(this); //add this member into the entity
            } else { //if there is already someone in the location
                cell.addCellMembers(this);
                state.agentGrid.setObjectLocation(cell, vegGridX, vegGridY); //set the entity in the space
            }
        }
    }


    /*
    *********************************************************************************
    *                                  Reproduction
    * *******************************************************************************
     */

    public void laying(TLBEnvironment state, TLBAgent parent) {
        System.out.println("This agent is laying an egg.");//debug
        double tlbLonX_newborn = parent.tlbLonX; //determine the longitude X for the newborn
        double tlbLatY_newborn = parent.tlbLatY; //determine the latitude Y for the newborn
        for (int i=0; i<parent.tlbSpawn; i++) {
            state.tlbAgentID ++; //provide an unique ID for the newborn
            int patchID_newborn = parent.patchID; //the eggs will be in the same patch as the parents
            TLBAgent a = new TLBAgent(state, tlbAgentID, Stage.TLBEGG, tlbLonX_newborn, tlbLatY_newborn, patchID_newborn, 0); //create a newborn
            System.out.println ("tlbAgentID: " + tlbAgentID + " has started his life at step " + currentStep);
            a.dateData.put("birthday", currentStep); //record the birthday
            a.locationData.put("lonAtBirth", tlbLonX_newborn); //record the x location of the newborn
            a.locationData.put("latAtBirth", tlbLonX_newborn); //record the y location of the newborn
            a.locationData.put("patchAtBirth", (double)patchID_newborn);
        }
        state.numBirth += tlbSpawn;
    }



    /*
    *********************************************************************************
    *                                   DEATH
    * *******************************************************************************
     */
    public void death (TLBEnvironment state) {
        //get Death Data
        this.dateData.put("dateOfDeath", currentStep); //record the date of death
        this.locationData.put("lonAtDeath", this.tlbLonX); //record the longitude at death
        this.locationData.put("latAtDeath", this.tlbLatY); //record the latitude at death
        this.stageData.put("deathStage", this.tlbStage); //record the death stage
        this.ageData.put("deathAge",this.tlbAge);//record the death age
        //create a String list that can store all the information
        String lifeHistoryInfo = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", currentStep, this.tlbAgentID,
                this.dateData.get("birthday"), this.dateData.get("dateOfDeath"), this.locationData.get("lonAtBirth"),
                this.locationData.get("latAtBirth"), this.locationData.get("lonAtDeath"), this.locationData.get("latAtDeath"),
                this.stageData.get("deathStage"), this.ageData.get("deathAge"));
        state.agentSummaryWriter.addToFile(lifeHistoryInfo); //add the information into file
        //stop the event
        event.stop();
    }

//    public void outsideRESET() {
//        event.stop();
//        this.TLBVegCell =
//    }

    /*
    *********************************************************************************
    *                               HELPER METHODs
    * *******************************************************************************
     */

    public void setTlbHostCell(TLBVegCell tlbHostCell) {
        this.tlbHostCell = tlbHostCell;
    }
}
