package sim.app.geo.norfolk_routingTEST;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.geo.GeomVectorField;
import sim.io.geo.ShapeFileExporter;
import sim.io.geo.ShapeFileImporter;
import sim.util.geo.GeomPlanarGraph;
import sim.util.geo.GeomPlanarGraphEdge;
import sim.util.geo.MasonGeometry;
import au.com.bytecode.opencsv.CSVReader;

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
public class NorfolkRoutingTEST extends SimState	{
	
	/////////////// Model Parameters ///////////////////////////////////
    
	private static final long serialVersionUID = -4554882816749973618L;

	/////////////// Containers ///////////////////////////////////////
    
    public GeomVectorField roads = new GeomVectorField();
    public GeomVectorField lsoa = new GeomVectorField();
    public GeomVectorField flood = new GeomVectorField();
    public GeomVectorField agents = new GeomVectorField();

    /////////////// Network ///////////////////////////////////////
    
    // Stores the road network connections
    public GeomPlanarGraph network = new GeomPlanarGraph();
    public GeomVectorField junctions = new GeomVectorField(); // nodes for intersections

    // mapping between unique edge IDs and edge structures themselves
    HashMap<Integer, GeomPlanarGraphEdge> idsToEdges =
        new HashMap<Integer, GeomPlanarGraphEdge>();

    HashMap<GeomPlanarGraphEdge, ArrayList<MainAgent>> edgeTraffic =
        new HashMap<GeomPlanarGraphEdge, ArrayList<MainAgent>>();

    public GeomVectorField mainagents = new GeomVectorField();

    ArrayList<MainAgent> agentList = new ArrayList<MainAgent>();
    
    // system parameter: can force agents to go to or from work at any time
    boolean goToWork = true;

    public boolean getGoToWork()	{
        return goToWork;
    }

    public void setGoToWork(boolean val)	{
        goToWork = val;
    }

    Integer[] goals =	{
    		// Relates to ROAD_ID in NorfolkITNLSOA.csv/.shp...
    		// 30250 = Norfolk & Norwich Hospital
    		// 74858 = James Paget Hospital (Does not work!)
    		// 18081 = Queen Elizabeth Hospital
    		30250, 18081, 519
    };

    
    ///////////////////////////////////////////////////////////////////////////
	/////////////////////////// BEGIN FUNCTIONS ///////////////////////////////
	///////////////////////////////////////////////////////////////////////////	
    
    /**
     * Constructor
     */
    public NorfolkRoutingTEST(long seed)	{
        super(seed);
    }

    
    /**
     * Initialization
     */
    @Override
    public void start() {
        super.start();
        System.out.println("Reading shapefiles...");

		//////////////////////////////////////////////
		///////////// READING IN DATA ////////////////
		//////////////////////////////////////////////
        
        try {
            // read in the roads shapefile to create the transit network
            URL roadsFile = NorfolkRoutingTEST.class.getResource
            		("data/NorfolkITNLSOA.shp");
            ShapeFileImporter.read(roadsFile, roads);
            System.out.println("Roads shapefile: " +roadsFile);
            
            Envelope MBR = roads.getMBR();

            // read in the LSOA shapefile to create the background        
            URL areasFile = NorfolkRoutingTEST.class.getResource
            		("data/NorfolkLSOA.shp");
            ShapeFileImporter.read(areasFile, lsoa);
            System.out.println("LSOA shapefile: " +areasFile);

            MBR.expandToInclude(lsoa.getMBR());

            // read in the floods file     
            URL floodFile = NorfolkRoutingTEST.class.getResource
            		("data/flood_zone_3_010k_NORFOLK_ONLY.shp");
            ShapeFileImporter.read(floodFile, flood);
            System.out.println("Flood shapefile: " +floodFile);
            System.out.println();
            
            MBR.expandToInclude(flood.getMBR());
            
            createNetwork();

            //////////////////////////////////////////////
            ////////////////// CLEANUP ///////////////////
            //////////////////////////////////////////////

            // standardize the MBRs so that the visualization lines up
            // and everyone knows what the standard MBR is
            roads.setMBR(MBR);
            lsoa.setMBR(MBR);
            flood.setMBR(MBR);
            
            //////////////////////////////////////////////
            ////////////////// AGENTS ////////////////////
            //////////////////////////////////////////////
            
            // initialize agents
            populate("data/NorfolkITNLSOA.csv");
            agents.setMBR(MBR);

            // Ensure that the spatial index is updated after all the agents move
            schedule.scheduleRepeating( agents.scheduleSpatialIndexUpdater(),
            		Integer.MAX_VALUE, 1.0);

            /**
             * Steppable that flips Agent paths once everyone reaches their destinations
             */
            Steppable flipper = new Steppable()	{
				private static final long serialVersionUID = 1L;

				@Override
                public void step(SimState state)	{

					NorfolkRoutingTEST gstate = (NorfolkRoutingTEST) state;

                    // pass to check if anyone has not yet reached work
                    for (MainAgent a : gstate.agentList)	{
                        if (!a.reachedDestination)	{
                            return; // someone is still moving: let him do so
                        }
                    }
                    // send everyone back in the opposite direction now
                    boolean toWork = gstate.goToWork;
                    gstate.goToWork = !toWork;

                    // otherwise everyone has reached their latest destination:
                    // turn them back
                    for (MainAgent a : gstate.agentList)	{
                        a.flipPath();
                    }
                }
            };
            schedule.scheduleRepeating(flipper, 10);

        } catch (FileNotFoundException e)	{
            System.out.println("Error: missing required data file");
        }
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
     * Read in the population file and create an appropriate pop
     * @param filename
     */
    public void populate(String filename)	{
    	//System.out.println("Populating model: ");
        try	{
            String filePath = NorfolkRoutingTEST.class.getResource(filename).getPath();
            FileInputStream fstream = new FileInputStream(filePath);
            System.out.println("Populating model: " +filePath);
            System.out.println();
            
            BufferedReader d = new BufferedReader(new InputStreamReader(fstream));
            String s;

            // get rid of the header
            d.readLine();
            // read in all data
            while ((s = d.readLine()) != null)	{ 
                String[] bits = s.split(",");
                
                // 39th column in NorfolkITNLSOA.csv = column AN "POP" e.g. 1, 10, 100...
                int pop = Integer.parseInt(bits[39]);
                //System.out.println("Main Agent Population: " +pop);
                
                // 11th column in NorfolkITNLSOA.csv = column L "ROAD_ID" e.g. 1, 2, 3...
                String homeTract = bits[11];
                //System.out.println("Main Agent Home (LSOA_ID): " +homeTract);
                
                // 40th column in NorfolkITNLSOA.csv = column AO "Work1" e.g. 307, 143, 13...
                String workTract = bits[40];
                //System.out.println("Main Agent Work (WORK): " +workTract);
                
                // 11th column in NorfolkITNLSOA.csv = column L "ROAD_ID" e.g. 1, 2, 3...
                String id_id = bits[11];
                //System.out.println("Main Agent ID_ID (ROAD_ID): " +id_id);
                
                // 11th column in NorfolkITNLSOA.csv = column L "ROAD_ID" e.g. 1, 2, 3...
                String ROAD_ID = bits[11];
                //System.out.println("Main Agent ROAD_ID: " +ROAD_ID);
                
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
                    System.out.println("Agent " + i + ":" + " Home: " +homeTract + ";"
                    		+ "	Work: " +workTract + ",	Starting Edge: " +startingEdge);
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
        doLoop(NorfolkRoutingTEST.class, args);        
        System.exit(0);
        
    }
}
