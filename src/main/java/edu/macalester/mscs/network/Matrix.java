package edu.macalester.mscs.network;

import edu.macalester.mscs.utils.FileUtils;
import edu.macalester.mscs.utils.Logger;
import edu.macalester.mscs.utils.WordUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Consumer;

/**
 * @author Ari Weiland
 */
public class Matrix {

    private String[] characters;
    private final Map<String, Integer> nameIndices;
    private int[][] matrix;
    private final List<Encounter> encounters;

    private boolean isModifiable = true;

    /**
     * Initializes an empty matrix
     * @param characters
     * @param nameIndices
     */
    public Matrix(String[] characters, Map<String, Integer> nameIndices) {
        this.characters = characters;
        this.nameIndices = nameIndices;
        this.matrix = new int[size()][size()];
        this.encounters = new ArrayList<>();
    }

    /**
     * Initializes an empty matrix
     * @param characters
     * @param nameIndices
     */
    public Matrix(List<String> characters, Map<String, Integer> nameIndices) {
        this(characters.toArray(new String[characters.size()]), nameIndices);
    }

    /**
     * Initializes a matrix and calls build(text, radius)
     * @param characters
     * @param nameIndices
     * @param text
     * @param radius
     */
    public Matrix(String[] characters, Map<String, Integer> nameIndices, String text, int radius) {
        this(characters, nameIndices);
        build(text, radius);
    }

    /**
     * Initializes a matrix and calls build(text, radius)
     * @param characters
     * @param nameIndices
     * @param text
     * @param radius
     */
    public Matrix(List<String> characters, Map<String, Integer> nameIndices, String text, int radius) {
        this(characters, nameIndices);
        build(text, radius);
    }

    /**
     * Builds the matrix from the text, given a maximum word radius for adjacency
     *
     * @param text
     * @param radius
     */
    public void build(String text, int radius) {
        StringBuilder search = new StringBuilder();
        FixedQueue<String> nameQueue = new FixedQueue<>(radius);
        Queue<Name> backup = new LinkedList<>();

        search.append(text.charAt(0));
        for (int i = 1; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            Name primary = new Name();
            boolean wordEnd = !WordUtils.isWordCharacter(c) && WordUtils.isWordCharacter(text.charAt(i - 1));

            if (i % 100000 == 0) {
                System.out.println("Processing char " + i + " out of " + text.length());
            }

            if (wordEnd) {
                String context = search.toString();
                for (String name : nameIndices.keySet()) {
                    // check the ends with condition, and choose the longest option
                    if (WordUtils.endsWithWord(context, name) && name.length() > primary.name.length()) {
                        primary = new Name(name, i, context);
                    }
                }
                // drain the backup queue into the name queue
                // if backup.size() > 7, nothing is happening and it can be drained
                if (primary.isValid() || backup.size() > 7) {
                    while (!backup.isEmpty()) {
                        Name last = backup.poll();
                        // if last is valid, part of primary, and not primary, do NOT tally neighbors
                        if (!last.isValid() || primary.name.contains(last.name) && !primary.name.equals(last.name)) {
                            nameQueue.push("");
                        } else { // else tally neighbors
                            tallyNeighbors(last, nameQueue);
                            nameQueue.push(last.name);
                        }
                    }
                }
                // update the queues
                // no need to add non-names to an empty backup
                if (backup.isEmpty() && !primary.isValid()) {
                    nameQueue.push("");
                } else {
                    backup.add(primary);
                }
            }
            // update the search string
            search.append(c);
            // cut the string to size
            if (StringUtils.countMatches(search, ' ') > radius + 2) {
                search = new StringBuilder(search.substring(search.indexOf(" ") + 1));
            }
        }
    }

    private void tallyNeighbors(Name primary, FixedQueue<String> nameQueue) {
        int index1 = nameIndices.get(primary.name);
        Map<Integer, String> secondaries = new HashMap<>();
        // use a map to avoid duplicate names on the left
        // ie. "...Mirri Maz Duur said, pointing to the altar, a massive blue-veined stone carved with images of shepherds and their flocks. Khal Drogo..."
        for (String secondary : nameQueue) {
            if (!secondary.isEmpty()) {
                int index2 = nameIndices.get(secondary);
                if (index1 == index2) {
                    secondaries.clear();
                    // clear so we don't pick things up multiple times for duplicate names on the right
                    // ie. "...Dany asked her. 'I am named Mirri Maz Duur'..."
                } else {
                    secondaries.put(index2, secondary);
                }
            }
        }
        for (String secondary : secondaries.values()) {
            addEncounter(primary.name, secondary, primary.index, primary.context);
        }
    }

    public String[] getCharacters() {
        return characters;
    }

    public int[][] getMatrix() {
        return matrix;
    }

    /**
     * Returns a sorted list of every encounter included in this Matrix
     * @return
     */
    public List<Encounter> getEncounterList() {
        Collections.sort(encounters);
        return encounters;
    }

    /**
     * Returns a sorted list of every encounter included in this Matrix involving the specified character
     * @param name
     * @return
     */
    public List<Encounter> getEncounterList(String name) {
        List<Encounter> encounterList = new ArrayList<>();
        for (Encounter encounter : encounters) {
            if (name.equals(encounter.character1) || name.equals(encounter.character2)
                    || name.equals(encounter.name1) || name.equals(encounter.name2)) {
                encounterList.add(encounter);
            }
        }
        Collections.sort(encounterList);
        return encounterList;
    }

    /**
     * Returns the size of the matrix. This corresponds to the number of characters,
     * which is also the height and width of the matrix.
     * @return
     */
    public int size() {
        return characters.length;
    }

    /**
     * Adds an encounter to the matrix and encounter list, unless the two names correspond to the same person.
     * Does no other checks, such as to avoid duplicate encounters at the same position.
     * @param name1
     * @param name2
     * @param position
     */
    public void addEncounter(String name1, String name2, int position) {
        addEncounter(name1, name2, position, "");
    }

    /**
     * Adds an encounter to the matrix and encounter list, unless the two names correspond to the same person.
     * Does no other checks, such as to avoid duplicate encounters at the same position.
     * @param name1
     * @param name2
     * @param position
     */
    public void addEncounter(String name1, String name2, int position, String context) {
        if (!isModifiable) {
            throw new IllegalStateException("This matrix has been cleaned and can no longer be modified.");
        }

        // don't add fake links when one name is a substring of the other
        if (!name1.contains(name2) && !name2.contains(name1)) {
            int index1 = nameIndices.get(name1);
            int index2 = nameIndices.get(name2);
            if (index1 != index2) {
                matrix[index1][index2]++;
                matrix[index2][index1]++;
                encounters.add(new Encounter(characters[index1], name1, characters[index2], name2, position, context));
            }
        }
    }

    /**
     * Removes any connections whose strength is below the specified noise threshold
     * Also removes any characters who have no connections
     * @param noise
     * @return
     */
    public Logger cleanNoise(int noise) {
        Logger logger = new Logger();
        logger.log("Removing noisy connections:");
        // clean noise
        for (int i=0; i<size(); i++) {
            for (int j=0; j<size(); j++) {
                if (matrix[i][j] < noise && matrix[i][j] > 0) {
                    // remove really weak connections
                    logger.log(characters[i] + ", " + characters[j] + ", " + matrix[i][j]);
                    matrix[i][j] = 0;
                }
            }
            // make sure the diagonal is 0
            matrix[i][i] = 0;
        }
        // get people without connections
        Set<Integer> loners = new HashSet<>();
        for (int i=0; i<size(); i++) {
            boolean loner = true;
            for (int j=0; j<size() && loner; j++) {
                if (matrix[i][j] > 0) {
                    loner = false;
                }
            }
            if (loner) {
                loners.add(i);
            }
        }
        logger.log("Removing loners: " + removeRows(loners));
        return logger;
    }

    /**
     * Removes any characters not connected to the network containing
     * the character whose index is 0
     * @return
     */
    public Logger cleanFloaters() {
        return cleanFloaters(0);
    }

    /**
     * Removes any characters not connected to the network containing
     * the character whose index is entryPoint
     * @param entryPoint
     * @return
     */
    public Logger cleanFloaters(int entryPoint) {
        // get people without connections
        Set<Integer> floaters = new HashSet<>();
        for (int i = 0; i < size(); i++) {
            floaters.add(i);
        }
        Queue<Integer> bfs = new ArrayDeque<>();
        bfs.add(entryPoint);
        floaters.remove(entryPoint);
        while (!bfs.isEmpty()) {
            int index = bfs.poll();
            for (int i=0; i<size(); i++) {
                if (matrix[index][i] > 0 && floaters.contains(i)) {
                    bfs.add(i);
                    floaters.remove(i);
                }
            }
        }
        Logger logger = new Logger();
        logger.log("Removing floating characters: " + removeRows(floaters));
        return logger;
    }

    /**
     * Iteratively removes characters with only one connection until the map stabilizes.
     * @return
     */
    public Logger cleanSingletons() {
        Logger logger = new Logger();
        // get people with only one connection
        Set<Integer> singletons = new HashSet<>();
        do {
            singletons.clear();
            for (int i = 0; i < size(); i++) {
                int neighbors = 0;
                for (int j = 0; j < size(); j++) {
                    if (matrix[i][j] > 0) {
                        neighbors++;
                    }
                }
                if (neighbors < 2) {
                    singletons.add(i);
                }
            }
            logger.log("Removing singletons: " + removeRows(singletons));
        } while (!singletons.isEmpty());
        return logger;
    }

    /**
     * Iteratively removes characters with only one connection for a fixed number of iterations.
     * @return
     */
    public Logger cleanSingletons(int iterations) {
        Logger logger = new Logger();
        // get people with only one connection
        for (int n=0; n<iterations; n++) {
            Set<Integer> singletons = new HashSet<>();
            for (int i=0; i<size(); i++) {
                int neighbors = 0;
                for (int j=0; j<size(); j++) {
                    if (matrix[i][j] > 0) {
                        neighbors++;
                    }
                }
                if (neighbors < 2) {
                    singletons.add(i);
                }
            }
            logger.log("Removing singletons: " + removeRows(singletons));
        }
        return logger;
    }

    /**
     * Converts the Matrix to CSV lines of a matrix.
     * The first line is the name headers.
     * @return
     */
    public Logger toMatrixCsvLog() {
        Logger logger = new Logger();
        logger.log(cleanArrayString(Arrays.toString(characters)));
        for (int[] row : matrix) {
            logger.log(cleanArrayString(Arrays.toString(row)));
        }
        return logger;
    }



    /**
     * Converts the Matrix to a JSON matrix, using the names in the given order.
     * The first line is the name headers.
     * @return
     */
    public Logger toMatrixJsonLog(String[] orderedCharacters) {
        Logger logger = new Logger();
        String[] orderedChars = (orderedCharacters == null) ? getCharacters() : orderedCharacters;
        int length = orderedChars.length;
        int[][] newMatrix = new int[length][length];

        for (int i = 0; i < length; i++) {
            String name1 = orderedChars[i];
            int name1index = ArrayUtils.indexOf(characters, name1);
            for (int j = i + 1; j < length; j++) {
                String name2 = orderedChars[j];
                int name2index = ArrayUtils.indexOf(characters, name2);

               System.out.println(i + " name1=" + name1 + " and " + j + " name2=" + name2 +
                " are at " + name1index + " and " + name2index);

                newMatrix[i][j] = matrix[name1index][name2index];
                newMatrix[j][i] = matrix[name1index][name2index];
            }
        }

        logger.log("[");
        for (int i=0; i < newMatrix.length; i++) {
            if (i == newMatrix.length - 1) {
                logger.log(Arrays.toString(newMatrix[i]));
            } else {
                logger.log(Arrays.toString(newMatrix[i]) + ",");
            }
        }
        logger.log("]");
        return logger;
    }

    /**
     * Converts the matrix to CSV lines of a list of edges, for import into Gephi.
     * The header is "Source,Target,Weight,Type", where the type is always undirected.
     * This is equivalent to calling
     * toEdgeListCsvLog("Source,Target,Weight,Type", "#C1,#C2,#W,undirected")
     * @return
     */
    public Logger toEdgeListCsvLog() {
        return toEdgeListCsvLog("Source,Target,Weight,Type", "#C1,#C2,#W,undirected");
    }

    /**
     * Converts the matrix to CSV lines of a list of edges, with customizable parameters.
     * The defaultValues parameter can contain default string values, or data references.
     * Use '#C1' to reference the first character in an edge.
     * Use '#C2' to reference the second character in an edge.
     * Use '#W' to reference the weight of an edge.
     * Make sure to include quotes as necessary, though they must not be included around
     * the three special references.
     * @param header
     * @param defaultValue
     * @return
     */
    public Logger toEdgeListCsvLog(String header, String defaultValue) {
        Logger logger = new Logger();
        logger.log(header);
        for (int i=0; i<size(); i++) {
            for (int j=i+1; j<size(); j++) {
                if (matrix[i][j] > 0) {
                    String line = defaultValue
                            .replace("#C1", "\"" + characters[i] + "\"")
                            .replace("#C2", "\"" + characters[j] + "\"")
                            .replace("#W", Double.toString(matrix[i][j]));
                    logger.log(line);
                }
            }
        }
        return logger;
    }

    public Logger toNodeListCsvLog(String charDataFileName) {
        if (charDataFileName == null) {
            Logger logger = new Logger();

            logger.log("Error: character data file name is null");

            return logger;
        } else {
            return toNodeListCsvLog("Id,Label,Allegiance,Royal House,Culture", "#ID,#LA,#AL,#RH,#C", charDataFileName);
        }
    }

    private Logger toNodeListCsvLog(String header, String defaultValue, String charDataFileName) {
        Logger logger = new Logger();
        logger.log(header);

        List<String> charLines = FileUtils.readFile(charDataFileName);

        Map<String, String[]> fullCharMap = new HashMap<>();

        System.out.println(charLines.get(0));

        for (int i = 1; i < charLines.size(); i++) {
            String[] charLine = charLines.get(i).split(",");
            System.out.println("Adding character:" + charLine[0]);
            fullCharMap.put(charLine[0], charLine);
        }

        for (String character : getCharacters()) {

            System.out.println("Handling character node " + character);

            String[] charData = fullCharMap.get(character);

            //System.out.println("\tcharData length="+charData.length);

            if (charData !=null) {

                String id = charData[0];
                String label = charData[1];
                String allegiance = (charData.length > 5) ? charData[5] : "";
                String royalHouse = (charData.length > 6) ? charData[6] : "";
                String culture = (charData.length > 7) ? charData[7] : "";

                String line = defaultValue
                        .replace("#ID", "\"" + id + "\"")
                        .replace("#LA", "\"" + label + "\"")
                        .replace("#AL", "\"" + allegiance + "\"")
                        .replace("#RH", "\"" + royalHouse + "\"")
                        .replace("#C", "\"" + culture + "\"");
                logger.log(line);
            } else {
                logger.log(character + ",missing");
            }
        }

        return logger;
    }



    private static String cleanArrayString(String arrayString) {
        return StringUtils.strip(arrayString, "[]").replaceAll(" ", "");
    }

    private String removeRows(Set<Integer> removed) {
        isModifiable = false;
        StringBuilder sb = new StringBuilder();
        if (!removed.isEmpty()) {
            int newLength = size() - removed.size();
            String[] cleanCharacters = new String[newLength];
            int[][] cleanMatrix = new int[newLength][newLength];
            int row=0;
            for(int i=0; i < size(); i++){
                if (!removed.contains(i)) {
                    cleanCharacters[row] = characters[i]; // clean characters
                    int col = 0;
                    for (int j=0; j<size(); j++) { // clean matrix
                        if (!removed.contains(j)) {
                            cleanMatrix[row][col] = matrix[i][j];
                            col++;
                        }
                    }
                    row++;
                } else {
                    sb.append(characters[i]).append(" ");
                }
            }
            characters = cleanCharacters;
            matrix = cleanMatrix;
        }
        return sb.toString();
    }

    private static class Name {
        final String name;
        final int index;
        final String context;

        public Name() {
            this("", -1, "");
        }

        public Name(String name, int index, String context) {
            this.name = name;
            this.index = index;
            this.context = context;
        }

        public boolean isValid() {
            return index > -1;
        }
    }

    private static class FixedQueue<T> implements Iterable<T> {

        private final int maxLength;
        private final Queue<T> queue;

        public FixedQueue(int maxLength) {
            this.maxLength = maxLength;
            this.queue = new LinkedList<>();
        }

        public T push(T t) {
            queue.add(t);
            if (queue.size() > maxLength) {
                return queue.poll();
            }
            return null;
        }

        public T pop() {
            return queue.poll();
        }

        public int size() {
            return queue.size();
        }

        @Override
        public Iterator<T> iterator() {
            return queue.iterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            queue.forEach(action);
        }

        @Override
        public Spliterator<T> spliterator() {
            return queue.spliterator();
        }
    }
}
