package tlb;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.util.Bag;

public class TLBVegCell implements Steppable {
    //cell vegetation state variables
    Bag members;
    int vegGridX;
    int vegGridY;
    int patchID;
    int numColonizedAgentsSteps; //the number of agents in the cell has been fed
    boolean defoliation;
    int numOfDefoliations; //number of defoliation of the cell
    int patchPercentTamarisk; //indicate how many tamarisks (patch quality) in the patch
    //scheduling
    Stoppable event; //schedule to stop the event

    //Constructor

    public TLBVegCell(TLBEnvironment state, Bag members, int vegGridX, int vegGridY, int patchID, int numColonizedAgentsSteps, boolean defoliation) {
        this.members = members;
        this.vegGridX = vegGridX;
        this.vegGridY = vegGridY;
        this.patchID = patchID;
        this.numColonizedAgentsSteps = numColonizedAgentsSteps;
        this.defoliation = defoliation;
        this.numOfDefoliations = 0;
        for (int i = 0; i< members.numObjs; i++) {
            TLBAgent a = (TLBAgent) members.objs[i];
            a.setTlbHostCell(this);
        }
        String activateInfo = String.format("%s,%s,%s,%s,%s,%s,%s", state.currentYear, state.currentWeek, this.defoliation,
                this.vegGridX, this.vegGridY, this.patchID, numOfDefoliations);
        state.impactWriter.addToFile(activateInfo);

    }

    @Override
    public void step(SimState state) {
        TLBEnvironment eState = (TLBEnvironment) state; //Downcasting the TLB Environment
//        this.week = (int) (eState.schedule.getSteps() % 52); //the week is from 0-51 in the current year
        if(numColonizedAgentsSteps >= eState.mpTlbFeed) {
            //collect impact data when a cell is dead - collect "year" "week" "vegGridX" "vegGridY" "patchID"
//            death(eState); //the cell won't die, make this change on 07-17-2025
            this.defoliation = true;
            numOfDefoliations++;
            this.numColonizedAgentsSteps = 0; //reset the number of colonized agents
            //record the impact data
            String impactInfo = String.format("%s,%s,%s,%s,%s,%s,%s", eState.currentYear, eState.currentWeek, this.defoliation,
                    this.vegGridX, this.vegGridY, this.patchID, numOfDefoliations);
            eState.impactWriter.addToFile(impactInfo);
        }

    }

    public void death(TLBEnvironment state) {
        this.numColonizedAgentsSteps = 0; //reset the number of agents in the cell
        event.stop(); //schedule to stop the event
        members.clear(); //clear all the members in the cell
    }

    /**
     * Add a new member in the cell
     * @param agent
     * @return
     */
    public boolean addCellMembers(TLBAgent agent) {
        final boolean results = members.add(agent);
        if (results) { //if adding a new member
            numColonizedAgentsSteps++; //the numAgentsTimeSteps increased by one
            agent.setTlbHostCell(this); //set this agent into the cell
            return true;
        } else { //not adding a member
            return false;
        }
    }
}
