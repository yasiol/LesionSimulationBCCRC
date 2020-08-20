package TumorModel_2;

//division and death dependant on oxygen levels present
//first check is death requirment is met
//die: if oxygen concentration at that cell location is less than a certain amount and 
// random number generated for that agent is less than the death probability
//divide: random number generated for that agent is less than the current oxygen concentration at that cell location:
//meaning the more oxygen it has, the more likely it is to divide
//can also survive without dividing
//5Example3D

import HAL.GridsAndAgents.AgentGrid2D;
import HAL.GridsAndAgents.AgentSQ2Dunstackable;
import HAL.GridsAndAgents.Grid2Ddouble;
import HAL.GridsAndAgents.PDEGrid2D;
import HAL.GridsAndAgents.Agent2DBase;
import HAL.Gui.GifMaker;
import HAL.Gui.GridWindow;
import HAL.Interfaces.DoubleToInt;
import HAL.Rand;
import HAL.Util;

import HAL.GridsAndAgents.*;
import static HAL.Util.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("serial")
class Cell extends AgentSQ2Dunstackable<Grid> {
    public int type; //type determines the metabolic rate of the cell
    double repProb =0.1; //10%

    void InitVessel(){
        type= G.VESSEL;
        //G.vessels.add(this);
    }
    //return false for vessle; true for cell
    boolean cellOrVessel(Cell toCheck){ 
        if (type== G.VESSEL){
            return false;

        } else {
            return true;
        }
        
    }

    boolean Death(){
        double resVal= G.oxygen.Get(Isq());
        return resVal< G.DEATH_CONC && G.rng.Double()<(1.0-resVal/ G.DEATH_CONC);
        //DEATH_CONC: largest value for concentration at which cell will die
        //resVal/G.DEATH_CONC is less than one: dependant on how much little oxygen there is 
        //current concentration of oxygen/death concentration: more likel
        //death probability= 1-resVal/G.DEATH_CONC: more likely to die if resVal/G.DEATH_CONC is lower so 1- that
    }

    boolean Divide(){
        return G.rng.Double()< G.oxygen.Get(Isq());
    }

    void Metabolism(){
        if (this.type==G.NORMAL){  //normal tumor cell with normal metabolism rate
            G.oxygen.Mul(Isq(), G.TUMOR_METABOLISM_RATE); //metabolism uses up oxygen
            //multiply current oxygen concentration amount at cell location by the metabolism rate and adds that to the delta field
            //to update the oxygen concentration
        } else if (this.type==G.VESSEL) {
            G.oxygen.Set(Isq(), 1); 
        }
           
    }

    public void StepCell(){
        //double deathProb, divProb; //division and death probabiity depend on the metabolic rate of the tumour cell

        if (this.type==G.NORMAL){ //if we have normal tumor cell
            if (Death()){
                G.countTumor= G.countTumor-1;
                Dispose();
                return;
            }

            if (Divide()){
                int options= MapEmptyHood(G.divHood);
                if (options>0){
                    //if selected position is occupied by a vessel: 
                    //10% chance that the new cell will take the place of the vessel
                    //90% chance that it will go to one of the other available locations
                    int locCell= G.divHood[G.rng.Int(options)];
                    Cell toBeReplaced= G.GetAgent(locCell);
                    boolean isCell= cellOrVessel(toBeReplaced); //false for vessel; true for cell
                    if (isCell==false && G.rng.Double()< repProb) { //location having agent is a vessel; do this only 10% of the time
                        toBeReplaced.Dispose();
                        G.NewAgentSQ(locCell).type= G.NORMAL;
                        //G.NewAgentSQ(locCell).InitVessel();
                        //G.NewAgentSQ(locCell).type= G.VESSEL;
                    } else { //location is empty or is a cell
                        G.NewAgentSQ(locCell).type= this.type; //daughter cell has same metabolic rate as parent
                        G.countTumor= G.countTumor+1;
                    }
                } 
                else {
                    int occupied= MapOccupiedHood(G.divHood);
                    int locCell= G.divHood[G.rng.Int(occupied)];
                    Cell toBeReplaced= G.GetAgent(locCell);
                    boolean isCell= cellOrVessel(toBeReplaced); //false for vessel; true for cell
                    if (isCell==false && G.rng.Double()< repProb) { //location having agent is a vessel; do this only 10% of the time
                        toBeReplaced.Dispose();
                        G.NewAgentSQ(locCell).type= G.NORMAL;
                        //G.NewAgentSQ(locCell).InitVessel();
                        //G.NewAgentSQ(locCell).type= G.VESSEL;
                    }
                    
                }
            }

  
        }

    }

}

@SuppressWarnings("serial")
public class Grid extends AgentGrid2D<Cell> {
    //model constants
    public static int countTumor;

    public final static int NORMAL= Util.RGB(0,1,0); //tumour cell with normal metabolic rate
    //Util RGB: constant serving as colors for drawing and labels for different cell types
    //public double DEATH_PROB= 0.001, DIV_PROB= 0.01; use for probabilistic modelling
    public final static int VESSEL= Util.RGB(1,0,0);
    double DEATH_CONC=0.005; //value they use is 0.01; works with 0.0099
    double TUMOR_METABOLISM_RATE =-0.000008333; //uses up oxygen: HAL: -0.04
   //double NORMAL_METABOLISM_RATE =-0.005;
    double DIFF_RATE=0.0202;
    //internal model objects
    public Rand rng; //random number generator
    public int[] divHood= MooreHood(false);
    public int[] vnHood= Util.VonNeumannHood(false);

    PDEGrid2D oxygen; //for oxygen concentration

    public Grid(int x, int y, Rand rng){ //grid constructor
        super(x, y, Cell.class);
        this.rng=rng;
        oxygen =new PDEGrid2D(x,y);//pde grid used for diffusion of oxygen
    }

    public int TumorSize(int[] tumorNeighborhood) {
        //get a list of indices that fill a circle at the center of the grid
        int hoodSize=MapHood(tumorNeighborhood,xDim/2,yDim/2);
        return hoodSize;
    }

    public void DiffStep(){
        for (Cell tumorCells : this) {
            tumorCells.Metabolism();
        }
        //oxygen.MulAll(NORMAL_METABOLISM_RATE); //this for when when we have normal cells as well
        oxygen.Diffusion(DIFF_RATE); //oxygen diffusion
        oxygen.Update(); //updates concentration of oxygen after metabolism and diffusion
        //System.out.println(oxygen.GetMin());
    }

    public void ModelStep(){ //executed once per time step by each Cell
        //ShuffleAgents(rng); //shuffles agent list to randomize iterations
        
        for (Cell cell: this) { //iterate over all cells on the grid
            cell.StepCell();
        }

        DiffStep();//oxygen 
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
    public void DrawOxygen(GridWindow winOxygen){
        // for (int x = 0; x < oxygen.xDim; x++) {
        //     for (int y = 0; y < oxygen.yDim; y++) {
        //         //winOxygen.SetPix(x,y,HeatMapRBG(Math.pow(oxygen.Get(x,y),0.5)));
        //         winOxygen.SetPix(x,y,HeatMapRBG(oxygen.Get(x,y)));
        //     }
        // }


        for (int i = 0; i < length; i++) {
            winOxygen.SetPix(i,HeatMapRBG(oxygen.Get(i)));
            
        }
    }


    // public static String newFileName() {
    //     LocalDateTime myDateObj = LocalDateTime.now();
    //     DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    //     String formattedDate = myDateObj.format(myFormatObj);
    //     String fileName= "/home/ubuntu/LesionSimulations/Videos/"+formattedDate+".gif";
    //     return fileName;
        
    // }


    public int GenVessels(double vesselSpacingMin,double migProb){
        //create a Grid to store the locations that are too close for placing another vessel
        Grid2Ddouble openSpots=new Grid2Ddouble(xDim,yDim); //planar grid; no wraparound
        //create a neighborhood that defines all indices that are too close
        int[]vesselSpacingHood=CircleHood(false,vesselSpacingMin); //radius of circular neighborhood is vesselSpacingMin
        int[]indicesToTry=GenIndicesArray(openSpots.length); //array of indecides
        rng.Shuffle(indicesToTry);
        int vesselCt=0; //number of vessels
        for (int i : indicesToTry) {
            if(openSpots.Get(i)==0){
                int x=openSpots.ItoX(i);
                int y=openSpots.ItoY(i);
                GenVessel(x,migProb);
                //migProb gives the probability that the vessel wont be placed 
                //directly on top of the previous x, y vessel placement position
                vesselCt++;
                int nSpots=openSpots.MapHood(vesselSpacingHood,x,y);
                for (int j = 0; j < nSpots; j++) {
                    //mark spot as too close for another vessel
                    openSpots.Set(vesselSpacingHood[j],-1);
                }
            }
        }
        return vesselCt;
    }

    public void GenVessel(int x,double migProb) {
        for (int y = 0; y < yDim; y++) {
            //clear out any agents that are in the path of the vessel
            if (rng.Double() < migProb) {
                int openCt = MapHood(vnHood, x, y); //number of valid locations in the generated enighborhood
                //von neuman neighbouhood centered at the current x and y location
                int i = vnHood[rng.Int(openCt)]; //random index in the generated neighborhood from 0 up to the number of valid locations
                x=ItoX(i); //x dimension of the square at index i
            }
            Cell occupant = GetAgent(x, y);
            if (occupant != null) {
                occupant.Dispose();
            }
            NewAgentSQ(x, y).InitVessel();
        }
    }

    public static void main(String[] args) {
        System.out.println("working");
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
        GridWindow winOxygen= new GridWindow(x,y, winScale); //each pixel will be viewed on the screen as 5*5 square pixels on the screen
        Grid model= new Grid(x,y, new Rand());
        //String savedFile=newFileName();
        //GifMaker saveGif= new GifMaker(savedFile, msPause, false);

        //model.InitTumor(tumorRad);
        int neighbourHoodSize=model.TumorSize(tumorNeighborhood); //returns total number of cells of the given tumour size
        model.NewAgentSQ(model.xDim/2, model.yDim/2).type = NORMAL; //initializing model

        //generating blood vessels
        model.GenVessels(15,0.8); //minimum vessel spacing, migration probability

        //Diffuse to steady state
        for (int i = 0; i < 100; i++) {
            model.DiffStep();
        }

        //main run loop
        int tick=0;//time that has passed in milliseconds 
        //int previousPop=0;
        //int currentPop;
        System.out.println("Initialization Complete");

        countTumor=1; //from initializing model
        while(true) {
            win.TickPause(msPause);
            //check to see if all cell have died, if yes, we make a new cell
            if (countTumor==0){
                Cell occupantMid = model.GetAgent(model.xDim/2, model.yDim/2);
                if (occupantMid != null) {
                    occupantMid.Dispose();
                }
                model.NewAgentSQ(model.xDim/2, model.yDim/2).type= NORMAL;// put agent in the middle of the square; always start with normal tumour cell
                countTumor=1;
            }
            model.ModelStep();
            //model.DrawModel(win);
            //model.DrawOxygen(winOxygen);


            //save frame only when number of agents changes
            //currentPop=model.Pop();
            //if (model.Pop()!=previousPop){
            //    saveGif.AddFrame(win);
            //}
            //previousPop=currentPop;
            // if (model.Pop()!=0){
            //     System.out.println(model.Pop());
            // }

            System.out.println(countTumor);

            model.CleanAgents();//Equivalent to calling CleanAgents, ShuffleAgents, and IncTick grid functions
            model.ShuffleAgents(model.rng);

            

            if (countTumor>=neighbourHoodSize){ //model.Pop() instead of countTumor if no blood vessels
                long stopTime = System.nanoTime();
                long totalTime=   (stopTime - startTime);
                System.out.println("Complete"); 
                //final state
                model.DrawModel(win);
                model.DrawOxygen(winOxygen);

                System.out.println(totalTime); //prints out time it takes code to run in seconds
                System.out.println(tick);
                System.out.println("Done");
                //saveGif.Close();
                break;
            }
            tick=tick+1;


            if (win.IsClosed() || winOxygen.IsClosed()) {
                win.Close();
                winOxygen.Close();
            }
        }

    }


}




