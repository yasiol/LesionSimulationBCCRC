package TumorModel_1;

//constant division and death probability
//if normal tumor cell:
//first check is death requirment is met
//die if: random number generated for the agent in that step is less than death probability
//this will happen because: sample from RV [0,1]: random number generator= u
//probability that that number (u) is less than x is just x
//eg. if you have 0.7 change of death, and sample from [0,1], checks if that number is less than 0.7
//Prob unif[0,1]<0.7 is 0.7: die is random number is less than 0.7 because that occurs with probability 0.7
//divide if: random number generated for the agent in that step is less than division probability
//there is a chance of survival but no division as well

import HAL.GridsAndAgents.AgentGrid2D;
import HAL.GridsAndAgents.AgentSQ2Dunstackable;
import HAL.Gui.GifMaker;
import HAL.Gui.GridWindow;
import HAL.Rand;
import HAL.Util;

import static HAL.Util.MooreHood;

class Cell extends AgentSQ2Dunstackable<Grid> {
    public int type; //type determines the metabolic rate of the cell

    public void StepCell(){
        double deathProb, divProb; //division and death probabiity depend on the metabolic rate of the tumour cell

        if (this.type==G.NORMAL){
            deathProb= G.DEATH_PROB;
            divProb= G.DIV_PROB;
        } else{
            deathProb= G.DEATH_PROB;
            divProb= G.DIV_PROB;
        }

        if (G.rng.Double()<deathProb){ //cell dies
            Dispose();
            return;
        }

        if (G.rng.Double()<divProb){ //cell divides
            int options= MapEmptyHood(G.divHood);
            if (options>0){
                G.NewAgentSQ(G.divHood[G.rng.Int(options)]).type= this.type; //daughter cell has same metabolic rate as parent
            }
        }
    }

}

public class Grid extends AgentGrid2D<Cell> {
    //model constants
    public final static int NORMAL= Util.RGB(0,1,0); //tumour cell with normal metabolic rate
    //Util RGB: constant serving as colors for drawing and labels for different cell types
    public double DEATH_PROB= 0.001, DIV_PROB= 0.01;
    //internal model objects
    public Rand rng; //random number generator
    public int[] divHood= MooreHood(false);

    public Grid(int x, int y, Rand rng){ //grid constructor
        super(x, y, Cell.class);
        this.rng=rng;
    }

    public int TumorSize(int[] tumorNeighborhood) {
        //get a list of indices that fill a circle at the center of the grid
        int hoodSize=MapHood(tumorNeighborhood,xDim/2,yDim/2);
        return hoodSize;
    }

    public void ModelStep(){ //executed once per time step by each Cell
        //ShuffleAgents(rng); //shuffles agent list to randomize iterations
        for (Cell cell: this) { //iterate over all cells on the grid
            cell.StepCell();
        }
    }

    public void DrawModel(GridWindow win){
        for (int i = 0; i < length; i++) {
            Cell cellDraw = GetAgent(i);
            int color= Util.BLACK; //colours are represented as integers; black to represent no one there
            if (cellDraw!=null) {
                color= cellDraw.type; //type is in form of color
            }
            win.SetPix(i,color);
        }

    }

    public static void main(String[] args) {
        long startTime = System.nanoTime(); //setting up to measure time it takes code to run

        //setting up starting string and data collection
        //assuming tumour cell diameter of 20 microns
        //1 unit size= cell diameter; can set cell diameter to any reasonable value and redo calculations...
        //as there is one cell per grid location
        int x=100, y=100, tumorRadCm=5, msPause=5; //want tumour of radius of 5 cm
        int winScale=5;
        double scaleVal= 0.004; //scaling ratio
        //setting scale bar to be equal to the radius of the tumor: tumorRadScaled
        double tumorRadScaled= scaleVal*tumorRadCm;
        double convFactor= 500; //1 unit=sqrt(0.0004)=  0.02 mm= 20 micron= 0.002 cm, 1 cm= 500 units
        //where units is the distance between grid locations
        double tumorRad= convFactor*tumorRadScaled; //radius of tumor in terms of unit given;

        int[] tumorNeighborhood = Util.CircleHood(true, tumorRad);

        GridWindow win= new GridWindow(x,y, winScale); //each pixel will be viewed on the screen as 5*5 square pixels on the screen
        Grid model= new Grid(x,y, new Rand());

        //model.InitTumor(tumorRad);
        int neighbourHoodSize=model.TumorSize(tumorNeighborhood); //returns total number of cells of the given tumour size
        model.NewAgentSQ(model.xDim/2, model.yDim/2).type = NORMAL; //initializing model

        //main run loop
        int tick=0;//time that has passed in milliseconds
        while(true) {
            win.TickPause(msPause);
            //check to see if all cell have died, if yes, we make a new cell
            if (model.Pop()==0){
                model.NewAgentSQ(model.xDim/2, model.yDim/2).type= NORMAL;// put agent in the middle of the square; always start with normal tumour cell
            }
            model.ModelStep();
            model.DrawModel(win);

            if (model.Pop()>=neighbourHoodSize){
                long stopTime = System.nanoTime();
                long totalTime=   (stopTime - startTime);
                System.out.println(totalTime); //prints out time it takes code to run in seconds
                System.out.println(tick++);
                break;
            }
            tick=tick+1;
        }

    }


}


