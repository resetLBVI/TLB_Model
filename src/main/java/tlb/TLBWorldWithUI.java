package tlb;

import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.portrayal.grid.FastValueGridPortrayal2D;
import sim.portrayal.grid.ValueGridPortrayal2D;
import sim.portrayal.grid.SparseGridPortrayal2D;
import sim.portrayal.simple.OvalPortrayal2D;
import sim.util.gui.SimpleColorMap;

import javax.swing.*;
import java.awt.*;
/*
*********************************************************************************************
* This class includes:
* (1) getName (2) getSimulationInspectedObject (3) init (4) quit (5) setupPortrayals
* (6) start (7) main
* *******************************************************************************************
 */
public class TLBWorldWithUI extends GUIState {

    Display2D display; //create a display
    JFrame displayFrame; //create a display frame
    //Portrayals
    FastValueGridPortrayal2D vegGridPortrayal = new FastValueGridPortrayal2D("vegetation grid");
//    ValueGridPortrayal2D vegGridPortrayal = new ValueGridPortrayal2D();
    SparseGridPortrayal2D TLBAgentPortrayal = new SparseGridPortrayal2D();

    //Constructor
    public TLBWorldWithUI(SimState state) { super(state);}
    public TLBWorldWithUI() { super (new TLBEnvironment(System.currentTimeMillis()));}

    //methods
    public static String getName() {return "RESET: TLB World";}

    public Object getSimulationInspectedObject() { return this.state; }

    public void init (Controller controller) {
        super.init(controller); //super from GUIState
        this.display = new Display2D(600, 600, this); //initially create a UI display
        this.display.attach(this.vegGridPortrayal, "vegetation Grids"); //attach the vegetation grids
        this.display.attach(this.TLBAgentPortrayal, "TLB Agents"); //attach the TLB agents
        this.displayFrame = this.display.createFrame(); //create a display frame
        controller.registerFrame(this.displayFrame); //setup display
        this.displayFrame.setVisible(true); //setup display
    }

    public void quit() {
        super.quit();
        if(this.displayFrame != null) {
            this.displayFrame.dispose();
        } //if there is a frame, dispose it
        this.displayFrame = null; //set the frame as null
        this.display = null; //set the display as null
    }

    public void start() {
        super.start();
        this.setupPortrayal();
    }

    public void setupPortrayal() {
        TLBEnvironment eState = (TLBEnvironment)state; //downcasting the TLBEnvironment
        //vegetation portrayal (showing the veg cells)
//        vegGridPortrayal.setField(eState.vegGrids.getGrid()); //connect the gridField from the Environment to UI
        vegGridPortrayal.setField(eState.vegetationGrid); //NEW
        Color color = new Color(0, 0, 255, 0); //blue
        this.vegGridPortrayal.setMap(new SimpleColorMap(0, 100, Color.WHITE, color));
        //agent portrayal (showing the agents as red dots)
        this.TLBAgentPortrayal.setField(eState.agentGrids);
        this.TLBAgentPortrayal.setPortrayalForAll(new OvalPortrayal2D(Color.RED, 0.7));
        //reschedule the display
        this.display.reset();
        this.display.setBackdrop(Color.WHITE); //set up the background color
        this.display.repaint();

    }
    /*
    **************************************************************************************
    *                           Main
    * ************************************************************************************
     */
    public static void main(String[] args) {
        TLBWorldWithUI worldGUI = new TLBWorldWithUI();
        Console console = new Console(worldGUI);
        console.setVisible(true);

    }
}
