package movement.map;

import core.Coord;
import core.Settings;
import movement.TimetableMovement;

public class TimetableNode {

    // Adding the time information to this node
    private double startTime;
    private double endTime;
    private double stepsPerHour;
    private MapNode node;

    public TimetableNode(MapNode location, double startTime, double stepsPerHour) {
        this.node = location;
        this.startTime = startTime;
        this.endTime = -1.0;
        this.stepsPerHour = stepsPerHour;
    }

    /** Returns 1 if the activity is yet to come.
     * Returns 0 if the activity is starting exactly now
     * Returns -1 if the activty is already due
     */
    public int canExecute(double currentIteration) {
        Settings settings = new Settings(TimetableMovement.TIMETABLE_MOVEMENT_NS);
        int startOfDay = settings.getInt(TimetableMovement.START_DAY_TIME);
        endTime = startTime + (settings.getDouble(TimetableMovement.DEF_DUR) * stepsPerHour);

        // Time in hours
        if (currentIteration < startTime)
            return 1;
        if (startTime < currentIteration && currentIteration < endTime)
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

    @Override
    public String toString() {
        return node.toString() + " at " + startTime;
    }
}
