package movement;

import core.Coord;
import core.Settings;
import core.SimError;
import input.WKTMapReader;
import movement.map.SimMap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class TimetableMovement extends MapBasedMovement {

    /** Our internal timetable working set **/
    // Adding a dedicated map only for rooms
    private SimMap rooms = null;
    private int nrofHosts = 0;
    private HashMap<Integer, ArrayList<Coord>> timetable = null;
    private int timeOfDay = 0;

    /** Configuration parameters **/
    public static final String MAP_BASE_MOVEMENT_NS = "TimetableMovement";
    public static final String ROOM_FILE_S = "roomFile";
    public static final String START_DAY_TIME = "startOfDay";
    // Below are some general settings to get more information
    public static final String SCENARIO_NS = "Scenario";
    public static final String GROUP_NS = "Group";
    public static final String HOST_GROUPS = "nrofHostGroups";
    public static  final String NUM_HOSTS = "nrofHosts";

    public TimetableMovement(Settings settings) {
        super(settings);
        rooms = readRooms();
        nrofHosts = getNumOfHosts();
        timetable = createTimetable();
    }

    protected  TimetableMovement(TimetableMovement tm) {
        super(tm);
        this.timeOfDay = tm.timeOfDay;
        this.timetable = tm.timetable;
        this.rooms = tm.rooms;
        this.nrofHosts = tm.nrofHosts;
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

    private SimMap readRooms() {
        SimMap simMap;
        Settings settings = new Settings(MAP_BASE_MOVEMENT_NS);
        WKTMapReader r = new WKTMapReader(true);

        // check out if previously asked map was asked again
        if (rooms != null) {
            // Does not change the rooms
            return rooms;
        }

        try {
            String pathFile = settings.getSetting(ROOM_FILE_S);

            // Adding our rooms to the existing nodes in the reader
            r.addPaths(new File(pathFile), super.nrofMapFilesRead);
        } catch (IOException e) {
            throw new SimError(e.toString(),e);
        }

        simMap = r.getMap();
        super.checkMapConnectedness(simMap.getNodes());
        // mirrors the map (y' = -y) and moves its upper left corner to origo
        simMap.mirror();
        Coord offset = simMap.getMinBound().clone();
        simMap.translate(-offset.getX(), -offset.getY());
        super.checkCoordValidity(simMap.getNodes());

        return simMap;
    }

    /** The initial location is currently used from the MapBasedMovement **/

    /** This method is used to determine where the node is going next **/
    @Override
    public Path getPath() {
        //Path p  = new Path(generateSpeed());

        return null;
    }

    @Override
    public TimetableMovement replicate() {return new TimetableMovement(this);}
}
