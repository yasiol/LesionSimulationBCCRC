package TumorModel_4;

//models: normal, hypoxic and necrotic tumor cells

import HAL.GridsAndAgents.AgentGrid2D;
import HAL.GridsAndAgents.AgentSQ2Dunstackable;
import HAL.GridsAndAgents.Grid2Ddouble;
import HAL.GridsAndAgents.PDEGrid2D;
import HAL.Gui.GridWindow;
import HAL.Interfaces.SerializableModel;
import HAL.Rand;
import HAL.Util;
import static HAL.Util.*;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

@SuppressWarnings("serial")
class Cell extends AgentSQ2Dunstackable<Grid> {
    public int type; //type determines the metabolic rate of the cell

    void InitVessel(){
        type= G.VESSEL;
    }

    //return false for vessel; true for cell or empty
    boolean cellOrVessel(Cell toCheck){ 
        if (toCheck==null){
            //System.out.println("cellOrVessel: daughter cell location is empty");
            return true;
        } else if (toCheck.type== G.VESSEL) {
            //System.out.println("cellOrVessel: daughter cell location is occupied by a vessel");
            return false;
        } else {
            //System.out.println("cellOrVessel: daughter cell location is occupied by another cell");
            return true;

        }
        
    }


    //return false for occupied; true for empty
    boolean occupiedOrEmpty(Cell toCheck){ 
        if (toCheck==null){
            return true;
        } else {
            return false;
        } 
        
    }

    boolean Death(){
        double resVal= G.oxygen.Get(Isq());
        double death_prob= ProbScale(1.0-resVal/ G.DEATH_CONC, G.TIMESTEP);
        
        if (resVal<= G.DEATH_CONC) {
            if (G.rng.Double()<death_prob) {
                // System.out.println("death_prob");
                // System.out.println(death_prob);
                return true;
            }
            else {
                return false;
            }
        } else {
            return false;
        }
        //return ( (resVal<= G.DEATH_CONC) && (G.rng.Double()<death_prob) ); 

    }

    boolean Divide(){
        double resVal= G.oxygen.Get(Isq()); //effectively the probability
        double prob= resVal/G.BLOOD_VESSEL_OXYGEN;
        double divide_prob= ProbScale(prob,G.TIMESTEP);
        // System.out.println("divide_prob");
        // System.out.println(divide_prob);
        return ( (resVal> G.DEATH_CONC) && (G.rng.Double()< divide_prob) ); 
    }

    boolean Hypoxia(){
        double resVal= G.oxygen.Get(Isq());
        if ( (resVal<=G.HYPOXIA_CONC) && (resVal>=G.DEATH_CONC) ) {
            return true;
        } else {
            return false;
        }
    }

    void Metabolism(){
        if (this.type==G.NORMAL){  //normal tumor cell with normal metabolism rate
            G.oxygen.Add(Isq(), G.TUMOR_METABOLISM_RATE); //metabolism uses up oxygen
            //multiply current oxygen concentration amount at cell location by the metabolism rate and adds that to the delta field
            //to update the oxygen concentration
        } else if (this.type==G.VESSEL) {
            G.oxygen.Set(Isq(), G.BLOOD_VESSEL_OXYGEN); //oxygen concentration set to maximum, at 1
        } else if (this.type==G.HYP) {
            G.oxygen.Add(Isq(), G.HYP_METABOLISM_RATE);
        }
           
    }

    public void StepCell(){
        double moveProbVessel= 0.50;

        //ensures there is a cell, ie the model is seeded
        // if (G.countTumor==0){
        //     System.out.println("No Tumor Cell; Model Ended");
        //     Cell occupantMid = G.GetAgent(G.xDim/2, G.yDim/2);
        //     if (occupantMid != null) {
        //         occupantMid.Dispose();
        //     }
        //     G.NewAgentSQ(G.xDim/2, G.yDim/2).type= G.NORMAL;// put agent in the middle of the square; always start with normal tumour cell
        //     G.countTumor=1;
        //     System.out.println("New Seeded");
        // }

        int currentCellType= this.type;

        if ((currentCellType==G.NORMAL) || (currentCellType==G.HYP) ){ //if we have normal/hypoxia tumor cell
            //System.out.println(G.oxygen.GetMax());
            //System.out.println(G.oxygen.Get(Isq()));
            if (Death()){ //normal or hypoxic tumor cell becomes nercotic
                //G.countTumor= G.countTumor-1;
                // Dispose();
                // System.out.println("cell died");
                // Cell toDie= G.GetAgent(Isq());
                // toDie.type=G.NERCOTIC;
                this.type=G.NERCOTIC;
                //System.out.println("nercotic");
                return;
            }

            if (Hypoxia()){ //normal tumor cell becomes hypoxic
                this.type=G.HYP;
                //System.out.println("hypoxia");
                //return;

            }

            if (Divide()){ //normal or hypoxic tumor cell divides; daughter cell has same type as parent cell
                int options= MapEmptyHood(G.divHood);
                if (options>0){
                    int locCell= G.divHood[G.rng.Int(options)];
                    Cell toBeReplaced= G.GetAgent(locCell);
                    boolean isCell= cellOrVessel(toBeReplaced); //false for vessel; true for cell or empty
                    if (isCell==false) { //location occupied by vessel; vessel is replaced by daughter cell
                        int optionsVessel= MapEmptyHood(G.divHood); //divHood is a moore neighbourhood
                        if (optionsVessel>0 && G.rng.Double()< moveProbVessel) { //if there are empty locations around the vessel; one is randomly selectiod and the vessel is moved to that location
                            int position;
                            while (true){
                                int movePosition= G.divHood[G.rng.Int(optionsVessel)];
                                Cell moveVesselLocationAgent= G.GetAgent(movePosition);
                                boolean identity= occupiedOrEmpty(moveVesselLocationAgent); //false for vessel; true for cell or empty
                                if (identity==true){ //location is empty
                                    position=movePosition;
                                    break;
                                }

                            }
                            toBeReplaced.MoveSQ(position);

                        } else { //if there are no empty locations around the vessel, the vessel is killed
                            toBeReplaced.Dispose(); //vessel can be disposed 
                        }
                        G.NewAgentSQ(locCell).type= currentCellType;
                        G.countTumor= G.countTumor+1;
                        //toBeReplaced.Dispose();
                        //G.NewAgentSQ(locCell).type= currentCellType;
                        //System.out.println("vessel replaced by cell");
                    } else if (toBeReplaced==null){ //location is empty; daughter cell created in new location
                        G.NewAgentSQ(locCell).type= currentCellType; //daughter cell has same metabolic rate as parent
                        //System.out.println("daughter cell");
                        G.countTumor= G.countTumor+1;
                    } else { //location is occupied by another cell; nothing is done
                        //System.out.println("nothing done; there were unoccupied locations; selected location was occupied by cell");
                        return;
                    }
                } 
                else {
                    int occupied= MapOccupiedHood(G.divHood);
                    int locCell= G.divHood[G.rng.Int(occupied)];
                    Cell toBeReplaced= G.GetAgent(locCell);
                    boolean isCell= cellOrVessel(toBeReplaced); //false for vessel; true for cell
                    if (isCell==false) { //location occupied by vessel; vessel is replaced by daughter cell
                        int optionsVessel= MapEmptyHood(G.divHood); //divHood is a moore neighbourhood
                        if (optionsVessel>0 && G.rng.Double()< moveProbVessel) { //if there are empty locations around the vessel; one is randomly selectiod and the vessel is moved to that location
                            int position;
                            while (true){
                                int movePosition= G.divHood[G.rng.Int(optionsVessel)];
                                Cell moveVesselLocationAgent= G.GetAgent(movePosition);
                                boolean identity= occupiedOrEmpty(moveVesselLocationAgent); //false for vessel; true for cell or empty
                                if (identity==true){ //location is empty
                                    position=movePosition;
                                    break;
                                }

                            }
                            toBeReplaced.MoveSQ(position);

                        } else { //if there are no empty locations around the vessel, the vessel is killed
                            toBeReplaced.Dispose(); //vessel can be disposed 
                        }
                        G.NewAgentSQ(locCell).type= currentCellType;
                        G.countTumor= G.countTumor+1;
                        //System.out.println("vessel replaced by cell");
                    } else { //location is occupied by another cell; nothing is done
                        //System.out.println("nothing done; all locations around occupied; seleced location was occupied by cells");
                        return;

                    }
                    
                }

                
                //all vessels in the divHood neighbourhood are also destoroyed when new daughter cell is created
                //this part of code isn't reached is randommly selected location for daughter cell is already occupied by another cell
                // int NumNeighbours= MapHood(G.divHood);
                // for (int i = 0; i < NumNeighbours; i++) {
                //     //System.out.println("loop for cutting off blood vessel "+ NumNeighbours );
                //     int vesselDieLoc= G.divHood[i];
                //     Cell vesselToDie= G.GetAgent(vesselDieLoc);
                //     boolean isCell= cellOrVessel(vesselToDie); //false for vessel; true for cell
                //     //System.out.println(isCell);
                //     if (isCell==false) {
                //         vesselToDie.Dispose();
                //         //System.out.println("killed vessel");
                //     }

                // }
                //end commentded section

                
            }  

        }

    }

}

@SuppressWarnings("serial")
public class Grid extends AgentGrid2D<Cell> implements SerializableModel{

    static byte[] state; //for saving model
    static String saveFolder = "/home/ubuntu/LesionSimulations/Saved_Models/";

    //tumor cell types
    //Util RGB: constant serving as colors for drawing and labels for different cell types
    public final static int NORMAL= Util.RGB(0,1,0); //tumour cell with normal metabolic rate
    public final static int NERCOTIC= Util.RGB(0.8,0.8,0.8);
    public final static int HYP= Util.RGB(0,0,1);
    public final static int VESSEL= Util.RGB(1,0,0);

    //model constants:
    int countTumor;
    public double TIMESTEP = 1.0 / 24.0;//1 hours per timestep; unit is days
    public double TIMESTEP_DIFFUSION= 0.03086; //0.03086 seconds for 10 micron diffusion distance; 0.06049 s for 14 micron diffusion distance; 
    //0.05216 s for 13 microns diffusion distance; 0.04444 s for 12 microns diffusion distance
    // 0.03735 s for 11 microns diffusion distance
    public double SPACE_STEP = 20.0;//um
    
    //metabolism and cell state constants
    double TUMOR_METABOLISM_RATE = -4.01*Math.pow(10,-15)*(TIMESTEP_DIFFUSION*60.0);  // oxygen consumption rate; units is mol/min/cell; before random value was-0.8
    //double TUMOR_METABOLISM_RATE2= 300* Math.pow(10,12)* 16 / 1.43 * 0.001*24 *60* TIMESTEP / (SPACE_STEP * SPACE_STEP*SPACE_STEP)* Math.pow(10,12);
    double HYP_METABOLISM_RATE= -2.005*Math.pow(10,-15)*(TIMESTEP_DIFFUSION*60.0); //oxygen consumption rate; units is mol/min/cell
    double BACKGROUND_METABOLISM_RATE= -2.5*Math.pow(10,-18)*(TIMESTEP_DIFFUSION*60.0); //oxygen consumption rate; units is mol/min/cell
    double BLOOD_VESSEL_OXYGEN= 2.81*Math.pow(10,-12)*(TIMESTEP_DIFFUSION*60.0); //amount of oxygen provided to the cells from the blood vessles in mol/min/20 microns segment
    double DEATH_CONC=0.005*BLOOD_VESSEL_OXYGEN; 
    double HYPOXIA_CONC= 0.03*BLOOD_VESSEL_OXYGEN;
    

    //diffusion constants
    double DIF_RATE_VAL= 1.62*Math.pow(10,-5); //in cm^2/sec: bioloogically accurate:  1.38*Math.pow(10,-11)
    double DIFF_RATE=DIF_RATE_VAL * TIMESTEP_DIFFUSION / (SPACE_STEP * SPACE_STEP)* Math.pow(10,8); //oxygen diffusion rate: 0.0202
    int MAX_OXYGEN_STEPS=(int)  ((1.0/TIMESTEP_DIFFUSION)*3600*TIMESTEP); // number of steps for oxygen diffusion loop: calcualted based on the timestep_diffusion and timestep values; NOT RANDOMLY CHOSEN
    int VESSEL_INITIALIZATION_STEPS= (int)  ((1.0/TIMESTEP_DIFFUSION)*3600); // number of steps for oxygen diffusion loop: want diffusion to occur for 1 hour
    //double OXYGEN_DELTA_THRESHOLD=; //maximum delta for updating oxygen PDE Grid

    //internal model objects
    public Rand rng; //random number generator
    public int[] divHood= MooreHood(false);
    public int[] vnHood= Util.VonNeumannHood(false);
    public int[] killVessel= MooreHood(false);
    PDEGrid2D oxygen; //PDE Grid for oxygen concentration

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
        for (int i=0; i<100; i++){ //time step here is 0.03086: MAX_OXYGEN_STEPS
            //long startTime = System.nanoTime();
            

            for (Cell tumorCells : this) { // tumor cell metabolism and vessel oxygen value setting
                tumorCells.Metabolism();
            }

            //background normal cell/tissue metabolism
            for (int j = 0; j < length; j++) {
                Cell occupant = GetAgent(j);
                if (occupant == null) {
                    oxygen.Add(j, BACKGROUND_METABOLISM_RATE);
                }
                
            }

            //diffusion
            //oxygen.DiffusionNew(DIFF_RATE); //oxygen diffusion; similar to oxygen.Diffusion(DIFF_RATE) without the 0.25 diffCoef threshold
            oxygen.Diffusion(DIFF_RATE); 
            // if (oxygen.MaxDelta()>=OXYGEN_DELTA_THRESHOLD) {
            //     oxygen.Update(); //updates concentration of oxygen after metabolism
            //     break;
            // }
            oxygen.Update();
            //oxygen.Diffusion(diffRatesX, diffRatesY);
            //long stopTime = System.nanoTime();
            //long totalTime=   (stopTime - startTime);
            //System.out.println("Time for single step of diffusion and metabolism"+ totalTime);
        }
    }

    public void initializeVesselOxygen() {

        for (Cell tumorCells : this) { //metabolism
            tumorCells.Metabolism();
        }

        //diffusion
        //oxygen.DiffusionNew(DIFF_RATE); //oxygen diffusion; similar to oxygen.Diffusion(DIFF_RATE) without the 0.25 diffCoef threshold
        oxygen.Diffusion(DIFF_RATE); 
        oxygen.Update();
        //oxygen.Diffusion(diffRatesX, diffRatesY);
    }

    public void ModelStep(){ //executed once per time step by each Cell
        //ShuffleAgents(rng); //shuffles agent list to randomize iterations
        for (Cell cell: this) { //iterate over all cells on the grid
            if (cell.type!= VESSEL) {
                cell.StepCell();
            }
        }

        DiffStep();//cell metabolism and oxygen diffusion

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
        for (int i = 0; i < length; i++) {
            winOxygen.SetPix(i,HeatMapRBG(oxygen.Get(i)/BLOOD_VESSEL_OXYGEN)); //used to be HeatMapRBG
            
        }
    }


    public static String newDirectory() {
        
        System.out.println("Enter the name of the desired folder for saving model files: ");
        Scanner sc = new Scanner(System.in);
        String completePath = saveFolder+ sc.next();
        //Creating a File object
        File file = new File(completePath);
        //Creating the directory
        boolean bool = file.mkdir();
        sc.close();
        if(bool){
            System.out.println("Directory created successfully in "+ saveFolder);
            System.out.println("Complete path is "+ completePath);
        }else{
            System.out.println("Sorry couldn’t create specified directory");
        }

        return completePath;

        
    }

    public static String newFileName(String path) {
        LocalDateTime myDateObj = LocalDateTime.now();
        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        String formattedDate = myDateObj.format(myFormatObj);
        String fileName= path+"/"+formattedDate;
        return fileName;
        
    }


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
        System.out.println("Start");
        long startTime = System.nanoTime(); //setting up to measure time it takes code to run
        

        //assuming tumour cell diameter of 20 microns
        //1 unit size= cell diameter; can set cell diameter to any reasonable value and redo calculations...
        //as there is one cell per grid location
        int x=2000, y=2000, msPause=5;
        double tumorRadCm= 0.05; //use 0.25 to see if model works; radius of tumor in cm: 2 cm is too big: corresponds to 3 million tumor cells
        //to test saving use radius of 0.10
        int winScale=1; //used to be 5
        double convFactor= 500; //1 unit=sqrt(0.0004)=  0.02 mm= 20 micron= 0.002 cm, 1 cm= 500 units
        //where units is the distance between grid locations
        //double tumorRad= convFactor*tumorRadScaled; //radius of tumor in terms of unit given;
        double tumorRad= convFactor*tumorRadCm;

        int[] tumorNeighborhood = Util.CircleHood(true, tumorRad);


        GridWindow win= new GridWindow("Tumor Growth Model",x,y, winScale); //each pixel will be viewed on the screen as 5*5 square pixels on the screen
        GridWindow winOxygen= new GridWindow("Tumor Growth Model Oxygen Concentration",x,y, winScale); //each pixel will be viewed on the screen as 5*5 square pixels on the screen
        Grid model= new Grid(x,y, new Rand());
        //String savedFile=newFileName();
        //GifMaker saveGif= new GifMaker(savedFile, msPause, false);

        //generating blood vessels
        model.GenVessels(100,0.8); //minimum vessel spacing (100) , migration probability (0.8)
        //Diffuse to steady state
        System.out.println("Initializing Blood Vessels");
        for (int i = 0; i < 10; i++) { //model.VESSEL_INITIALIZATION_STEPS
            //model.DiffStep();
            model.initializeVesselOxygen();
            if (i % 1000==0){
                System.out.println(i);
                model.DrawModel(win);
                model.DrawOxygen(winOxygen);
            }
        }

        System.out.println("Blood Vessel Initialization Complete");
        model.DrawModel(win);
        model.DrawOxygen(winOxygen);


        //model.InitTumor(tumorRad);
        int neighbourHoodSize=model.TumorSize(tumorNeighborhood); //returns total number of cells of the given tumour size
        Cell occupantMid = model.GetAgent(model.xDim/2, model.yDim/2);
        if (occupantMid != null) {
            occupantMid.Dispose();
        }
        model.NewAgentSQ(model.xDim/2, model.yDim/2).type = NORMAL; //initializing model
        model.countTumor=1; //from initializing model

        //main run loop
        int tick=0;//time that has passed in milliseconds 
        int previousPop=model.countTumor;
        int currentPop;
        int countNoGrow=0;
        System.out.println("Cell Initialization Complete");
        System.out.println("Number of Tumor Cells Needed: "+ neighbourHoodSize);
        model.DrawModel(win);
        model.DrawOxygen(winOxygen);

        String filePath= newDirectory();


        while(true) { 
            //win.TickPause(msPause);
            //check to see if all cell have died, if yes, we make a new cell
            
            //for saving model: refer to manual for better understanding of what's happening: ONLY THESE TWO LINES
            String modelFile= newFileName(filePath);
            SaveState(model, modelFile); //saves model in file
            
            if (model.countTumor<=0){
                System.out.println("No Tumor Cell; Model Ended");
                break;
                // Cell occupantMid = model.GetAgent(model.xDim/2, model.yDim/2);
                // if (occupantMid != null) {
                //     occupantMid.Dispose();
                // }
                // model.NewAgentSQ(model.xDim/2, model.yDim/2).type= model.NORMAL;// put agent in the middle of the square; always start with normal tumour cell
                // model.countTumor=1;
                // System.out.println("New Seeded");
            }

            currentPop= model.countTumor;
            if (currentPop==previousPop){
                System.out.println("Previous Pop: "+ previousPop+" Current Pop: "+currentPop);
                countNoGrow++;
                if (countNoGrow>=40) {
                    System.out.println("Tumor Has Stopped Growing Before Reaching Desired Size."); 
                    int diff=  neighbourHoodSize-currentPop;
                    System.out.println("Number of Desired Tumor Cells: "+ neighbourHoodSize +" With a Difference of "+ diff+ " cells. ");
                    long stopTime = System.nanoTime();
                    long totalTime=   (stopTime - startTime);
                    //final state
                    model.DrawModel(win);
                    model.DrawOxygen(winOxygen);

                    System.out.println("Time for Code to Run in Nanosecond: "+ totalTime); //prints out time it takes code to run in seconds
                    System.out.println("Number of Steps: "+ tick);
                    System.out.println("Done");
                    break;
                }
            }
            previousPop=currentPop;

            model.ModelStep();
            // model.DrawModel(win);
            // model.DrawOxygen(winOxygen);


            //save frame only when number of agents changes: this is for saving gif
            //currentPop=model.Pop();
            //if (model.Pop()!=previousPop){
            //    saveGif.AddFrame(win);
            //}
            //previousPop=currentPop;
            // if (model.Pop()!=0){
            //     System.out.println(model.Pop());
            // }

            //System.out.println(model.countTumor);


            // model.CleanAgents();//Equivalent to calling CleanAgents, ShuffleAgents, and IncTick grid functions
            // model.ShuffleAgents(model.rng);

            if (model.countTumor>=neighbourHoodSize){ //if (model.countTumor>=neighbourHoodSize) for stopping based on tumor size or (tick>=numSteps) for stopping based on number of time steps
                //model.Pop() instead of countTumor if no blood vessels
                long stopTime = System.nanoTime();
                long totalTime=   (stopTime - startTime);
                System.out.println("Complete"); 
                System.out.println("Number of Tumor Cells "+ model.countTumor);
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

            if (tick % 100==0){
                //System.out.println(model.countTumor);
                model.DrawModel(win);
                model.DrawOxygen(winOxygen);
            }

            if (tick % 10==0){
                System.out.println(model.countTumor);  
            }

            //System.out.println(model.countTumor);

            if (win.IsClosed() || winOxygen.IsClosed()) {
                win.Close();
                winOxygen.Close();
                System.out.println("Windows Closed");
                break;
            }
        }

        if (win.IsClosed() || winOxygen.IsClosed()) {
            win.Close();
            winOxygen.Close();
            System.out.println("Windows Closed");
        }

        

    }

    @Override
    public void SetupConstructors() {
        // TODO Auto-generated method stub
        _PassAgentConstructor(Cell.class);

    }


}




