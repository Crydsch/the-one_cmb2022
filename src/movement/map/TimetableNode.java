package movement.map;

import core.Coord;
import core.Settings;

public class TimetableNode {

    public static final String MAP_BASE_MOVEMENT_NS = "TimetableMovement";
    public static final String SEC_PER_ITER = "secondsPer1Iter";
    public static final String START_DAY_TIME = "startOfDay";
    public static final String DEF_DUR = "defActivityDur";

    // Adding the time information to this node
    private double startTime;
    private double endTime;
    private MapNode node;

    public TimetableNode(MapNode location, double startTime) {
        this.node = location;
        this.startTime = startTime;
        this.endTime = -1.0;
    }

    /** Returns 1 if the activity is yet to come.
     * Returns 0 if the activity is starting exactly now
     * Returns -1 if the activty is already due
     */
    public int canExecute(double currentTime) {
        Settings settings = new Settings(MAP_BASE_MOVEMENT_NS);
        double secPerIter = settings.getDouble(SEC_PER_ITER);
        int startOfDay = settings.getInt(START_DAY_TIME);
        endTime = startTime + settings.getDouble(DEF_DUR);

        // Time in hours
        double time = (double)startOfDay + ((currentTime * secPerIter) / (60 * 60));
        if (time < startTime)
            return 1;
        if (startTime < time && time < endTime)
            return 0;
        // Else case
        return -1;
    }

    public double getStartTime() {
        return startTime;
    }

    public MapNode getNode() {
        return node;
    }
}
