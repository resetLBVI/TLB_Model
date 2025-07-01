package tlb;

import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.portrayal.SimplePortrayal2D;
import sim.portrayal.simple.ScaledImagePortrayal;
import sim.portrayal.grid.SparseGridPortrayal2D;
import sim.portrayal.simple.OvalPortrayal2D;
import sim.util.Int2D;
import tlb.Utils.OutputWriter;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

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
    SparseGridPortrayal2D backgroundPortrayal = new SparseGridPortrayal2D();
//    SparseGridPortrayal2D TLBAgentPortrayal = new SparseGridPortrayal2D();
    SparseGridPortrayal2D TLBAgentPortrayal = new SparseGridPortrayal2D() {
        @Override
        public SimplePortrayal2D getPortrayalForObject(Object obj) {
            TLBEnvironment eState = (TLBEnvironment)state;
            Int2D loc = eState.agentGrid.getObjectLocation(obj);
//            System.out.println("Drawing agent at: " + loc);
            return new OvalPortrayal2D(Color.BLACK, 6);
        }
    };


    //Constructor
    public TLBWorldWithUI(SimState state) throws IOException { super(state);}
    public TLBWorldWithUI() {
        super(new TLBEnvironment(System.currentTimeMillis()));
    }

    //methods
    public static String getName() {return "RESET: TLB World";}

    public Object getSimulationInspectedObject() { return this.state; }

    public void init (Controller controller) {
        super.init(controller); //super from GUIState
        display = new Display2D(800, 800, this);
        displayFrame = display.createFrame();
        controller.registerFrame(displayFrame);
        displayFrame.setVisible(true);
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
        String bgFileName;
        try {
            bgFileName = OutputWriter.getFileName("/RESET_TLB_inputData/RESET_model_UI_background.jpg");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //load the image
        Image img = new ImageIcon(bgFileName).getImage();
        System.out.println ("image loaded: " + (img != null));
        //custom background portrayal using the background image
        // Assign portrayal
//        backgroundPortrayal = new SparseGridPortrayal2D();
        backgroundPortrayal.setField(eState.backgroundGrid);
        backgroundPortrayal.setPortrayalForAll(new ScaledImagePortrayal(img));

        //agent portrayal (showing the agents as red dots)
        TLBAgentPortrayal.setField(eState.agentGrid);
        TLBAgentPortrayal.setPortrayalForClass(TLBAgent.class, new OvalPortrayal2D(Color.BLACK, 0.2));

        //attach portryal to display
        display.detachAll();
        display.attach(backgroundPortrayal, "Image Layer", true);
        display.attach(TLBAgentPortrayal, "TLB Agents");

//        TLBAgentPortrayal.setPortrayalForAll(new OvalPortrayal2D(Color.BLACK, 100));

        System.out.println("Number of Agents in UI: " + eState.agentGrid.allObjects.numObjs);

        display.setClipping(false);
        display.setScale(0.2);// or smaller if zoomed out
        this.display.setBackdrop(Color.WHITE); //set up the background color
        //reschedule the display
        this.display.reset();
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
