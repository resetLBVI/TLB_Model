package tlb;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.util.Bag;

public class TLBTerritory implements Steppable {
    //Identity
    int terrID;
    int patchID;
    // Spatial membership — cells belonging to this territory
    Bag memberCells;   // raster cell coordinates or cell references within the territory
    Bag memberAgents;  // TLBAgent instances currently in this territory
    // TLB agent impact tracking
    int terrNTlb;                  // current number of TLB agents in territory
    double terrTotalTamariskFeed;  // the impact/stress of TLB agent on the Tamarisk: terrNTlb / mpTlbFeed
    int terrNumDefoliation;        // cumulative defoliation counter
    // Vegetation state (read from input initially)
    double pTamariskAtStart;
    double terrTamariskDensity;
    boolean permanentlyDefoliated; // true when terrNumDefoliation >= 5 and no regrowth
    //scheduling
    Stoppable event; //schedule to stop the event

    //Constructor

    public TLBTerritory(TLBEnvironment state, int terrID, int patchID, int terrNumDefoliation, double terrTamariskDensity,
                        double pTamarisk, boolean permanentlyDefoliated) {
        this.terrID = terrID;
        this.patchID = patchID;
        this.memberCells = new Bag();
        this.memberAgents = new Bag();
        this.terrNTlb = 0;
        this.terrTotalTamariskFeed = 0.0;
        this.terrNumDefoliation = terrNumDefoliation;
        this.pTamariskAtStart = pTamarisk;
        this.terrTamariskDensity = terrTamariskDensity;
        this.permanentlyDefoliated = permanentlyDefoliated;
    }

    @Override
    public void step(SimState state) {
        TLBEnvironment eState = (TLBEnvironment) state; //Downcasting the TLB Environment
        // Update agent count and feeding pressure
        this.terrNTlb = memberAgents.numObjs;
        this.terrTotalTamariskFeed = (double) terrNTlb / eState.mpTlbFeed;

        // Check defoliation threshold
        if (terrNTlb >= eState.mpTlbFeed && !permanentlyDefoliated) {
            terrNumDefoliation++;
            terrTamariskDensity --;
        }

        // Check for permanent defoliation after 5 events
        if (eState.currentWeek == 1) {
            if (terrNumDefoliation >= 5) {
                this.terrTamariskDensity = 0;
                this.permanentlyDefoliated = true;
            } else { //Spring regrowth
                this.terrTamariskDensity = this.pTamariskAtStart;
            }
        }
        // Record impact
        String impactInfo = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s", eState.currentYear, eState.currentWeek, this.terrID,
                this.patchID, this.pTamariskAtStart, this.terrNTlb, this.terrTamariskDensity, this.terrNumDefoliation,
                this.permanentlyDefoliated);
        eState.impactWriter.addToFile(impactInfo);
    }
}
