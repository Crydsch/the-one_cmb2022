package movement;

import core.Coord;
import core.Settings;
import core.SimError;
import input.WKTMapReader;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.SimMap;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TimetableMovement extends MapBasedMovement {

    /** Our internal timetable working set **/
    // Adding a dedicated map only for rooms
    private int nrofHosts = 0;
    private DijkstraPathFinder pathFinder;
    private HashMap<Integer, ArrayList<Coord>> timetable = null;
    private int timeOfDay = 0;

    /** Configuration parameters **/
    public static final String MAP_BASE_MOVEMENT_NS = "TimetableMovement";
    public static final String START_MAP_NUM = "nrofStartMap";
    public static final String START_DAY_TIME = "startOfDay";
    // Below are some general settings to get more information
    public static final String SCENARIO_NS = "Scenario";
    public static final String GROUP_NS = "Group";
    public static final String HOST_GROUPS = "nrofHostGroups";
    public static  final String NUM_HOSTS = "nrofHosts";

    public TimetableMovement(Settings settings) {
        super(settings);
        nrofHosts = getNumOfHosts();
        timetable = createTimetable();
        // Cheating to actually use all nodes for the path
        int[] allowed = new int[32];
        Arrays.setAll(allowed, p -> p);
        pathFinder = new DijkstraPathFinder(allowed);
    }

    protected  TimetableMovement(TimetableMovement tm) {
        super(tm);
        this.timeOfDay = tm.timeOfDay;
        this.timetable = tm.timetable;
        this.nrofHosts = tm.nrofHosts;
        this.pathFinder = tm.pathFinder;
    }

    private int getNumOfHosts() {
        Settings settings = new Settings(SCENARIO_NS);
        int hostGroups = settings.getInt(HOST_GROUPS);
        int numHosts = 0;
        for (int i = 1; i <= hostGroups; i++) {
            Settings groupSetting = new Settings(GROUP_NS + i);
            numHosts += groupSetting.getInt(NUM_HOSTS);
        }
        return numHosts;
    }

    private HashMap<Integer, ArrayList<Coord>> createTimetable() {
        Settings settings = new Settings(MAP_BASE_MOVEMENT_NS);
        this.timeOfDay = settings.getInt(START_DAY_TIME);

        HashMap timetable = new HashMap<>(nrofHosts);


        return timetable;
    }

    /** The initial location is currently used from the MapBasedMovement **/
    @Override
    public Coord getInitialLocation() {
        // TODO: Select list of nodes that make valid entry points and use these as
        // starting point



        Settings settings = new Settings(MAP_BASE_MOVEMENT_NS);
        int numStartMap = settings.getInt(START_MAP_NUM);

        MapNode startNode;
        SimMap map = getMap();
        List<MapNode> nodes = map.getNodes();
        do {
            startNode = nodes.get(rng.nextInt(nodes.size()));
        } while(!startNode.isType(numStartMap));

        this.lastMapNode = startNode;

        return startNode.getLocation().clone();
    }

    /** This method is used to determine where the node is going next **/
    @Override
    public Path getPath() {
        Path p  = new Path(generateSpeed());
        MapNode nextNode;

        SimMap map = getMap();
        // TODO: This has to be adapted to the timetable structure
//        do {
//            nextNode = map.getNodes().get(rng.nextInt(map.getNodes().size()));
//        } while (1 < nextNode.getNeighbors().size());
        do {
            nextNode = map.getNodes().get(rng.nextInt(map.getNodes().size()));
        } while (getOkMapNodeTypes() != null && !nextNode.isType(getOkMapNodeTypes()));

        // The rest is simply from the shortestPathExample
        List<MapNode> nodePath = pathFinder.getShortestPath(lastMapNode, nextNode);
        assert nodePath.size() > 0 : "No path from " + lastMapNode + " to " +
                nextNode + ". The simulation map isn't fully connected";
        System.out.println(nodePath.size());
        for (MapNode node : nodePath) {
            p.addWaypoint(node.getLocation());
        }
        lastMapNode = nextNode;
        return p;
    }

    @Override
    public TimetableMovement replicate() {return new TimetableMovement(this);}
}
