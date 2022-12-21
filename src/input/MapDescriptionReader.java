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
    public static final String DESCRIPTION_S = "#";

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

    private Vector<String> parseDescription(String line) {
        String lineTrimmed = line.trim().toLowerCase();
        String[] splitted = lineTrimmed.split(";");
        if (splitted.length != 2)
            throw new RuntimeException("Description '" + lineTrimmed + "' in unknown format!");

//        System.out.println(Arrays.toString(splitted));
        Vector<String> description = new Vector<>(2);
        for (String desc : splitted) {
            String trimmed = desc.trim();
            String cleaned = trimmed.replaceAll(" ", "");
            String[] split = cleaned.split(":");
//            System.out.println(Arrays.toString(split));
            if (split.length != 2) {
                throw new RuntimeException("The description '" + desc + "' does not match the expected pattern");
            }
            if (split[0].contains("room")) {
                description.add(split[1]);
            }
            if (split[0].contains("capacity")) {
                description.add(split[1]);
            }
        }
        return description;
    }

    public HashMap<RoomType, List<Map.Entry<Coord, Integer>>> readDescription(Coord offset) throws IOException {
        BufferedReader fileReader = new BufferedReader(new FileReader(description));
        String line = fileReader.readLine();
        HashMap<RoomType, List<Map.Entry<Coord, Integer>>> mapping = new HashMap<>();
        RoomType currType = RoomType.LEISURE;
        Integer capacity = 0;
        while (line != null) {
            if (line.startsWith(DESCRIPTION_S)) {
                // We got a mapping
                Vector<String> roomDescription = parseDescription(line);
                currType = convertNameToRoomType(roomDescription.get(0));
                mapping.computeIfAbsent(currType, k -> new ArrayList<>());
                capacity = Integer.parseInt(roomDescription.get(1));
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
                Map.Entry<Coord, Integer> entry = new AbstractMap.SimpleEntry<>(coord, capacity);
                mapping.get(currType).add(entry);
            } else {
                throw new IOException("Unknown line\"" + line + "\" in room description!");
            }

            line = fileReader.readLine();
        }
        fileReader.close();

        return mapping;
    }

}
