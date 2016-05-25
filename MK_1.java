package sim.app.geo.MK_1;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.geo.GeomVectorField;
import sim.io.geo.ShapeFileImporter;
import sim.util.geo.GeomPlanarGraph;
import sim.util.geo.GeomPlanarGraphEdge;
import sim.util.geo.MasonGeometry;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.planargraph.Node;

/**
 * 
 * A simple model that locates agents on Norfolk's road network and makes them
 * move from A to B, then they change direction and head back to the start.
 * The process repeats until the user quits. The number of agents, their start
 * and end points is determined by data in NorfolkITNLSOA.csv and assigned by the
 * user under 'goals' (approx. Line 84).
 * 
 * @author KJGarbutt
 *
 */
public class MK_1 extends SimState	{
	
	////////////////////////////////////////////////////////////////
	///////////////////// MODEL PARAMETERS /////////////////////////
	////////////////////////////////////////////////////////////////
    
	private static final long serialVersionUID = -4554882816749973618L;

	//////////////////// Containers //////////////////////////////
    public GeomVectorField roads = new GeomVectorField();
    public GeomVectorField lsoa = new GeomVectorField();
    public GeomVectorField flood3 = new GeomVectorField();
    public GeomVectorField flood2 = new GeomVectorField();
    public GeomVectorField agents = new GeomVectorField();
    public GeomVectorField ngoagents = new GeomVectorField();
    public GeomVectorField elderlyagents = new GeomVectorField();
    public GeomVectorField limitedactionsagents = new GeomVectorField();

    ////////////////////// Network ///////////////////////////////
    public GeomPlanarGraph network = new GeomPlanarGraph();	// Stores road network connections
    public GeomVectorField junctions = new GeomVectorField();	// nodes for intersections

    ///////////////////// MainAgent //////////////////////////////
    // maps between unique edge IDs and edge structures themselves
    HashMap<Integer, GeomPlanarGraphEdge> idsToEdges =
        new HashMap<Integer, GeomPlanarGraphEdge>();
    HashMap<GeomPlanarGraphEdge, ArrayList<MainAgent>> edgeTraffic =
        new HashMap<GeomPlanarGraphEdge, ArrayList<MainAgent>>();
    
    public GeomVectorField mainagents = new GeomVectorField();
    ArrayList<MainAgent> agentList = new ArrayList<MainAgent>();
    
    // Here we force the agents to go to or from work at any time
    boolean goToWork = true;
    public boolean getGoToWork()	{
        return goToWork;
    }
    
    ///////////////////// NGOAgent //////////////////////////////
    HashMap<Integer, GeomPlanarGraphEdge> idsToEdges1 =
            new HashMap<Integer, GeomPlanarGraphEdge>();
    HashMap<GeomPlanarGraphEdge, ArrayList<NGOAgent>> edgeTraffic1 =
            new HashMap<GeomPlanarGraphEdge, ArrayList<NGOAgent>>();
    
    public GeomVectorField ngogents = new GeomVectorField();
    ArrayList<NGOAgent> ngoAgentList = new ArrayList<NGOAgent>();

    boolean goToWork1 = true;    
    public boolean getGoToWork1()	{
        return goToWork1;
    }
    
    //////////////////// ElderlyAgent ///////////////////////////
    HashMap<Integer, GeomPlanarGraphEdge> idsToEdges2 =
            new HashMap<Integer, GeomPlanarGraphEdge>();
    HashMap<GeomPlanarGraphEdge, ArrayList<ElderlyAgent>> edgeTraffic2 =
            new HashMap<GeomPlanarGraphEdge, ArrayList<ElderlyAgent>>();
    
    public GeomVectorField elderlyagent = new GeomVectorField();
    ArrayList<ElderlyAgent> elderlyAgentList = new ArrayList<ElderlyAgent>();
    
    boolean goToWork2 = true;    
    public boolean getGoToWork2()	{
        return goToWork2;
    }
    
    //////////////////// LimitedActionsAgent //////////////////////
    HashMap<Integer, GeomPlanarGraphEdge> idsToEdges3 =
            new HashMap<Integer, GeomPlanarGraphEdge>();
    HashMap<GeomPlanarGraphEdge, ArrayList<LimitedActionsAgent>> edgeTraffic3 =
            new HashMap<GeomPlanarGraphEdge, ArrayList<LimitedActionsAgent>>();
    
    public GeomVectorField limitedactionsagent = new GeomVectorField();
    ArrayList<LimitedActionsAgent> limitedActionsAgentList = new ArrayList<LimitedActionsAgent>();

    boolean goToWork3 = true;    
    public boolean getGoToWork3()	{
        return goToWork3;
    }
    
    /**
     * Here we set the 'goals', or destinations, which relate to ROAD_ID in NorfolkITNLSOA.csv/.shp...
     * 30250 = Norfolk & Norwich Hospital
     * 74858 = James Paget Hospital (Does not work!)
     * 18081 = Queen Elizabeth Hospital
     * 46728 = BRC Norwich Office
     * 49307 = Sprowston Fire Station - BRC Fire & Emergency Support
     * 
     */
    
    Integer[] goals =	{	// MainAgent
    		// 78277, 82124 // doesn't work
    		30250
    };

    Integer[] goals1 =	{	// NGOAgent
    		30250
    };
    
    Integer[] goals2 =	{	// ElderlyAgent
    		30250
    };
    
    Integer[] goals3 =	{	// LimitedActionsAgent
    		30250
    };
    
    ///////////////////////////////////////////////////////////////////////////
	/////////////////////////// BEGIN FUNCTIONS ///////////////////////////////
	///////////////////////////////////////////////////////////////////////////	
    
    
    /**
     * Model Constructor
     */
    public MK_1(long seed)	{
        super(seed);
    }

    
    /**
     * Model Initialization
     */
    @Override
    public void start() {
        super.start();
        System.out.println("Reading shapefiles...");

		//////////////////////////////////////////////
		///////////// READING IN DATA ////////////////
		//////////////////////////////////////////////
       
        /*
        src/
        data/
            .shp, .dbf, .csv ...
        sim/
        	app/
        		geo/
        			MK_1/
        				.java files
         */
        
        try {
            // read in the roads shapefile to create the transit network
        	URL roadsFile = MK_1.class.getResource
        			("/data/NorfolkITN.shp");
            ShapeFileImporter.read(roadsFile, roads);
            System.out.println("	Roads shapefile: " +roadsFile);
            
            Envelope MBR = roads.getMBR();

            // read in the LSOA shapefile to create the background        
            URL areasFile = MK_1.class.getResource
            		("/data/NorfolkLSOA.shp");
            ShapeFileImporter.read(areasFile, lsoa);
            System.out.println("	LSOA shapefile: " +areasFile);

            MBR.expandToInclude(lsoa.getMBR());

            // read in the FZ3 file     
            URL flood3File = MK_1.class.getResource
            		("/data/NorfolkFZ3.shp");
            ShapeFileImporter.read(flood3File, flood3);
            System.out.println("	FZ3 shapefile: " +flood3File);
            
            MBR.expandToInclude(flood3.getMBR());
            
            // read in the FZ2 file     
            URL flood2File = MK_1.class.getResource
            		("/data/NorfolkFZ2.shp");
            ShapeFileImporter.read(flood2File, flood2);
            System.out.println("	FZ2 shapefile: " +flood2File);
            System.out.println();
            
            MBR.expandToInclude(flood2.getMBR());
            
            createNetwork();

            //////////////////////////////////////////////
            ////////////////// CLEANUP ///////////////////
            //////////////////////////////////////////////

            // clear any existing agents from previous runs
            agents.clear();
            ngoagents.clear();
            elderlyagents.clear();
            limitedactionsagents.clear();
            
            //////////////////////////////////////////////
            ////////////////// AGENTS ////////////////////
            //////////////////////////////////////////////
            
            // initialize agents
            populateAgent("/data/NorfolkITNAGENT.csv");
            populateNGO("/data/NorfolkITNNGO.csv");
            populateElderly("/data/NorfolkITNELDERLY.csv");
            populateLimitedActions("/data/NorfolkITNLIMITED.csv");
            System.out.println();
            System.out.println("Starting simulation...");
            
            // standardize the MBRs so that the visualization lines up
            // and everyone knows what the standard MBR is
            roads.setMBR(MBR);
            lsoa.setMBR(MBR);
            flood3.setMBR(MBR);
            flood2.setMBR(MBR);
            agents.setMBR(MBR);
            ngoagents.setMBR(MBR);
            elderlyagents.setMBR(MBR);
            limitedactionsagents.setMBR(MBR);

            // Ensure that the spatial index is updated after all the agents move
            schedule.scheduleRepeating( agents.scheduleSpatialIndexUpdater(),
            		Integer.MAX_VALUE, 1.0);
            schedule.scheduleRepeating( ngoagents.scheduleSpatialIndexUpdater(),
            		Integer.MAX_VALUE, 1.0);
            schedule.scheduleRepeating( elderlyagents.scheduleSpatialIndexUpdater(),
            		Integer.MAX_VALUE, 1.0);
            schedule.scheduleRepeating( limitedactionsagents.scheduleSpatialIndexUpdater(),
            		Integer.MAX_VALUE, 1.0);

            /**
             * Steppable that flips Agent paths once everyone reaches their destinations
             */
            Steppable flipper = new Steppable()	{
				private static final long serialVersionUID = 1L;

				@Override
                public void step(SimState state)	{

					MK_1 gstate = (MK_1) state;

					// checks to see if anyone has not yet reached destination
                    for (MainAgent a : gstate.agentList)	{
                        if (!a.reachedDestination)	{
                            return; // someone is still moving: let them do so
                        }
                    }
                    
                    for (NGOAgent b : gstate.ngoAgentList)	{
                        if (!b.reachedDestination)	{
                            return;
                        }
                    }
                    
                    for (ElderlyAgent c : gstate.elderlyAgentList)	{
                        if (!c.reachedDestination)	{
                            return;
                        }
                    }
                    
                    for (LimitedActionsAgent d : gstate.limitedActionsAgentList)	{
                        if (!d.reachedDestination)	{
                            return;
                        }
                    }
                    
                    // Now send everyone back in the opposite direction
                    boolean toWork = gstate.goToWork; 
                    gstate.goToWork = !toWork;

                    boolean toWork1 = gstate.goToWork1;
                    gstate.goToWork1 = !toWork1;
                    
                    boolean toWork2 = gstate.goToWork2;
                    gstate.goToWork2 = !toWork2;
                    
                    boolean toWork3 = gstate.goToWork3;
                    gstate.goToWork3 = !toWork3;
                    
                    // otherwise everyone has reached their latest destination:
                    // turn them back
                    // turning off means agents reach first destination and stay there.
                    for (MainAgent a : gstate.agentList) 	{
                        a.flipPath();
                    }                    

                    for (NGOAgent b : gstate.ngoAgentList)	{
                        b.flipPath();
                    }                   

                    for (ElderlyAgent c : gstate.elderlyAgentList)	{
                        c.flipPath();
                    }
                    
                    for (LimitedActionsAgent d : gstate.limitedActionsAgentList)	{
                        d.flipPath();
                    }
                }
            };
            schedule.scheduleRepeating(flipper, 10);
            // 10? Does it repeat x10? Appears to go on forever...

        } catch (FileNotFoundException e)	{
            System.out.println("Error: missing required data file");
        }
    }

    
    /**
	 * Finish the simulation and clean up
	 */
    public void finish()	{
    	super.finish();
    	System.out.println();
    	System.out.println("Simulation ended by user.");
        /*
    	System.out.println("Attempting to export agent data...");
        try	{
        	ShapeFileExporter.write("agents", agents);
        } catch (Exception e)	{
        	System.out.println("Export failed.");
        	e.printStackTrace();
        }
        */
    }
    
    
    /**
     * Create the road network the agents will traverse
     */
    private void createNetwork()	{
    	System.out.println("Creating road network...");
    	System.out.println();
    	network.createFromGeomField(roads);
 
        for (Object o : network.getEdges())	{
            GeomPlanarGraphEdge e = (GeomPlanarGraphEdge) o;

            idsToEdges.put(e.getIntegerAttribute("ROAD_ID").intValue(), e);
                      
            e.setData(new ArrayList<MainAgent>());
        }
    
        addIntersectionNodes(network.nodeIterator(), junctions);
    }


    /**
     * Read in the population files and create appropriate populations
     * @param filename
     */
    ////////////////////// MainAgent /////////////////////////////
    public void populateAgent(String filename)	{
    	//System.out.println("Populating model: ");
        try	{
            String filePath = MK_1.class.getResource(filename).getPath();
            FileInputStream fstream = new FileInputStream(filePath);
            System.out.println("Populating model with Main Agents: " +filePath);
            System.out.println();
            
            BufferedReader d = new BufferedReader(new InputStreamReader(fstream));
            String s;

            // get rid of the header
            d.readLine();
            // read in all data
            while ((s = d.readLine()) != null)	{ 
                String[] bits = s.split(",");
                
                int pop = Integer.parseInt(bits[2]);
                //System.out.println();
                System.out.println("Main Agent road segment population (C:Count): " +pop);

                String homeTract = bits[3];
                System.out.println("Main Agent homeTract (D:ROAD_ID): " +homeTract);

                String workTract = bits[4];
                System.out.println("Main Agent workTract (E:Work): " +workTract);

                String id_id = bits[3];
                System.out.println("Main Agent ID_ID (D:ROAD_ID): " +id_id);

                String ROAD_ID = bits[3];
                System.out.println("Main Agent road segment (D:ROAD_ID): " +ROAD_ID);
                //System.out.println();
                
                GeomPlanarGraphEdge startingEdge = idsToEdges.get(
                		(int) Double.parseDouble(ROAD_ID));
                GeomPlanarGraphEdge goalEdge = idsToEdges.get(
                    goals[ random.nextInt(goals.length)]);
                //System.out.println("startingEdge: " +startingEdge);
                //System.out.println("idsToEdges: " +idsToEdges);
                //System.out.println("goalEdge: " +goalEdge);
                //System.out.println("goals: " +goals);
                //System.out.println("homeNode: " +goals);
                
                for (int i = 0; i < pop; i++)	{
                	//pop; i++)	{ 	// NO IDEA IF THIS MAKES A DIFFERENCE!?!
                    MainAgent a = new MainAgent(this, homeTract, workTract, startingEdge, goalEdge);                    
                    //System.out.println("Agent " + i + ":" + " Home: " +homeTract + ";"
                    		//+ "	Work: " +workTract + ",	Starting Edge: " +startingEdge);
                    boolean successfulStart = a.start(this);
                    //System.out.println("Starting...");

                    if (!successfulStart)	{
                    	System.out.println("Successful!");
                    	continue; // DON'T ADD IT if it's bad
                    }

                    // MasonGeometry newGeometry = new MasonGeometry(a.getGeometry());
                    MasonGeometry newGeometry = a.getGeometry();
                    newGeometry.isMovable = true;
                    agents.addGeometry(newGeometry);
                    agentList.add(a);
                    schedule.scheduleRepeating(a);
                }
            }

            d.close();
            
        } catch (Exception e) {
		    	System.out.println("ERROR: issue with population file: ");
				e.printStackTrace();
		}    
    }
    
    //////////////////////// NGOAgent /////////////////////////////
    public void populateNGO(String filename)	{
        try	{
            String filePath = MK_1.class.getResource(filename).getPath();
            FileInputStream fstream = new FileInputStream(filePath);
            System.out.println();
            System.out.println("Populating model with NGO Agents: " +filePath);
            System.out.println();
            
            BufferedReader d = new BufferedReader(new InputStreamReader(fstream));
            String s;

            // get rid of the header
            d.readLine();
            // read in all data
            while ((s = d.readLine()) != null)	{ 
                String[] bits = s.split(",");
                
                int pop = Integer.parseInt(bits[2]);
                //System.out.println();
                System.out.println("NGO Agent road segment population (C:Count): " +pop);

                String homeTract = bits[3];
                System.out.println("NGO Agent homeTract (D:ROAD_ID): " +homeTract);

                String workTract = bits[4];
                System.out.println("NGO Agent workTract (E:Work): " +workTract);

                String id_id = bits[3];
                System.out.println("NGO Agent ID_ID (D:ROAD_ID): " +id_id);

                String ROAD_ID = bits[3];
                System.out.println("NGO Agent road segment (D:ROAD_ID): " +ROAD_ID);
                //System.out.println();
                
                GeomPlanarGraphEdge startingEdge = idsToEdges.get(
                		(int) Double.parseDouble(ROAD_ID));
                GeomPlanarGraphEdge goalEdge = idsToEdges.get(
                    goals1[ random.nextInt(goals1.length)]);
                
                for (int i = 0; i < pop; i++)	{
                	//pop; i++)	{ 	// NO IDEA IF THIS MAKES A DIFFERENCE!?!
                    NGOAgent a = new NGOAgent(this, homeTract, workTract, startingEdge, goalEdge);                    
                    //System.out.println("NGO Agent " + i + ":" + " Home: " +homeTract + ";"
                    	//	+ "	Work: " +workTract + ",	Starting Edge: " +startingEdge);
                    boolean successfulStart = a.start(this);

                    if (!successfulStart)	{
                    	System.out.println("Successful!");
                    	continue; // DON'T ADD IT if it's bad
                    }

                    MasonGeometry newGeometry = a.getGeometry();
                    newGeometry.isMovable = true;
                    ngoagents.addGeometry(newGeometry);
                    ngoAgentList.add(a);
                    schedule.scheduleRepeating(a);
                }
            }

            d.close();
            
        } catch (Exception e) {
		    	System.out.println("ERROR: issue with population file: ");
				e.printStackTrace();
		}    
    }
    
    //////////////////////// ElderlyAgent ///////////////////////////
    public void populateElderly(String filename)	{
        try	{
            String filePath = MK_1.class.getResource(filename).getPath();
            FileInputStream fstream = new FileInputStream(filePath);
            System.out.println();
            System.out.println("Populating model with Elderly Agents: " +filePath);
            System.out.println();
            
            BufferedReader d = new BufferedReader(new InputStreamReader(fstream));
            String s;

            // get rid of the header
            d.readLine();
            // read in all data
            while ((s = d.readLine()) != null)	{ 
                String[] bits = s.split(",");
                
                int pop = Integer.parseInt(bits[2]);
                //System.out.println();
                System.out.println("Elderly Agent road segment population (C:Count): " +pop);

                String homeTract = bits[3];
                System.out.println("Elderly Agent homeTract (D:ROAD_ID): " +homeTract);

                String workTract = bits[4];
                System.out.println("Elderly Agent workTract (E:Work): " +workTract);

                String id_id = bits[3];
                System.out.println("Elderly Agent ID_ID (D:ROAD_ID): " +id_id);

                String ROAD_ID = bits[3];
                System.out.println("Elderly Agent road segment (D:ROAD_ID): " +ROAD_ID);
                //System.out.println();
                
                GeomPlanarGraphEdge startingEdge = idsToEdges.get(
                		(int) Double.parseDouble(ROAD_ID));
                GeomPlanarGraphEdge goalEdge = idsToEdges.get(
                    goals2[ random.nextInt(goals2.length)]);
                
                for (int i = 0; i < pop; i++)	{
                	//pop; i++)	{ 	// NO IDEA IF THIS MAKES A DIFFERENCE!?!
                    ElderlyAgent a = new ElderlyAgent(this, homeTract, workTract, startingEdge, goalEdge);                    
                    //System.out.println("Elderly Agent " + i + ":" + " Home: " +homeTract + ";"
                    	//	+ "	Work: " +workTract + ",	Starting Edge: " +startingEdge);
                    boolean successfulStart = a.start(this);

                    if (!successfulStart)	{
                    	System.out.println("Successful!");
                    	continue; // DON'T ADD IT if it's bad
                    }

                    MasonGeometry newGeometry = a.getGeometry();
                    newGeometry.isMovable = true;
                    elderlyagents.addGeometry(newGeometry);
                    elderlyAgentList.add(a);
                    schedule.scheduleRepeating(a);
                }
            }

            d.close();
            
        } catch (Exception e) {
		    	System.out.println("ERROR: issue with population file: ");
				e.printStackTrace();
		}    
    }
    
    //////////////////// LimitedActions Agent ////////////////////////////
    public void populateLimitedActions(String filename)	{
        try	{
            String filePath = MK_1.class.getResource(filename).getPath();
            FileInputStream fstream = new FileInputStream(filePath);
            System.out.println();
            System.out.println("Populating model with Limited Actions Agents: " +filePath);
            System.out.println();
            
            BufferedReader d = new BufferedReader(new InputStreamReader(fstream));
            String s;

            // get rid of the header
            d.readLine();
            // read in all data
            while ((s = d.readLine()) != null)	{ 
                String[] bits = s.split(",");
                
                int pop = Integer.parseInt(bits[2]);
                //System.out.println();
                System.out.println("LimitedActions Agent road segment population (C:Count): " +pop);

                String homeTract = bits[3];
                System.out.println("LimitedActions Agent homeTract (D:ROAD_ID): " +homeTract);

                String workTract = bits[4];
                System.out.println("LimitedActions Agent workTract (E:Work): " +workTract);

                String id_id = bits[3];
                System.out.println("LimitedActions Agent ID_ID (D:ROAD_ID): " +id_id);

                String ROAD_ID = bits[3];
                System.out.println("LimitedActions Agent road segment (D:ROAD_ID): " +ROAD_ID);
                //System.out.println();
                
                GeomPlanarGraphEdge startingEdge = idsToEdges.get(
                		(int) Double.parseDouble(ROAD_ID));
                GeomPlanarGraphEdge goalEdge = idsToEdges.get(
                    goals3[ random.nextInt(goals3.length)]);
                
                for (int i = 0; i < pop; i++)	{
                	//pop; i++)	{ 	// NO IDEA IF THIS MAKES A DIFFERENCE!?!
                    LimitedActionsAgent a = new LimitedActionsAgent(this, homeTract, workTract, startingEdge, goalEdge);                    
                    //System.out.println("Limited Actions Agent " + i + ":" + " Home: " +homeTract + ";"
                    	//	+ "	Work: " +workTract + ",	Starting Edge: " +startingEdge);
                    boolean successfulStart = a.start(this);

                    if (!successfulStart)	{
                    	System.out.println("Successful!");
                    	continue; // DON'T ADD IT if it's bad
                    }

                    MasonGeometry newGeometry = a.getGeometry();
                    newGeometry.isMovable = true;
                    limitedactionsagents.addGeometry(newGeometry);
                    limitedActionsAgentList.add(a);
                    schedule.scheduleRepeating(a);
                }
            }

            d.close();
            
        } catch (Exception e) {
		    	System.out.println("ERROR: issue with population file: ");
				e.printStackTrace();
		}    
    }
    
    
    /** adds nodes corresponding to road intersections to GeomVectorField
     *
     * @param nodeIterator Points to first node
     * @param intersections GeomVectorField containing intersection geometry
     *
     * Nodes will belong to a planar graph populated from LineString network.
     */
    private void addIntersectionNodes(Iterator<?> nodeIterator,
                                      GeomVectorField intersections)	{
        GeometryFactory fact = new GeometryFactory();
        Coordinate coord = null;
        Point point = null;
        int counter = 0;

        while (nodeIterator.hasNext())	{
            Node node = (Node) nodeIterator.next();
            coord = node.getCoordinate();
            point = fact.createPoint(coord);

            junctions.addGeometry(new MasonGeometry(point));
            counter++;
        }
    }

    
    /**
     * Main function allows simulation to be run in stand-alone, non-GUI mode
     */
    public static void main(String[] args)	{
        doLoop(MK_1.class, args);        
        System.exit(0);
    }
}
