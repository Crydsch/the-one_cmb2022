package input;

import core.Coord;
import core.Settings;
import util.RoomType;

import java.io.*;
import java.util.*;

public class MapDescriptionReader {

    public static final String TIMETABLE_MOVEMENT_NS = "TimetableMovement";
    public static final String MAPPING_FILE = "roomMapping";
    public static final String LINESTRING = "POINT";
    public static final String ROOM_STRING = "# Room:";

    private final File description;

    public MapDescriptionReader() {

        description = extractFilename();
    }

    private File extractFilename() {
        Settings settings = new Settings(TIMETABLE_MOVEMENT_NS);
        String mapping = settings.getSetting(MAPPING_FILE);
        return new File(mapping);
    }

    private RoomType convertNameToRoomType(String name) {
        name = name.toLowerCase();
        if (name.contains("hs") || name.contains("lecture")) {
            return RoomType.LECTURE_HALL;
        } else if (name.contains("corridor") || name.contains("seminar") || name.contains("room") || name.contains("chair")) {
            return RoomType.SEMINAR_ROOM;
        } else if (name.contains("table")) {
            return RoomType.TABLE;
        } else if (name.contains("coffee") || name.contains("cafe") || name.contains("mensa")) {
            return RoomType.MENSA;
        } else if (name.contains("rechner") || name.contains("pc") || name.contains("rbg")) {
            return RoomType.PC_ROOM;
        }
        return RoomType.LEISURE;
    }

    public HashMap<RoomType, List<Coord>> readDescription(Coord offset) throws IOException {
        BufferedReader fileReader = new BufferedReader(new FileReader(description));
        String line = fileReader.readLine();
        HashMap<RoomType, List<Coord>> mapping = new HashMap<>();
        RoomType currType = RoomType.LEISURE;
        while (line != null) {
            if (line.startsWith(ROOM_STRING)) {
                // We got a mapping
                String roomName = line.substring(ROOM_STRING.length());
                currType = convertNameToRoomType(roomName);
                mapping.computeIfAbsent(currType, k -> new ArrayList<>());
            } else if (line.startsWith(LINESTRING)) {
                String coordString = line.substring(LINESTRING.length()+2, line.length()-2);
                Scanner s = new Scanner(coordString);
                double x,y;
                try {
                    x = s.nextDouble();
                    y = s.nextDouble();
                } catch (RuntimeException e) {
                    throw new IOException("Bad coordinate values: '" + coordString + "'");
                }
                Coord coord = new Coord(x, -y);
                coord.translate(-offset.getX(), -offset.getY());
                System.out.println("Adding " + coord + " with type " + currType);
                mapping.get(currType).add(coord);
            } else {
                throw new IOException("Unknown line\"" + line + "\" in room description!");
            }

            line = fileReader.readLine();
        }
        fileReader.close();

        return mapping;
    }

}
