package movement;

import core.Coord;
import core.Settings;
import core.SimClock;
import core.SimError;
import input.MapDescriptionReader;
import input.WKTMapReader;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.SimMap;
import movement.map.TimetableNode;
import util.RoomType;

import javax.sound.midi.SysexMessage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

public class TimetableMovement extends MapBasedMovement {

    /** Our internal timetable working set **/
    // Adding a dedicated map only for rooms
    private int nrofHosts = 0;
    private DijkstraPathFinder pathFinder;
    private static HashMap<Integer, List<TimetableNode>> timetable = null;
    // Used to differentiate the users
    private int userNum;
    private static int processedUsers = 0;

    /** Configuration parameters **/
    public static final String TIMETABLE_MOVEMENT_NS = "TimetableMovement";
    public static final String START_MAP_NUM = "nrofStartMap";
    public static final String START_DAY_TIME = "startOfDay";
    public static final String NUM_ACTIVITIES = "defActivities";
    public static final String DEF_DUR = "defActivityDur";
    public static final String SEC_PER_ITER = "secondsPer1Iter";
    public static final String SPAWN_PROBS = "spawnProbability";
    public static final String ACT_PROBS = "activityProbability";

    // Below are some general settings to get more information
    public static final String SCENARIO_NS = "Scenario";
    public static final String GROUP_NS = "Group";
    public static final String HOST_GROUPS = "nrofHostGroups";
    public static  final String NUM_HOSTS = "nrofHosts";

    public TimetableMovement(Settings settings) {
        super(settings);
        nrofHosts = getNumOfHosts();
        userNum = processedUsers++;
        timetable = fillTimetable(userNum);
        // Cheating to actually use all nodes for the path
        int[] allowed = new int[32];
        Arrays.setAll(allowed, p -> p);
        pathFinder = new DijkstraPathFinder(allowed);
    }

    protected  TimetableMovement(TimetableMovement tm) {
        super(tm);
        this.userNum = processedUsers++;
        this.timetable = tm.timetable;
        this.nrofHosts = tm.nrofHosts;
        this.pathFinder = tm.pathFinder;
        fillTimetable(this.userNum);
//        System.out.println("Timetable contains: " + timetable.get(userNum).size() + " elems");
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

    private HashMap<Integer, List<TimetableNode>> fillTimetable(int user) {
//        System.out.println("Filling timetable for user: " + userNum);
        if (timetable == null)
            timetable = new HashMap<>();

        Settings settings = new Settings(TIMETABLE_MOVEMENT_NS);
        int numStartMap = settings.getInt(START_MAP_NUM);
        int defDuration = settings.getInt(DEF_DUR);
        double startTime = settings.getDouble(START_DAY_TIME);
        int[] probs = settings.getCsvInts(SPAWN_PROBS, 4);
        int roomMapNum = getOkMapNodeTypes()[0];
        Settings settings1 = new Settings(MAP_BASE_MOVEMENT_NS);
        String roomString = settings1.getSetting(FILE_S + roomMapNum);
        File roomFile = new File(roomString);
        MapDescriptionReader reader = new MapDescriptionReader(roomFile);
        HashMap<RoomType, List<Coord>> mapping;
        try {
            mapping = reader.readDescription();
        } catch (IOException e) {
            System.out.println(e.fillInStackTrace());
        }

        // Calculate the start position based on probabilities
        int hostCounter = 0;
        for (int i=0; i < probs.length; i++) {
            probs[i] = hostCounter + (int) Math.floor(nrofHosts * (probs[i] / (double)100));
            hostCounter = probs[i];
        }
        List<TimetableNode> timeplan = new ArrayList<>();
        MapNode start;
        SimMap map = getMap();
        List<MapNode> mapNodes = map.getNodes();
        List<MapNode> filteredNodes = mapNodes.stream().filter(p -> p.isType(numStartMap)).collect(Collectors.toList());
        int index = 0;
        for (; index < probs.length; index++) {
            if (probs[index] >= user)
                break;
        }
        index = Math.min(index, filteredNodes.size()-1);
//        System.out.println("User " + user + " index " + index);
        start = filteredNodes.get(index);

        TimetableNode startNode = new TimetableNode(start, startTime);
        timeplan.add(startNode);

        // -------------------------------------------------------------------
        // Select daily activities
        // Currently randomly selected classroom
        int activites = settings.getInt(NUM_ACTIVITIES);
        double[] actProbs = settings.getCsvDoubles(ACT_PROBS, 4);
        for (int i = 0; i < actProbs.length; i++) {
            actProbs[i] = actProbs[i] / (double)100;
        }

        MapNode nextActivity;
        for (int i = 0; i < activites; i++) {
            do {
                nextActivity = mapNodes.get(rng.nextInt(mapNodes.size()));
            } while(!nextActivity.isType(getOkMapNodeTypes()));
            TimetableNode nextNode = new TimetableNode(nextActivity, startTime + 0.2 + (i*defDuration));
            timeplan.add(nextNode);
        }
        // -------------------------------------------------------------------


        // Currently leave the build were we entered
        TimetableNode endNode = new TimetableNode(start, 19.0);
        timeplan.add(endNode);

        timetable.put(userNum, timeplan);
        return timetable;
    }

    /** The initial location is currently used from the MapBasedMovement **/
    @Override
    public Coord getInitialLocation() {
        assert timetable != null : "Timetable not created before first step!";
        // Selecting the first point from the timetable
        TimetableNode startNode = timetable.get(userNum).get(0);
        this.lastMapNode = startNode.getNode();

        return lastMapNode.getLocation().clone();
    }

    /** This method is used to determine where the node is going next **/
    @Override
    public Path getPath() {
        Path p  = new Path(generateSpeed());
//        System.out.println("Called getPath for " + userNum + " with internal step " + SimClock.getTime() + "(" + nrofHosts + " users)");

        // Select a new classroom if time is already fine, otherwise return current room
        List<TimetableNode> nextNodes = timetable.get(userNum);
        double currentTime = SimClock.getTime();
        MapNode nextNode = null;
        TimetableNode timeNode = null;
        for (int i = 1; i < nextNodes.size(); i++) {
            timeNode = nextNodes.get(i);
            switch (timeNode.canExecute(currentTime)) {
                case 1:
                    // Event still to come, but because of order (can break)
                    break;
                case 0:
                    if (lastMapNode == timeNode.getNode())
                        return null;
                    nextNode = timeNode.getNode();
                    break;
                case -1:
                    // Already happened, continue
                    continue;
                default:
                    System.out.println("Unknown return type!");
                    return null;
            }
        }
        if (nextNode == null)
            return null;

        // The rest is simply from the shortestPathExample
        List<MapNode> nodePath = pathFinder.getShortestPath(lastMapNode, nextNode);
        assert nodePath.size() > 0 : "No path from " + lastMapNode + " to " +
                nextNode + ". The simulation map isn't fully connected";
//        System.out.println(nodePath.size());
        for (MapNode node : nodePath) {
            p.addWaypoint(node.getLocation());
        }
        lastMapNode = nextNode;
        return p;
    }

    @Override
    public TimetableMovement replicate() {return new TimetableMovement(this);}
}
