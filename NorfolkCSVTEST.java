package sim.app.geo.norfolk_csvTEST;

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

import sim.app.geo.gridlock.Agent;
import sim.app.geo.gridlock.Gridlock;
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
 * THIS IS A TEST OF THE SYNC BETWEEN ECLIPSE>COPY>GITHUB
 * 
 * A simple model that locates agents on Norfolk's road network
 * and makes them move from A to B.
 * 
 * @author KJGarbutt
 *
 */
public class NorfolkCSVTEST extends SimState	{
	/////////////// Model Parameters ///////////////////////////////////
    private static final long serialVersionUID = -4554882816749973618L;

	/////////////// Containers ///////////////////////////////////////
    public GeomVectorField roads = new GeomVectorField();
    public GeomVectorField lsoa = new GeomVectorField();
    public GeomVectorField flood = new GeomVectorField();
    public GeomVectorField agents = new GeomVectorField();

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

    // cheap, hacky, hard-coded way to identify which edges are associated with
    // goal Nodes. Done because we cannot seem to read in .shp file for goal nodes because
    // of an NegativeArraySize error? Any suggestions very welcome!
    Integer[] goals =	{
    		//72142, 72176, 72235, 72178, 89178
    		10, 24, 38
    };

    
    /** Constructor */
    public NorfolkCSVTEST(long seed)
    {
        super(seed);
    }

    
    /** Initialization */
    @Override
    public void start() {
        super.start();

        // read in data
        try {
            // read in the roads to create the transit network
            System.out.println("reading roads layer...");
            URL roadsFile = NorfolkCSVTEST.class.getResource("data/NorfolkITNLSOA.shp");
            ShapeFileImporter.read(roadsFile, roads);
            Envelope MBR = roads.getMBR();

            // read in the tracts to create the background
            System.out.println("reading areas layer...");         
            URL areasFile = NorfolkCSVTEST.class.getResource("data/NorfolkLSOA.shp");
            ShapeFileImporter.read(areasFile, lsoa);

            MBR.expandToInclude(lsoa.getMBR());

            createNetwork();

            // update so that everyone knows what the standard MBR is
            roads.setMBR(MBR);
            lsoa.setMBR(MBR);

            // initialize agents
            populate("data/NorfolkITNLSOA.csv");
            agents.setMBR(MBR);

            // Ensure that the spatial index is updated after all the agents move
            schedule.scheduleRepeating( agents.scheduleSpatialIndexUpdater(), Integer.MAX_VALUE, 1.0);

            /** Steppable that flips Agent paths once everyone reaches their destinations*/
            Steppable flipper = new Steppable()	{
				private static final long serialVersionUID = 1L;

				@Override
                public void step(SimState state)	{

					NorfolkCSVTEST gstate = (NorfolkCSVTEST) state;

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


    /** Create the road network the agents will traverse
     *
     */
    private void createNetwork()
    {
        System.out.println("creating network...");

        network.createFromGeomField(roads);
 
        for (Object o : network.getEdges())	{
            GeomPlanarGraphEdge e = (GeomPlanarGraphEdge) o;

            idsToEdges.put(e.getIntegerAttribute("TOID").intValue(), e);
  
            e.setData(new ArrayList<MainAgent>());
        }
    
        addIntersectionNodes(network.nodeIterator(), junctions);
    }


    /**
     * Read in the population file and create an appropriate pop
     * @param filename
     */
    public void populate(String filename)	{
    	System.out.println("populating model...");
        try	{
        	// filename = NorfolkITNLSOA.csv
            String filePath = NorfolkCSVTEST.class.getResource(filename).getPath();
            // filePath = data/NorfolkITNLSOA.csv
            FileInputStream fstream = new FileInputStream(filePath);

            BufferedReader d = new BufferedReader(new InputStreamReader(fstream));
            String s;

            // get rid of the header
            d.readLine();
            // read in all data
            while ((s = d.readLine()) != null)	{ 
                String[] bits = s.split(",");
                
                // 24th column in NorfolkITNLSOA.csv = column Y "LSOA_ID" e.g. 120, 145, 317...
                int pop = Integer.parseInt(bits[24]); // TODO: reset me if desired!
                System.out.println("Main Agent: " +pop);
                
                // 40th column in NorfolkITNLSOA.csv = column AO "Work1" e.g. 1, 1, 1...
                int workTract = Integer.parseInt(bits[40]);
                System.out.println("Main Agent work: " +workTract);
                
                // 11th column in NorfolkITNLSOA.csv = column L "ROAD_ID" e.g. 1, 2, 3...
                int homeTract = Integer.parseInt(bits[11]);
                System.out.println("Main Agent home: " +homeTract);
                
                // 11th column in NorfolkITNLSOA.csv = column L "ROAD_ID" e.g. 1, 2, 3...
                String id_id = bits[11];
                System.out.println("Main Agent ID_ID: " +id_id);
                
                GeomPlanarGraphEdge startingEdge = idsToEdges.get(
                    (double) Double.parseDouble(id_id));
                GeomPlanarGraphEdge goalEdge = idsToEdges.get(
                    goals[ random.nextInt(goals.length)]);
                for (double i = 0; i < 1; i++){
                    MainAgent a = new MainAgent(this, homeTract, workTract, startingEdge, goalEdge);                    
                    
                    boolean successfulStart = a.start(this);

                    if (!successfulStart)	{
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

            // clean up
            d.close();

        } catch (Exception e)
        {
            System.out.println("ERROR: issue with population file: " + e);
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
                                      GeomVectorField intersections)
    {
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


    /** Main function allows simulation to be run in stand-alone, non-GUI mode */
    public static void main(String[] args)
    {
        doLoop(NorfolkCSVTEST.class, args);
        System.exit(0);
    }

}