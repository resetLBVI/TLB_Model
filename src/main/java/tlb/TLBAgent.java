package tlb;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.util.Bag;
import tlb.Utils.CoordinateConverter;
import tlb.Utils.TamariskLookup;

import java.util.Map;

import static tlb.Utils.DayLengthLookup.getDayLength;

public class TLBAgent implements Steppable {
    //agent state variable
    int tlbAgentID; // a unique ID for each super agent
    Stage tlbStage; //state an agent's current stage
    double tlbLonX; //longitude i.e., tlblong
    double tlbLatY; //latitude i.e., tlblat
    int vegGridX; //x loation on the vegetation grid map
    int vegGridY; //y location on the vegetation grid map
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

    /*
    *************************************************************************
    *                      Constructor
    * ***********************************************************************
     */
    public TLBAgent(TLBEnvironment state, int tlbAgentID, Stage tlbStage, double tlbLonX, double tlblatY, int patchID) {
        this.tlbAgentID = tlbAgentID;
        this.tlbStage = tlbStage;
        this.tlbLonX = tlbLonX; //tlb longitude
        this.tlbLatY = tlblatY; //tlb latitude
        this.vegGridX = CoordinateConverter.longitudeXtoGridX(tlbLonX, state.xllcornerVeg, state.vegCellSize);
        this.vegGridY = CoordinateConverter.latitudeYtoGridY(tlblatY, state.yllcornerVeg, state.vegCellSize, state.nRowsVeg);
        this.tlbDiapause = false;
        this.tlbSpawn = state.random.nextInt(state.mpTlbSpawn);
        this.patchID = patchID;
        this.currentPTamarisk = 0;


    }

    @Override
    public void step(SimState state) {
        TLBEnvironment eState = (TLBEnvironment) state; //Downcasting the PSHB Environment
        currentStep = eState.schedule.getSteps(); //get the current step
        System.out.println("========Agent " + this.tlbAgentID + " has started current step = " + currentStep + "========");
        //Step 1: check the age, if the age hit the maxAge, the agent died
        if (tlbAge == 0) { //egg stage
            tlbStage = Stage.TLBEGG;
        } else if (tlbAge >= 1 && tlbAge <= 3) {
            tlbStage = Stage.TLBLARVA;
        } else if (tlbAge == 4) {
            tlbStage = Stage.TLBPUPA;
        } else if (tlbAge >= 5 && tlbAge < 8) {
            tlbStage = Stage.TLBADULT;
        } else { //when the tlbAge >=8, this agent die
            death(eState);
            return;
        }
        System.out.println("this tlbStage: " + tlbStage);
        //Step 2: Take actions, perform the actions according to their stage
        takeAction(this.tlbStage, eState); //taking actions according to their stages
        //Step 3: Age increment by one
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
                        death(state);
                    } else {
                        return;
                    }
                } else { //if the diapause == false, the agent can execute actions
                    //During the ADULT stage, agents disperse, feed, and layeggs
                    dispersal (state);
                    if (this.currentPTamarisk > 1) {
                        feed(state);
                        laying(state,this);
                    } else if (this.currentPTamarisk == 1) {
                        feed(state);
                    }
                }
                break;
            case TLBEGG:
                //mortality: (1) diapause (2) ant predation
                if(checkDiapause(state) || state.random.nextBoolean(state.mpAntPre)) { //diapause and ant predation cause the death
                    death(state);
                }
                //During the EGG stage, the agents that survive to hatch enter the LARVA stage
                break;
            case TLBLARVA:
                //mortality: (1) diapause (2) ant predation
                if(checkDiapause(state) || state.random.nextBoolean(state.mpAntPre)) { //diapause and ant predation cause the death
                    death(state);
                }
                //check the vegetation quality to see if the agent will die
                if(this.currentPTamarisk == 0) {
                    death(state);
                } else { //if the patchPercentTamarisk >=1
                    feed(state);
                }
                break;
            case TLBPUPA:
                //mortality: (1) diapause (2) ant predation
                if(checkDiapause(state) || state.random.nextBoolean(state.mpAntPre)) { //diapause and ant predation cause the death
                    death(state);
                }
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
        int currentWeek = state.year;
        tlbCurrDayLeng = getDayLength(currentLat, currentWeek);
        System.out.println("Latitude: " + currentLat + ", Week: " + currentWeek + ", Day Length: " + tlbCurrDayLeng);
        //check whether they are at the autumnal equinox (week > 39) and the day length
        if(state.week > 39 && tlbCurrDayLeng <= state.mpCriticalDayLength) { //if the current day length is not long enough, go diapause
            return true;
        } else {
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
        findANewLocation(state); //provide a new location (vegGridX, vegGridY)
        // Read raster value, which is the patch ID if there is one
        this.patchID = state.getPatchID(state, vegGridX, vegGridY);
        System.out.println("patch val= " + this.patchID); //the value is the patch ID
        // (2) read the patch quality from the vegetation patch
        this.currentPTamarisk = tamariskLookup.getPTamarisk(this.patchID);
        //(3) return current percentage of tamarisk (0-9)
    }
    public void findANewLocation (TLBEnvironment state) {
        //get current location vegGridX and vegGrids
        //determine the dispersal distance and direction
        exponentialGenerator = new ExponentialDistribution(state.mpTlbDisperse); //create a negative exponential distribution
        tlbDispDist = exponentialGenerator.sample(); //the movement distance follows an exponential distribution, sample from the distribution
        tlbDispDir = Math.random() * 360; //randomly choose a direction in degrees
        this.tlbDispDir = Math.toRadians(tlbDispDir); //convert to Radians
        //move to the new location
        this.tlbLonX = this.tlbLonX + tlbDispDist * Math.cos(tlbDispDir); //new coordinate X
        this.tlbLatY = this.tlbLatY + tlbDispDist * Math.sin(tlbDispDir); //new coordinate Y
        vegGridX = CoordinateConverter.longitudeXtoGridX(state.xllcornerVeg, state.vegCellSize, state.vegCellSize); //new Grid X
        vegGridY = CoordinateConverter.latitudeYtoGridY(tlbLatY, state.yllcornerVeg, state.vegCellSize, state.vegCellSize); //new Grid Y
        state.agentGrids.setObjectLocation(this, vegGridX, vegGridY); //set the agent at the location
        //log
    }


    public void colonizeACell (TLBEnvironment state) {
        this.tlbHostCell = state.getVegMapCell(vegGridX, vegGridY); // the hostCell is an entity of the model
        this.tlbHostCell.numColonizedAgentsSteps ++;
        this.patchID = state.getPatchID(state, vegGridX, vegGridY);
        this.currentPTamarisk = tamariskLookup.getPTamarisk(this.patchID);
        if(this.patchID != 0) { //this grid is within RESET area
            //add the agent into the cell, activate a new cell or join a current active cell
            if (state.agentGrids.getObjectsAtLocation(vegGridX, vegGridY) == null) {
                Bag members = new Bag();
                TLBVegCell newActiveCell = new TLBVegCell(members, vegGridX, vegGridY, this.patchID, 0, false); //activate a new veg cell
                state.vegMapCell.put(String.join("-", String.valueOf(newActiveCell.vegGridX), String.valueOf(newActiveCell.vegGridY)), newActiveCell); //set the entity
                newActiveCell.addCellMembers(this); //add this member into the entity
                state.agentGrids.setObjectLocation(newActiveCell, vegGridX, vegGridY); //set the vegCell entity in the space
            } else { //if there is already someone in the location
                TLBVegCell joinCurrentCell = state.getVegMapCell(vegGridX, vegGridY); //get the active cell
                joinCurrentCell.addCellMembers(this); //add this current agent into the cell
                state.agentGrids.setObjectLocation(joinCurrentCell, vegGridX, vegGridY); //set the entity in the space
            }
        }
    }

    /*
    *********************************************************************************
    *                                   Feeding
    * *******************************************************************************
     */


    public void feed(TLBEnvironment state) {
        colonizeACell(state);
    }


    /*
    *********************************************************************************
    *                                  Reproduction
    * *******************************************************************************
     */

    public void laying(TLBEnvironment state, TLBAgent parent) {
        double tlbLonX_newborn = parent.tlbLonX; //determine the longitude X for the newborn
        double tlbLatY_newborn = parent.tlbLatY; //determine the latitude Y for the newborn
        for (int i=0; i<parent.tlbSpawn; i++) {
            state.tlbAgentID ++; //provide an unique ID for the newborn
            int patchID_newborn = parent.patchID; //the eggs will be in the same patch as the parents
            TLBAgent a = new TLBAgent(state, tlbAgentID, Stage.TLBEGG, tlbLonX_newborn, tlbLatY_newborn, patchID_newborn); //create a newborn
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

    /*
    *********************************************************************************
    *                               HELPER METHODs
    * *******************************************************************************
     */

    public void setTlbHostCell(TLBVegCell tlbHostCell) {
        this.tlbHostCell = tlbHostCell;
    }
}
