package tlb;

import sim.engine.SimState;
import sim.engine.Steppable;


public class TLBObserver implements Steppable {
    @Override
    public void step(SimState simState) {
        TLBEnvironment eState = (TLBEnvironment) simState; //downcasting the TLB environment
        //collect population data in the last week of the year
//        if (eState.currentWeek % 51 ==0) {
            collectPopData (eState);
            reset(eState);
//        }
    }

    /*
    ******************************************************************************************
    *                                      Data Collection
    * ****************************************************************************************
     */
    public void collectPopData (TLBEnvironment state) {
        //start writing
        System.out.println("week " + state.currentWeek + " in Data Collection");
        System.out.println("num of death: " + state.numDeath) ;
        state.populationSize = state.agentGrid.getAllObjects().numObjs;
        String popInfo = String.format("%s,%s,%s,%s,%s,%s,%s,%s", state.currentYear, state.populationSize, state.numBirth, state.numDeath, state.numDeathInADULT,
                state.numDeathINEGG, state.numDeathInLARVA, state.numDeathInPUPA);
        state.popSummaryWriter.addToFile(popInfo); //write to the file
    }

    public void reset (TLBEnvironment state) {
        state.numBirth = 0;
        state.numDeath = 0;
        state.numDeathInADULT = 0;
        state.numDeathINEGG = 0;
        state.numDeathInLARVA = 0;
        state.numDeathInPUPA = 0;
    }

}
