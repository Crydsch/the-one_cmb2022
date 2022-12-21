package movement;

import core.Coord;
import core.Settings;
import core.SimClock;
import core.SimScenario;
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
    private static HashMap<RoomType, List<Map.Entry<Coord, Integer>>> roomMapping;
    private static HashMap<Double, List<Map.Entry<Coord, Integer>>> peoplePerRoom;
    // Used to differentiate the users
    private final int userNum;
    private boolean isActive;
    private static int processedUsers;

    /** Configuration parameters **/
    public static final String TIMETABLE_MOVEMENT_NS = "TimetableMovement";
    public static final String START_MAP_NUM = "nrofStartMap";
    public static final String START_DAY_TIME = "startOfDay";
    public static final String END_DAY_TIME = "endOfDay";
    public static final String NUM_ACTIVITIES = "defActivities";
    public static final String DEF_DUR = "defActivityDur";
    public static final String ACTIVITY_GAP = "pauseBetweenActivities";
    public static final String SPAWN_PROBS = "spawnProbability";
    public static final String ACT_PROBS = "activityProbability";
    public static final String VERBOSE = "verbose";

    public TimetableMovement(Settings settings) {
        super(settings);
        nrofHosts = getNumOfHosts();
        userNum = processedUsers++;
        isActive = true;
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
        this.isActive = true;
        fillTimetable(this.userNum);
//        System.out.println("Timetable contains: " + timetable.get(userNum).size() + " elems");
    }

    private int getNumOfHosts() {
        Settings settings = new Settings(SimScenario.SCENARIO_NS);
        int hostGroups = settings.getInt(SimScenario.NROF_GROUPS_S);
        int numHosts = 0;
        for (int i = 1; i <= hostGroups; i++) {
            Settings groupSetting = new Settings(SimScenario.GROUP_NS + i);
            numHosts += groupSetting.getInt(SimScenario.NROF_HOSTS_S);
        }
        return numHosts;
    }

    private HashMap<RoomType, List<Map.Entry<Coord, Integer>>> createRoomMapping() {
        if (roomMapping != null) {
            return roomMapping;
        }
        MapDescriptionReader reader = new MapDescriptionReader();
        HashMap<RoomType, List<Map.Entry<Coord, Integer>>> mapping = null;
        try {
            mapping = reader.readDescription(offset);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return mapping;
    }

    /** Selecting a random node from rooms of the given type.
     * @param types The types that should be used as rooms
     * @param startingTime The point in time for which we want to select a room. Allows to include the room capacity.
     * @return The next map node
     */
    private MapNode selectRandomNodeOfType(Vector<RoomType> types, double startingTime) {
        Vector<Coord> possibleCoords = new Vector<>();
        Vector<Integer> roomCapacities = new Vector<>();
        for (RoomType type : types) {
            List<Map.Entry<Coord, Integer>> coords = roomMapping.get(type);
            if (coords == null)
                continue;
            possibleCoords.addAll(coords.stream().map(Map.Entry::getKey).collect(Collectors.toList()));
            roomCapacities.addAll(coords.stream().map(Map.Entry::getValue).collect(Collectors.toList()));
        }
        if (possibleCoords.isEmpty())
            throw new RuntimeException("No rooms for given types found!");
//        else
//            System.out.println("Possible coords: " + possibleCoords);

        int totalCapacity = 0;
        for(Integer capacity : roomCapacities) {
            totalCapacity += capacity;
        }
//        System.out.println("The total capacity of the selected room type: " + types + " is " + totalCapacity);

        // Try
        // MapNode fromNode = getMap().getNodeByCoord(from);
        SimMap map = getMap();
        List<MapNode> mapNodes = map.getNodes();
        List<Map.Entry<Coord, Integer>> peoplesInRoom = peoplePerRoom.get(startingTime);
        if (peoplesInRoom == null)
            peoplesInRoom = new ArrayList<Map.Entry<Coord, Integer>>();
        MapNode nextNode;
        int counter = 0;
        outer: while(true) {
            nextNode = mapNodes.get(rng.nextInt(mapNodes.size()));
            if (possibleCoords.contains(nextNode.getLocation())) {
                // Update the room capacity
                int index = possibleCoords.indexOf(nextNode.getLocation());
                for (int j = 0; j < peoplesInRoom.size(); ++j) {
                    Map.Entry<Coord, Integer> roomMapping = peoplesInRoom.get(j);
                    if (roomMapping.getKey().equals(nextNode.getLocation())) {
                        if (roomMapping.getValue() > 0) {
                            peoplesInRoom.get(j).setValue(roomMapping.getValue()-1);
                            peoplePerRoom.put(startingTime, peoplesInRoom);
                            break outer;
                        }
                        continue outer;
                    }
                }
                // No entry is found
                peoplesInRoom.add(new AbstractMap.SimpleEntry<>(possibleCoords.get(index), roomCapacities.get(index)));
            }
            if (counter++ > 20000) // Hard unlucky if this fails incorrectly
                throw new RuntimeException("Failed to find nodes for type " + types + " (maybe because all rooms full (" + totalCapacity + "))");
        }
        return nextNode;
    }

    private HashMap<Integer, List<TimetableNode>> fillTimetable(int user) {
//        System.out.println("Filling timetable for user: " + userNum);
        if (timetable == null)
            timetable = new HashMap<>();
        if (roomMapping == null)
            roomMapping = createRoomMapping();
        if (peoplePerRoom == null)
            peoplePerRoom = new HashMap<>();

        Settings settings = new Settings(TIMETABLE_MOVEMENT_NS);
        Settings scenarioSettings = new Settings(SimScenario.SCENARIO_NS);
        double startTime = settings.getDouble(START_DAY_TIME);
        double endTime = settings.getDouble(END_DAY_TIME);
        double dayDuration = endTime - startTime;
        double steps = scenarioSettings.getDouble(SimScenario.END_TIME_S);
        double stepsPerHour = Math.floor(steps / dayDuration);
        double defDuration = settings.getDouble(DEF_DUR) * stepsPerHour;
        double activityGap = settings.getDouble(ACTIVITY_GAP) * stepsPerHour;

        int numStartMap = settings.getInt(START_MAP_NUM);
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

        TimetableNode startNode = new TimetableNode(start, 0., stepsPerHour);
        timeplan.add(startNode);

        // -------------------------------------------------------------------
        // Select daily activities
        // Currently randomly selected classroom
        int activites = settings.getInt(NUM_ACTIVITIES);
        double[] actProbs = settings.getCsvDoubles(ACT_PROBS, 4);
        hostCounter = 0;
        for (int i = 0; i < actProbs.length; i++) {
            actProbs[i] = actProbs[i] / (double)100;
        }

        // Implementing a capacity limit per room, this way the probabilities should be more or less
        // automatically correctly (i guess...)
        MapNode nextActivity;
        double timeBeforeAct = 1.0; // Starting with the first iteration
        Vector<RoomType> morningTypes = new Vector<>(Arrays.asList(RoomType.SEMINAR_ROOM, RoomType.LECTURE_HALL, RoomType.PC_ROOM, RoomType.LIBRARY));
        Vector<RoomType> lunchTypes = new Vector<>(Arrays.asList(RoomType.MENSA, RoomType.LEISURE, RoomType.TABLE));
        Vector<RoomType> afternoonTypes = new Vector<>(Arrays.asList(RoomType.SEMINAR_ROOM, RoomType.PC_ROOM, RoomType.LECTURE_HALL, RoomType.LIBRARY, RoomType.TABLE, RoomType.LEISURE));
        for (int i = 0; i < activites; i++) {
            if (timeBeforeAct < 12 * stepsPerHour) {
                // Morning, learn or lecture
                nextActivity = selectRandomNodeOfType(morningTypes, timeBeforeAct);
            } else if (12 * stepsPerHour < timeBeforeAct && timeBeforeAct < 14 * stepsPerHour) {
                // Eating
                nextActivity = selectRandomNodeOfType(lunchTypes, timeBeforeAct);
            } else {
                // Afternoon, both learning and leisure
                nextActivity = selectRandomNodeOfType(afternoonTypes, timeBeforeAct);
            }
            TimetableNode nextNode = new TimetableNode(nextActivity, timeBeforeAct, stepsPerHour);
            timeplan.add(nextNode);
            timeBeforeAct += defDuration + activityGap;
        }
        // -------------------------------------------------------------------


        // Currently leave the build were we entered
        TimetableNode endNode = new TimetableNode(start, Math.min(endTime * stepsPerHour, timeBeforeAct), stepsPerHour);
        timeplan.add(endNode);
//        System.out.println("Timetable for " + userNum + " has " + timeplan.size() + " entries");
        timetable.put(userNum, timeplan);

        boolean verbose = settings.getBoolean(VERBOSE);
        if (verbose)
            if (userNum + 1 == nrofHosts) {
                printTimetable();
                printRoomOccupation();
            }

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
        boolean allHappend = false;
        for (int i = 1; i < nextNodes.size(); i++) {
            timeNode = nextNodes.get(i);
            switch (timeNode.canExecute(currentTime)) {
                case 1:
                    // Event still to come, but because of order (can break)
                    break;
                case 0:
                    if (lastMapNode == timeNode.getNode())
                        return null;
//                    System.out.println("Selecting " + i + " event for " + userNum);
                    nextNode = timeNode.getNode();
                    break;
                case -1:
                    // Already happened, continue
                    if (i + 1 == nextNodes.size())
                        allHappend = true;
                    continue;
                default:
                    System.out.println("Unknown return type!");
                    return null;
            }
        }
        if (allHappend && lastMapNode.equals(nextNodes.get(nextNodes.size()-1)))
            isActive = false;
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

    private static void printRoomOccupation() {
        System.out.println("----------------------------------------------------------------------------");
        System.out.println("Room Occupation");
        System.out.println("----------------------------------------------------------------------------");
        if (peoplePerRoom == null) {
            return;
        }
        peoplePerRoom.forEach((e, v) -> {
            StringBuilder list = new StringBuilder();
            for (Map.Entry<Coord, Integer> node : v) {
                list.append(node.getKey()).append(" ").append(node.getValue()).append(", ");
            }
            String nodes = list.substring(0, list.length()-2);
            System.out.println("| " + e + " | " + nodes + " |");
        });
        System.out.println("----------------------------------------------------------------------------");
    }

    private static void printTimetable() {
        System.out.println("----------------------------------------------------------------------------");
        System.out.println("Timetable");
        System.out.println("----------------------------------------------------------------------------");
        if (timetable == null) {
            return;
        }
        timetable.forEach((e, v) -> {
            StringBuilder list = new StringBuilder();
            for (TimetableNode node : v) {
                list.append(node.toString()).append(", ");
            }
            String nodes = list.substring(0, list.length()-2);
            System.out.println("| " + e + " | " + nodes + " |");
        });
        System.out.println("----------------------------------------------------------------------------");
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public TimetableMovement replicate() {return new TimetableMovement(this);}

    public static void reset() {
        timetable = new HashMap<>();
        peoplePerRoom = new HashMap<>();
        processedUsers = 0;
    }
}
