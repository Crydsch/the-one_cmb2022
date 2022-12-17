package movement;

import core.Coord;
import core.Settings;
import core.SimClock;
import input.MapDescriptionReader;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.SimMap;
import movement.map.TimetableNode;
import util.RoomType;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TimetableMovement extends MapBasedMovement {

    /** Our internal timetable working set **/
    // Adding a dedicated map only for rooms
    private final int nrofHosts;
    private final DijkstraPathFinder pathFinder;
    private static HashMap<Integer, List<TimetableNode>> timetable;
    private static HashMap<RoomType, List<Coord>> roomMapping;
    // Used to differentiate the users
    private final int userNum;
    private static int processedUsers = 0;

    /** Configuration parameters **/
    public static final String TIMETABLE_MOVEMENT_NS = "TimetableMovement";
    public static final String START_MAP_NUM = "nrofStartMap";
    public static final String START_DAY_TIME = "startOfDay";
    public static final String NUM_ACTIVITIES = "defActivities";
    public static final String DEF_DUR = "defActivityDur";
    public static final String ACTIVITY_GAP = "pauseBetweenActivities";
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

    private HashMap<RoomType, List<Coord>> createRoomMapping() {
        if (roomMapping != null) {
            return roomMapping;
        }
        MapDescriptionReader reader = new MapDescriptionReader();
        HashMap<RoomType, List<Coord>> mapping = null;
        try {
            mapping = reader.readDescription(offset);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return mapping;
    }

    // TODO: Fix
    private MapNode selectRandomNodeOfType(Vector<RoomType> types) {
        Vector<Coord> possibleCoords = new Vector<>();
        for (RoomType type : types) {
            List<Coord> coords = roomMapping.get(type);
            if (coords == null)
                continue;
            possibleCoords.addAll(coords);
        }
        if (possibleCoords.isEmpty())
            throw new RuntimeException("No rooms for given types found!");
//        else
//            System.out.println("Possible coords: " + possibleCoords);

        SimMap map = getMap();
        List<MapNode> mapNodes = map.getNodes();
        MapNode nextNode;
        int counter = 0;
        while(true) {
            nextNode = mapNodes.get(rng.nextInt(mapNodes.size()));
            if (possibleCoords.contains(nextNode.getLocation()))
                break;
            if (counter++ > 20000)
                break;
        }
        if (counter > 20000)
            throw new RuntimeException("Failed to find nodes for type " + types);
        return nextNode;
    }

    private HashMap<Integer, List<TimetableNode>> fillTimetable(int user) {
//        System.out.println("Filling timetable for user: " + userNum);
        if (timetable == null)
            timetable = new HashMap<>();
        if (roomMapping == null)
            roomMapping = createRoomMapping();

        Settings settings = new Settings(TIMETABLE_MOVEMENT_NS);
        int numStartMap = settings.getInt(START_MAP_NUM);
        int defDuration = settings.getInt(DEF_DUR);
        double startTime = settings.getDouble(START_DAY_TIME);
        double activityGap = settings.getDouble(ACTIVITY_GAP) * (10.0/6);
        int[] probs = settings.getCsvInts(SPAWN_PROBS, 4);

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

        // TODO: Implement the probabilities for the room selection
        MapNode nextActivity;
        double timeBeforeAct = startTime;
        Vector<RoomType> morningTypes = new Vector<>(Arrays.asList(RoomType.SEMINAR_ROOM, RoomType.LECTURE_HALL));
        Vector<RoomType> lunchTypes = new Vector<>(Arrays.asList(RoomType.MENSA));
        Vector<RoomType> afternoonTypes = new Vector<>(Arrays.asList(RoomType.SEMINAR_ROOM, RoomType.PC_ROOM, RoomType.LECTURE_HALL, RoomType.LIBRARY, RoomType.TABLE));
        for (int i = 0; i < activites; i++) {
            if (timeBeforeAct < 12) {
                // Morning, learn or lecture
                nextActivity = selectRandomNodeOfType(morningTypes);
            } else if (12 < timeBeforeAct && timeBeforeAct < 14) {
                // Eating
                nextActivity = selectRandomNodeOfType(lunchTypes);
            } else {
                // Afternoon, both learning and leisure
                nextActivity = selectRandomNodeOfType(afternoonTypes);
            }
            TimetableNode nextNode = new TimetableNode(nextActivity, startTime + 0.2 + (i*defDuration));
            timeplan.add(nextNode);
            timeBeforeAct += defDuration + activityGap;
        }
        // -------------------------------------------------------------------


        // Currently leave the build were we entered
        TimetableNode endNode = new TimetableNode(start, Math.min(16.0, timeBeforeAct));
        timeplan.add(endNode);

        timetable.put(userNum, timeplan);
        return timetable;
    }

    /** Only selecting the first coord from the timetable **/
    @Override
    public Coord getInitialLocation() {
        assert timetable != null : "Timetable not created before first step!";
        // Selecting the first point from the timetable
        TimetableNode startNode = timetable.get(userNum).get(0);
        this.lastMapNode = startNode.getNode();

        return lastMapNode.getLocation().clone();
    }

    /** Checking if the user can walk anywhere based on timetable and select path **/
    @Override
    public Path getPath() {
        Path p  = new Path(generateSpeed());
//        System.out.println("Called getPath for " + userNum + " with internal step " + SimClock.getTime() + "(" + nrofHosts + " users)");

        // Select a new classroom if timetables allows to, otherwise return current room
        List<TimetableNode> nextNodes = timetable.get(userNum);
        double currentTime = SimClock.getTime();
        MapNode nextNode = null;
        TimetableNode timeNode;
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
