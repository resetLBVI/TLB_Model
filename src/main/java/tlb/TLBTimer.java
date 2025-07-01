package tlb;

import sim.engine.SimState;
import sim.engine.Steppable;

public class TLBTimer implements Steppable {
    @Override
    public void step(SimState simState) {
        TLBEnvironment eState = (TLBEnvironment) simState; //downcasting the TLB environment
        //update the timeer
        eState.updateYear();
        eState.updateWeek();
        System.out.println("===================================================");
        System.out.println("Update Current week:" + eState.currentWeek);
        System.out.println("Update Current year:" + eState.currentYear);
        System.out.println("===================================================");

    }
}
