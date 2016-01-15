package sim.app.geo.norfolk_csvTEST;

import com.vividsolutions.jts.io.ParseException;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;

import org.jfree.data.xy.XYSeries;

//import sim.app.geo.gridlock_norfolk.Agent;
//import sim.app.geo.gridlock_norfolk.Gridlock_Norfolk;
//import sim.app.geo.gridlock_norfolk.Gridlock_NorfolkWithUI;
import sim.app.keepaway.Bot;
import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.portrayal.geo.GeomPortrayal;
import sim.portrayal.geo.GeomVectorFieldPortrayal;
import sim.portrayal.simple.MovablePortrayal2D;
import sim.portrayal.simple.OvalPortrayal2D;
import sim.util.media.chart.TimeSeriesChartGenerator;

/**
 * A simple model that locates agents on Norfolk's road network
 * and makes them move from A to B.
 * 
 * @author KJGarbutt
 */
public class NorfolkCSVTESTWithUI extends GUIState	{
    private Display2D display;
    private JFrame displayFrame;

    // Map visualization objects
    private GeomVectorFieldPortrayal lsoaPortrayal = new GeomVectorFieldPortrayal();
    private GeomVectorFieldPortrayal roadsPortrayal = new GeomVectorFieldPortrayal();
    private GeomVectorFieldPortrayal agentPortrayal = new GeomVectorFieldPortrayal();
    private GeomVectorFieldPortrayal floodPortrayal = new GeomVectorFieldPortrayal();
    //private GeomVectorFieldPortrayal ngoagentPortrayal = new GeomVectorFieldPortrayal();
    //private GeomVectorFieldPortrayal elderlyagentPortrayal = new GeomVectorFieldPortrayal();
    //private GeomVectorFieldPortrayal limactagentPortrayal = new GeomVectorFieldPortrayal();
    TimeSeriesChartGenerator trafficChart;
    XYSeries maxSpeed;
    XYSeries avgSpeed;
    XYSeries minSpeed;
    
    ///////////////////////////////////////////////////////////////////////////
    /////////////////////////// BEGIN functions ///////////////////////////////
    ///////////////////////////////////////////////////////////////////////////	
    
    /** Default constructor */
    protected NorfolkCSVTESTWithUI(SimState state)
        {
            super(state);
        }

        /**
         * Main function
         * @param args
         */
        public static void main(String[] args)
        {
        	NorfolkCSVTESTWithUI simple = new NorfolkCSVTESTWithUI(new NorfolkCSVTEST(System.currentTimeMillis()));
            Console c = new Console(simple);
            c.setVisible(true);
        }



        /**
         * @return name of the simulation
         */
        public static String getName()
        {
            return "NorfolkCSVTEST";
        }



        /**
         *  This must be included to have model tab, which allows mid-simulation
         *  modification of the coefficients
         */
        public Object getSimulationInspectedObject()
        {
            return state;
        }  // non-volatile



        /**
         * Called when starting a new run of the simulation. Sets up the portrayals
         * and chart data.
         */
        public void start()
        {
            super.start();

            NorfolkCSVTEST world = (NorfolkCSVTEST) state;

            maxSpeed = new XYSeries("Max Speed");
            avgSpeed = new XYSeries("Average Speed");
            minSpeed = new XYSeries("Min Speed");
            trafficChart.removeAllSeries();
            trafficChart.addSeries(maxSpeed, null);
            trafficChart.addSeries(avgSpeed, null);
            trafficChart.addSeries(minSpeed, null);

            state.schedule.scheduleRepeating(new Steppable()
            {

                public void step(SimState state)
                {
                	NorfolkCSVTEST world = (NorfolkCSVTEST) state;
                    double maxS = 0, minS = 10000, avgS = 0, count = 0;
                    for (MainAgent a : world.agentList)
                    {
                        if (a.reachedDestination)
                        {
                            continue;
                        }
                        count++;
                        double speed = Math.abs(a.speed);
                        avgS += speed;
                        if (speed > maxS)
                        {
                            maxS = speed;
                        }
                        if (speed < minS)
                        {
                            minS = speed;
                        }
                    }
                    double time = state.schedule.time();
                    avgS /= count;
                    maxSpeed.add(time, maxS, true);
                    minSpeed.add(time, minS, true);
                    avgSpeed.add(time, avgS, true);
                }

            });

            roadsPortrayal.setField(world.roads);
            // roadsPortrayal.setPortrayalForAll(new RoadPortrayal());//GeomPortrayal(Color.DARK_GRAY,0.001,false));
            roadsPortrayal.setPortrayalForAll(new GeomPortrayal(Color.DARK_GRAY, 0.001, false));

            lsoaPortrayal.setField(world.lsoa);
            // tractsPortrayal.setPortrayalForAll(new PolyPortrayal());//(Color.GREEN,true));
            lsoaPortrayal.setPortrayalForAll(new GeomPortrayal(Color.LIGHT_GRAY, true));

            floodPortrayal.setField(world.flood);
            floodPortrayal.setPortrayalForAll(new GeomPortrayal(Color.CYAN, true));
            
            agentPortrayal.setField(world.agents);
            agentPortrayal.setPortrayalForAll(new GeomPortrayal(Color.RED, 50, true));

            display.reset();
            display.setBackdrop(Color.WHITE);

            display.repaint();

        }



        /**
         * Called when first beginning a WaterWorldWithUI. Sets up the display window,
         * the JFrames, and the chart structure.
         */
        public void init(Controller c)
        {
            super.init(c);

            // make the displayer
            display = new Display2D(1300, 600, this);
            // turn off clipping
            // display.setClipping(false);

            displayFrame = display.createFrame();
            displayFrame.setTitle("NorfolkCSVTEST");
            c.registerFrame(displayFrame); // register the frame so it appears in
            // the "Display" list
            displayFrame.setVisible(true);

            display.attach(lsoaPortrayal, "LSOA");
            display.attach(floodPortrayal, "Flood Zone");
            display.attach(roadsPortrayal, "Roads");
            display.attach(agentPortrayal, "Agents");

            // CHART
            trafficChart = new TimeSeriesChartGenerator();
            trafficChart.setTitle("Traffic Statistics");
            trafficChart.setYAxisLabel("Speed");
            trafficChart.setXAxisLabel("Time");
            JFrame chartFrame = trafficChart.createFrame(this);
            chartFrame.pack();
            c.registerFrame(chartFrame);

        }



        /**
         * called when quitting a simulation. Does appropriate garbage collection.
         */
        public void quit()
        {

        	System.out.println("Exiting..?");
        	super.quit();

            if (displayFrame != null)
            {
                displayFrame.dispose();
            }
            displayFrame = null; // let gc
            display = null; // let gc
        }

    }
