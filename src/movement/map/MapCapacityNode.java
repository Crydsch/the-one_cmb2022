package movement.map;

import core.Coord;

public class MapCapacityNode extends MapNode {

    private int capacity;

    private String roomName;

    /**
     * Constructor. Creates a map node to a location.
     *
     * @param location The location of the node.
     */
    public MapCapacityNode(Coord location, String roomName, int capacity) {
        super(location);
        this.roomName = roomName;
        this.capacity = capacity;
    }

    public String getRoomName() {
        return roomName;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public boolean equals(Object obj) {
        if (!obj.getClass().equals(MapCapacityNode.class))
            return false;
        return super.equals((MapNode) obj);
    }

    @Override
    public String toString() {
        return roomName + " (" + capacity + ")";
    }
}
