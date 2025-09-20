//mvn exec:java -Dexec.mainClass="com.example.Gabor"
// powershell: mvn clean compile assembly:single ; java -jar C:\Users\erak5\Downloads\GaborLLM2\GaborLLM2\demo\target\demo-1.0-SNAPSHOT-jar-with-dependencies.jar
// everything else: mvn clean compile assembly:single && java -jar C:\Users\erak5\Downloads\GaborLLM2\GaborLLM2\demo\target\demo-1.0-SNAPSHOT-jar-with-dependencies.jar

package com.example;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpException;

import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Tool;


public class Gabor {
    static HashMap<String, String[]> adjacentPoints = new HashMap<>();
    static HashMap<String, String[][]> mills = new HashMap<>();
    //1 represents blue piece on the board, 2 is orange, 0 is empty
    static int[][] currentBoard = new int[7][7];
    // counts[0] = blue in-hand piece count
    // counts[1] = orange in-hand piece count
    // counts[2] = blue on-board piece count
    // counts[3] = orange on-board piece count
    // counts[4] = blue discarded piece count
    // counts[5] = orange discarded piece count
    static int[] currentCounts = {10, 10, 0, 0, 0, 0};
    static String mePlayer = "orange";
    static String[] points = {"a1", "a4", "a7", "b2", "b4", "b6", "c3", "c4", "c5", "d1", "d2", "d3", "d5", "d6", "d7", "e3", "e4", "e5", "f2", "f4", "f6", "g1", "g4", "g7"};
    static long startTime;
    final static long cutoffTime = 4900;
    final static int stalemateThreshold = 20;
    static int moveSinceLastActivity = 0;
    static String errorMessage = "";

    public static boolean isValidPoint(int x, int y) {
        String point = indexToMove(new int[] {x, y});
        for (int i = 0; i < points.length; i++) {
            if (points[i].equals(point)) {
                return true;
            }
        }
        return false;
    }

    public static String createPrompt(int[][] board, int[] counts){
        String boardString = "";
        boardString += ".      1 2 3 4 5 6 7\n";
        for (int i = 0; i < 7; i++) {
            boardString += "Row " + "abcdefg".charAt(i) + ": ";
            for (int j = 0; j < 7; j++) {
                boardString += (board[i][j] == 0 ? (isValidPoint(i, j) ? "." : " ") : board[i][j]) + " ";
            }
            boardString += "\n";
        }
        //Counts
        String countsString  = "\n";
        countsString += 
        "Player 1 pieces in hand: " + counts[0] + "\n" + 
        "Player 2 pieces in hand: " + counts[1] + "\n" +
        "Player 1 pieces on the board: " + counts[2] + "\n" + 
        "Player 2 pieces on the board: " + counts[3] + "\n" +
        "Player 1 pieces discarded: " + counts[4] + "\n" +
        "Player 2 pieces discarded: " + counts[5] + "\n"; 

        String pleaseString = "\nPlease provide the optimal legal move for Player " + (mePlayer.equals("blue") ? '1' : '2') + " given these conditions.";
        
        return boardString + countsString + pleaseString;
    }

    public static void printBoard(int[][] board, int[] counts){
        System.out.println(". 1 2 3 4 5 6 7");
        for (int i = 0; i < 7; i++) {
            System.out.print("ABCDEFG".charAt(i) + " ");
            for (int j = 0; j < 7; j++) {
                System.out.print((board[i][j] == 0 ? (isValidPoint(i, j) ? "." : " ") : board[i][j]) + " ");
            }
            System.out.println();
        }
        System.out.print("COUNTS: " );
        for (int i = 0; i < 6; i++) {
            System.out.print(counts[i] + " ");
        }
        System.out.println();
    }

    public static void initializeAdjacentPoints() { 
        adjacentPoints.put("a1", new String[] {"a4", "d1"});
        adjacentPoints.put("a4", new String[] {"a1", "a7", "b4"});
        adjacentPoints.put("a7", new String[] {"a4", "d7"});

        adjacentPoints.put("b2", new String[] {"b4", "d2"});
        adjacentPoints.put("b4", new String[] {"a4", "b2", "b6", "c4"});
        adjacentPoints.put("b6", new String[] {"b4", "d6"});

        adjacentPoints.put("c3", new String[] {"c4", "d3"});
        adjacentPoints.put("c4", new String[] {"b4", "c3", "c5"});
        adjacentPoints.put("c5", new String[] {"c4", "d5"});

        adjacentPoints.put("d1", new String[] {"a1", "d2", "g1"});
        adjacentPoints.put("d2", new String[] {"b2", "d1", "d3", "f2"});
        adjacentPoints.put("d3", new String[] {"c3", "d2", "e3"});

        adjacentPoints.put("d7", new String[] {"a7", "d6", "g7"});
        adjacentPoints.put("d6", new String[] {"b6", "d7", "d5", "f6"});
        adjacentPoints.put("d5", new String[] {"c5", "d6", "e5"});

        adjacentPoints.put("e3", new String[] {"e4", "d3"});
        adjacentPoints.put("e4", new String[] {"f4", "e3", "e5"});
        adjacentPoints.put("e5", new String[] {"e4", "d5"});

        adjacentPoints.put("f2", new String[] {"f4", "d2"});
        adjacentPoints.put("f4", new String[] {"g4", "f2", "f6", "e4"});
        adjacentPoints.put("f6", new String[] {"f4", "d6"});

        adjacentPoints.put("g1", new String[] {"g4", "d1"});
        adjacentPoints.put("g4", new String[] {"g1", "g7", "f4"});
        adjacentPoints.put("g7", new String[] {"g4", "d7"});
    }

    public static void initializeMills() {
        mills.put("a1", new String[][] {{"a1", "a4", "a7"}, {"a1", "d1", "g1"}});
        mills.put("a4", new String[][] {{"a1", "a4", "a7"}, {"a4", "b4", "c4"}});
        mills.put("a7", new String[][] {{"a1", "a4", "a7"}, {"a7", "d7", "g7"}});
        mills.put("b2", new String[][] {{"b2", "b4", "b6"}, {"b2", "d2", "f2"}});
        mills.put("b4", new String[][] {{"b2", "b4", "b6"}, {"a4", "b4", "c4"}});
        mills.put("b6", new String[][] {{"b2", "b4", "b6"}, {"b6", "d6", "f6"}});
        mills.put("c3", new String[][] {{"c3", "c4", "c5"}, {"c3", "d3", "e3"}});
        mills.put("c4", new String[][] {{"c3", "c4", "c5"}, {"a4", "b4", "c4"}});
        mills.put("c5", new String[][] {{"c3", "c4", "c5"}, {"c5", "d5", "e5"}});
        mills.put("d1", new String[][] {{"d1", "d2", "d3"}, {"a1", "d1", "g1"}});
        mills.put("d2", new String[][] {{"d1", "d2", "d3"}, {"b2", "d2", "f2"}});
        mills.put("d3", new String[][] {{"d1", "d2", "d3"}, {"c3", "d3", "e3"}});
        mills.put("d7", new String[][] {{"d7", "d6", "d5"}, {"a7", "d7", "g7"}});
        mills.put("d6", new String[][] {{"d7", "d6", "d5"}, {"b6", "d6", "f6"}});
        mills.put("d5", new String[][] {{"d7", "d6", "d5"}, {"c5", "d5", "e5"}});
        mills.put("g1", new String[][] {{"g1", "g4", "g7"}, {"g1", "d1", "a1"}});
        mills.put("g4", new String[][] {{"g1", "g4", "g7"}, {"g4", "f4", "e4"}});
        mills.put("g7", new String[][] {{"g1", "g4", "g7"}, {"g7", "d7", "a7"}});
        mills.put("f2", new String[][] {{"f2", "f4", "f6"}, {"f2", "d2", "b2"}});
        mills.put("f4", new String[][] {{"f2", "f4", "f6"}, {"g4", "f4", "e4"}});
        mills.put("f6", new String[][] {{"f2", "f4", "f6"}, {"f6", "d6", "b6"}});
        mills.put("e3", new String[][] {{"e3", "e4", "e5"}, {"e3", "d3", "c3"}});
        mills.put("e4", new String[][] {{"e3", "e4", "e5"}, {"g4", "f4", "e4"}});
        mills.put("e5", new String[][] {{"e3", "e4", "e5"}, {"e5", "d5", "c5"}});
    }

    static String[][] allMills = new String[][] {{"a1", "a4", "a7"}, {"b2", "b4", "b6"}, {"c3", "c4", "c5"}, {"d1", "d2", "d3"}, {"d7", "d6", "d5"}, {"g1", "g4", "g7"}, {"f2", "f4", "f6"}, {"e3", "e4", "e5"}, {"a1", "d1", "g1"}, {"b2", "d2", "f2"}, {"e3", "d3", "c3"}, {"a4", "b4", "c4"}, {"g4", "f4", "e4"}, {"e5", "d5", "c5"}, {"f6", "d6", "b6"}, {"g7", "d7", "a7"}};

    static String[] millCorners = new String[] {"a1", "g7", "c3", "e5", "b4", "d6", "d2", "f4"};

    // "d2" -> [3,1]
    public static int[] moveToIndex(String move) {
        return new int[] {"abcdefg".indexOf(move.charAt(0)), "1234567".indexOf(move.charAt(1))};
    }

    // [3,1] -> "d2"
    public static String indexToMove(int[] index) {
        return "" + "abcdefg".charAt(index[0]) + "1234567".charAt(index[1]);
    }

    public static void playMove(String move, int[][] board, int[] counts, String player) {
        String source = move.substring(0,2);
        String destination = move.substring(3,5);
        String removal = move.substring(6,8);
        // enact the move
        //if the current player is blue then 1, if its orange then 2
        board[moveToIndex(destination)[0]][moveToIndex(destination)[1]] = (player.equals("blue") ? 1 : 2);

        //remove from h1 (blue) hand
        if (source.equals("h1")) {
            counts[0]--;
            counts[2]++;
        }
        //remove from h2(orange hand)
        else if (source.equals("h2")) {
            counts[1]--;
            counts[3]++;
        } else {
            //set source boardpoint to 0 to represent now empty boardpoint
            board[moveToIndex(source)[0]][moveToIndex(source)[1]] = 0;
        }
        if (!removal.equals("r0")) { // does removal if applicable
            //remove piece - set board at remove coordinates to 0
            board[moveToIndex(removal)[0]][moveToIndex(removal)[1]] = 0;
            counts[player.equals("blue") ? 5 : 4]++; //if the player is blue, increase discardedOrangePieces count, otherwise increase blue count
            counts[player.equals("blue") ? 3 : 2]--; //if the player is blue, decrease the orangePiecesOnBoard count, otherwise decrease blue count
            
        }
    }

    //find if already a formed mill
    public static boolean findIfMillAt(int[][] board, String point) {
        String[][] adjacentMills = mills.get(point);
        boolean weHaveANewMill = ((board[moveToIndex(adjacentMills[0][0])[0]][moveToIndex(adjacentMills[0][0])[1]] == board[moveToIndex(adjacentMills[0][1])[0]][moveToIndex(adjacentMills[0][1])[1]] 
        && board[moveToIndex(adjacentMills[0][0])[0]][moveToIndex(adjacentMills[0][0])[1]] == board[moveToIndex(adjacentMills[0][2])[0]][moveToIndex(adjacentMills[0][2])[1]] && board[moveToIndex(adjacentMills[0][0])[0]][moveToIndex(adjacentMills[0][0])[1]] != 0) 
        || (board[moveToIndex(adjacentMills[1][0])[0]][moveToIndex(adjacentMills[1][0])[1]] == board[moveToIndex(adjacentMills[1][1])[0]][moveToIndex(adjacentMills[1][1])[1]] 
        && board[moveToIndex(adjacentMills[1][0])[0]][moveToIndex(adjacentMills[1][0])[1]] == board[moveToIndex(adjacentMills[1][2])[0]][moveToIndex(adjacentMills[1][2])[1]] && board[moveToIndex(adjacentMills[1][0])[0]][moveToIndex(adjacentMills[1][0])[1]] != 0)); // don't worry
        return weHaveANewMill;
    }


    //playMove(currentboard, arraCont[myPieces, myRemovedPieces, enemyPieces, enemyRemoved])
    // counts[0] = blue in-hand piece count
    // counts[1] = orange in-hand piece count
    // counts[2] = blue on-board piece count
    // counts[3] = orange on-board piece count
    // counts[4] = blue discarded piece count
    // counts[5] = orange discarded piece count
    //move can be any move valid or invalid
    //board can be a hypothetical board, different from global variable value
    //player is 1 if blue, 2 if orange
    public static boolean isMoveValid(String move, int[][] inputBoard, int[] counts, String player) {
        //check if move is valid for the board passed in
        if(move.trim().isEmpty()){
            errorMessage = "No move was given or a move was written in the incorrect format. Please format the move as (SOURCE DESTINATION REMOVAL)";
            return false;
        }
        String source = move.substring(0,2);
        String destination = move.substring(3,5);
        String removal = move.substring(6,8);
        int[][] board = boardCopy(inputBoard);

        boolean isDestinationThere = false;
        boolean isSourceThere = false;
        boolean isRemovalThere = false;
        for (int i = 0; i < points.length; i++) {
            if (points[i].equals(destination)) {
                isDestinationThere = true;
            }
            if (points[i].equals(source)) {
                isSourceThere = true;
            }
            if (points[i].equals(removal)) {
                isRemovalThere = true;
            }
        }
        if (!isDestinationThere) {
            errorMessage = "Invalid destination " + destination;
            return false;
        }
        if (!isSourceThere && !source.equals("h1") && !source.equals("h2")) {
            errorMessage = "Invalid source " + source;
            return false;
        }
        if (!isRemovalThere && !removal.equals("r0")) {
            errorMessage = "Invalid removal " + removal;
            return false;
        }
        
        if (source.equals("h2") && player.equals("blue") || source.equals("h1") && player.equals("orange")) {
            errorMessage = "Taking from the other player's hand is highly frowned upon!";
            return false;
        }
        //  check the source (hand or board)
        if (source.equals("h1") || source.equals("h2")) { // if taking piece from a hand (h1 or h2)
           if (counts[player.equals("blue") ? 0 : 1] == 0) { // and we have no pieces in hand
                errorMessage = "Your hand is empty!";
                return false; // then move was invalid, return false
           }
        }
        //if not from the hand or not from a spot where there is a piece to move
        if ((source.charAt(0) != 'h') && (board[moveToIndex(source)[0]][moveToIndex(source)[1]] == 0)) {
            errorMessage = source + " does not have a piece!";
            return false;
        } // check that A PIECE exists at the given coordinate
        else if ((source.charAt(0) != 'h') && (board[moveToIndex(source)[0]][moveToIndex(source)[1]] == 1 && player.equals("orange"))) {
            errorMessage = source + " has a blue piece, which you are not permitted to move!";
            return false;
        }
        else if ((source.charAt(0) != 'h') && (board[moveToIndex(source)[0]][moveToIndex(source)[1]] == 2 && player.equals("blue"))) {
            errorMessage = source + " has an orange piece, which you are not permitted to move!";
            return false;
        }
        else if (board[moveToIndex(destination)[0]][moveToIndex(destination)[1]] > 0) {
            errorMessage = destination + " is occupied!";
            return false; // occupied spot on the board, cannot move here
        }


        //if a player has 3 pieces on board and no pieces in hand then flyiing = true  
        boolean flying = counts[player.equals("blue") ? 2 : 3] == 3 && counts[player.equals("blue") ? 0 : 1] == 0;

        //if not flying and did not take from hand then we check that source and dest are ADJACENT
        if (!flying && source.charAt(0) != 'h') {
            if (!arrayContains(adjacentPoints.get(source), destination)) {
                errorMessage = source + " is not adjacent to " + destination + "!";
                return false;
            }
        }
            
        board[moveToIndex(destination)[0]][moveToIndex(destination)[1]] = (player.equals("blue") ? 1 : 2); // enact move
        if (source.charAt(0) != 'h') {
            board[moveToIndex(source)[0]][moveToIndex(source)[1]] = 0;
        }

        // check if the DESTINATION is on a mill
        boolean weHaveANewMill = findIfMillAt(board, destination);
        
        if (weHaveANewMill && removal.equals("r0")) {
            errorMessage = "You must remove a piece if you have created a mill!";
            return false;
        }

        if (!weHaveANewMill && !removal.equals("r0")) {
            errorMessage = "You cannot remove a piece if you have not created a mill!";
            return false;
        }

        
        if (!removal.equals("r0")) { // does removal if applicable
            //check that the piece to be removed is an opponents piece (not empty, not own peice)
            int pieceToBeRemoved = board[moveToIndex(removal)[0]][moveToIndex(removal)[1]];
            if (pieceToBeRemoved == 0) {
                errorMessage = "No piece to remove at " + removal + "!";
                return false;
            }
            if ((pieceToBeRemoved == 2 && player.equals("orange")) || (pieceToBeRemoved == 1 && player.equals("blue"))) {
                errorMessage = "Cannot remove your own piece at " + removal + "!";
                return false;
            }
            
        }

        return true;
    }

    public static boolean areWePastCutoffTime() {
        return (System.currentTimeMillis() - startTime > cutoffTime);
    }

    //iterative deepening here
    public static String findBestMove() {
        ArrayList<String> possibleMoves = findAllValidMoves(currentBoard, currentCounts, mePlayer);
        if (possibleMoves.size() == 0) {
            return "0";
        }
        
        ArrayList<String> bestMovesFromLowest = new ArrayList<>();
        for (int depth = 1; !areWePastCutoffTime(); depth++) {
            // also check time IN minimax
            int[] scoresOfPossibleMoves = new int[possibleMoves.size()];
            int maxScore = Integer.MIN_VALUE;
            for (int moveIndex = 0; moveIndex < possibleMoves.size(); moveIndex++) {
                int[][] hypotheticalBoard = boardCopy(currentBoard);
                int[] hypotheticalCounts = currentCounts.clone();
                playMove(possibleMoves.get(moveIndex), hypotheticalBoard, hypotheticalCounts, mePlayer);
                int thisScore = minimax(hypotheticalBoard, hypotheticalCounts, (mePlayer.equals("blue") ? "orange" : "blue"), depth, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
                if (areWePastCutoffTime()) {
                    break;
                }
                scoresOfPossibleMoves[moveIndex] = thisScore;
                if (thisScore > maxScore) {
                    maxScore = thisScore;
                }
                // if we are at 4.9 seconds (around here), then just break
            }
            if (areWePastCutoffTime()) {
                break;
            }
            ArrayList<String> bestMoves = new ArrayList<>();
            //for (int i = 0; i < scoresOfPossibleMoves.length; i++)
                //System.out.print(possibleMoves.get(i) + ": " + scoresOfPossibleMoves[i] + "\t");
            //System.out.println();
            for (int i = 0; i < scoresOfPossibleMoves.length; i++) {
                if (scoresOfPossibleMoves[i] == maxScore) {
                    bestMoves.add(possibleMoves.get(i));
                }
            }
            bestMovesFromLowest = bestMoves;
            //System.out.println("best moves array " + bestMovesFromLowest.toString());
        }

        // grade(): String move ==> int grade
        int[] grades = new int[bestMovesFromLowest.size()];
        int maxGrade = Integer.MIN_VALUE;
        for (int i = 0; i < bestMovesFromLowest.size(); i++) {
            grades[i] = grade(bestMovesFromLowest.get(i));
            if (grades[i] > maxGrade) {
                maxGrade = grades[i];
            }
        }
        ArrayList<String> bestMovesEver = new ArrayList<>();
        for (int i = 0; i < bestMovesFromLowest.size(); i++) {
            if (grades[i] == maxGrade) {
                bestMovesEver.add(bestMovesFromLowest.get(i));
            }
        }
        int random = (int) (Math.random() * bestMovesEver.size());
        //System.out.print("best moves ever: ");
        //for (int i = 0; i < bestMovesEver.size(); i++) {
            //System.out.print(bestMovesEver.get(i) + "; ");
        //}
        //System.out.println();
        return bestMovesEver.get(random); 
        //return bestMovesEver.getLast(); 
    }

    public static boolean arrayContains(String[] array, String thing) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(thing))
                return true;
        }
        return false;
    }

    public static int grade(String move) {
        if (move.charAt(0) == 'h') {
            return 100;
        }
        return 0;
    }

    public static int[][] boardCopy(int[][] inputBoard) {
        int[][] board = new int[7][7]; //copy of board so board is not modified
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                board[i][j] = inputBoard[i][j];
            }
        }
        return board;
    }
    
    //find all valid moves for a given board
        // counts[0] = blue in-hand piece count
    // counts[1] = orange in-hand piece count
    // counts[2] = blue on-board piece count
    // counts[3] = orange on-board piece count
    // counts[4] = blue discarded piece count
    // counts[5] = orange discarded piece count
    public static ArrayList<String> findAllValidMoves(int[][] board, int[] counts, String player) {
        ArrayList<String> moves = new ArrayList<>();
        int piecesInMyHand = counts[0]; // for blue
        int enemyPiece = 2;
        int myPiece = (player.equals("blue")) ? 1 : 2;
        if (player.equals("orange")) {
            piecesInMyHand = counts[1]; // for orange
            enemyPiece = 1;
        }
        //find opponent piece to remove
        ArrayList<String> removableEnemyPieces = new ArrayList<>();
        ArrayList<String> enemyPiecesInMill = new ArrayList<>();
        
        for (int i = 0; i < points.length; i++) {
            if (board[moveToIndex(points[i])[0]][moveToIndex(points[i])[1]] == enemyPiece) {
                if (findIfMillAt(board, points[i])) {
                    enemyPiecesInMill.add(points[i]);
                } else {
                    removableEnemyPieces.add(points[i]);
                }   
            }
        }
        if (removableEnemyPieces.size() == 0) {
            removableEnemyPieces = enemyPiecesInMill;
        }

        if (piecesInMyHand > 0) { // adding pieces from my hand
            String myHand = (player.equals("blue")) ? "h1" : "h2";
            for (int i = 0; i < points.length; i++) {
                if (board[moveToIndex(points[i])[0]][moveToIndex(points[i])[1]] == 0) {
                    int[][] boardWithMove = boardCopy(board);
                    boardWithMove[moveToIndex(points[i])[0]][moveToIndex(points[i])[1]] = myPiece;
                    boolean weHaveANewMill = findIfMillAt(boardWithMove, points[i]);
                    if (weHaveANewMill) {
                        for (int j = 0; j < removableEnemyPieces.size(); j++) {
                            moves.add(0, myHand + " " + points[i] + " " + removableEnemyPieces.get(j));
                        }
                    } else {
                        moves.add(myHand + " " + points[i] + " r0");
                    }     
                }
            }
        }
        //moving my piece on board to an adjacent valid position
        int myPiecesOnBoard = counts[2];
        if (player.equals("orange")) {
            myPiecesOnBoard = counts[3];
        }
        if (myPiecesOnBoard == 3 && piecesInMyHand == 0) { // if flying
            for (int i = 0; i < points.length; i++) {
                if (board[moveToIndex(points[i])[0]][moveToIndex(points[i])[1]] == myPiece) {
                    for (int j = 0; j < points.length; j++) {
                        if (board[moveToIndex(points[j])[0]][moveToIndex(points[j])[1]] == 0) {
                            int[][] boardWithMove = boardCopy(board);
                            boardWithMove[moveToIndex(points[j])[0]][moveToIndex(points[j])[1]] = myPiece;
                            boardWithMove[moveToIndex(points[i])[0]][moveToIndex(points[i])[1]] = 0;
                            boolean weHaveANewMill = findIfMillAt(boardWithMove, points[j]);
                            if (weHaveANewMill) {
                                for (int k = 0; k < removableEnemyPieces.size(); k++) {
                                    moves.add(0, points[i] + " " + points[j] + " " + removableEnemyPieces.get(k));
                                }
                            } else {
                                moves.add(points[i] + " " + points[j] + " r0");
                            }   
                        }
                    }
                }
            }
        }
        else { // if NOT flying
            for (int i = 0; i < points.length; i++) {
                if (board[moveToIndex(points[i])[0]][moveToIndex(points[i])[1]] == myPiece) {
                    String[] pointsAdjacentToPointI = adjacentPoints.get(points[i]);
                    for (int j = 0; j < pointsAdjacentToPointI.length; j++) {
                        if (board[moveToIndex(pointsAdjacentToPointI[j])[0]][moveToIndex(pointsAdjacentToPointI[j])[1]] == 0) {
                            int[][] boardWithMove = boardCopy(board);
                            boardWithMove[moveToIndex(pointsAdjacentToPointI[j])[0]][moveToIndex(pointsAdjacentToPointI[j])[1]] = myPiece;
                            //reset source to 0 after moving to destination
                            boardWithMove[moveToIndex(points[i])[0]][moveToIndex(points[i])[1]] = 0;
                            //find if placing a move will make a mill
                            boolean weHaveANewMill = findIfMillAt(boardWithMove, pointsAdjacentToPointI[j]);
                            if (weHaveANewMill) {
                                for (int k = 0; k < removableEnemyPieces.size(); k++) {
                                    moves.add(0, points[i] + " " + pointsAdjacentToPointI[j] + " " + removableEnemyPieces.get(k));
                                }
                            } else {
                                moves.add(points[i] + " " + pointsAdjacentToPointI[j] + " r0");
                            }   
                        }
                    }
                }
            }
        }
        return moves;
    }

public static int utility(int[][] board, int[] counts) {
    int myPiecesOnBoard = counts[2];
    int enemyPiecesOnBoard = counts[3];
     int piecesInMyHand = counts[0]; // for blue
    int piecesInEnemyHand = counts[1];
    if (mePlayer.equals("orange")) {
        piecesInMyHand = counts[1]; // for orange
        piecesInEnemyHand = counts[0];
        myPiecesOnBoard = counts[3];
        enemyPiecesOnBoard = counts[2];
    }
    if (piecesInMyHand == 0 && myPiecesOnBoard == 2) {// we are losing
        return -1000;
    } else if (piecesInEnemyHand == 0 && enemyPiecesOnBoard == 2) { // it is awesome
        return 1000;
    }
    return 0;
}

public static int evaluateBoard(int[][] board, int[] counts){
    
    int piecesInMyHand = counts[0]; // for blue
    int piecesInEnemyHand = counts[1];
    int myPiecesOnBoard = counts[2];
    int enemyPiecesOnBoard = counts[3];
    int myRemovedPieces = counts[4];
    int enemyRemovedPieces = counts[5];
    //int enemyPiece = 2;
    if (mePlayer.equals("orange")) {
        piecesInMyHand = counts[1]; // for orange
        piecesInEnemyHand = counts[0];
        //enemyPiece = 1;
        myPiecesOnBoard = counts[3];
        enemyPiecesOnBoard = counts[2];
        myRemovedPieces = counts[5];
        enemyRemovedPieces = counts[4];
    }
    //utility function (check numbre of pieces on board)
    if (piecesInMyHand == 0 && myPiecesOnBoard == 2) {// we are losing
        return -1000;
    } else if (piecesInEnemyHand == 0 && enemyPiecesOnBoard == 2) { // it is awesome
        return 1000;
    }

    //check the number of our pieces on the board
    int evaluation = 0;
    evaluation += enemyRemovedPieces * 100;
    evaluation -= myRemovedPieces * 100;

    for (int i = 0; i < allMills.length; i++) {
        boolean areTheseAll1s = true;
        boolean areTheseAll2s = true;
        int totalButSet2ToNegative1 = 0;
        boolean weAreBlue = (mePlayer.equals("blue"));
        for (int j = 0; j < 3; j++) {
            if (board[moveToIndex(allMills[i][j])[0]][moveToIndex(allMills[i][j])[1]] != 1){
                areTheseAll1s = false;
            }
            if (board[moveToIndex(allMills[i][j])[0]][moveToIndex(allMills[i][j])[1]] != 2){
                areTheseAll2s = false;
            }
            if (board[moveToIndex(allMills[i][j])[0]][moveToIndex(allMills[i][j])[1]] == 2){
                totalButSet2ToNegative1 -= 1;
            }
            else{
                totalButSet2ToNegative1 += board[moveToIndex(allMills[i][j])[0]][moveToIndex(allMills[i][j])[1]];
            }
        }
        boolean areTheseMostly1s = (totalButSet2ToNegative1 == 2);
        boolean areTheseMostly2s = (totalButSet2ToNegative1 == -2);
        //we formed a mill
        if (areTheseAll1s && weAreBlue || areTheseAll2s && !weAreBlue)
            evaluation += 15;
        //opponent formed a mill
        if (areTheseAll1s && !weAreBlue || areTheseAll2s && weAreBlue)
            evaluation -= 10;
        //we have 2/3 of a mill
        if (areTheseMostly1s && weAreBlue || areTheseMostly2s && !weAreBlue)
            evaluation += 15;
        //opponent has 2/3 of a mill
        if (areTheseMostly1s && !weAreBlue || areTheseMostly2s && weAreBlue)
            evaluation -= 10;
    }

    return evaluation;
}

public static int minimax(int[][] board, int[] counts, String player, int depth, boolean maxPlayer, int alpha, int beta){
    // Check if the game has ended
    if (areWePastCutoffTime()) {
        return 0;
    }
    int score = evaluateBoard(board, counts);
    if (score == 1000 || score == -1000 || depth == 0) {
        return score;
    }

    ArrayList<String> possibleMoves = findAllValidMoves(board, counts, player);

    if(maxPlayer) { 
        int bestMax = Integer.MIN_VALUE;
        for (int i = 0; i < possibleMoves.size(); i++){ //update counts 
            int[][] hypotheticalBoard = boardCopy(board);
            int[] hypotheticalCounts = counts.clone();
            playMove(possibleMoves.get(i), hypotheticalBoard, hypotheticalCounts, player);
            //decide if taking the move was the best option
            int possibleMax = minimax(hypotheticalBoard, hypotheticalCounts, (mePlayer.equals("orange") ? "blue" : "orange"), depth - 1, false, alpha, beta);
            if (areWePastCutoffTime()) {
                return score;
            }
            bestMax = Math.max(bestMax, possibleMax);
    
            //alpha-beta pruning - set alpha to the best move for X (Max-player), break if alpha >= beta
            alpha = Math.max(alpha, bestMax);
            if(alpha >= beta){
                break; //stop looking further
            }
    
        }
        return bestMax;
    }
    else {
        int bestMin = Integer.MAX_VALUE;
        for(int i = 0; i < possibleMoves.size(); i++){
            int[][] hypotheticalBoard = boardCopy(board);
            int[] hypotheticalCounts = counts.clone();
            playMove(possibleMoves.get(i), hypotheticalBoard, hypotheticalCounts, player);
            int possibleMin = minimax(hypotheticalBoard, hypotheticalCounts, (mePlayer.equals("blue") ? "orange" : "blue"), depth - 1, true, alpha, beta);
            if (areWePastCutoffTime()) {
                return score;
            }
            bestMin = Math.min(bestMin, possibleMin);
            beta = Math.min(beta, bestMin);
            if(beta <= alpha){
                break;
            }
        }
        return bestMin;
    }
}
    

public static boolean isItATie(String move) {
    char r = move.charAt(6);
    if (r == 'r')
        moveSinceLastActivity++;
    else
        moveSinceLastActivity = 0;
    if (moveSinceLastActivity > stalemateThreshold)
        return true;
    return false;
}

public static void main(String[] args) throws IOException, HttpException, Exception {
    try {
        Client client = Client.builder().apiKey("").build();

        // Sets the safety settings in the config.
        ImmutableList<SafetySetting> safetySettings =
            ImmutableList.of(
                SafetySetting.builder()
                    .category("HARM_CATEGORY_HATE_SPEECH")
                    .threshold("BLOCK_ONLY_HIGH")
                    .build(),
                SafetySetting.builder()
                    .category("HARM_CATEGORY_DANGEROUS_CONTENT")
                    .threshold("BLOCK_LOW_AND_ABOVE")
                    .build());

        // Sets the system instruction in the config.
        Content systemInstruction =
            Content.builder()
                .parts(ImmutableList.of(Part.builder().text("Answer as concisely as possible").build()))
                .build();

        // Sets the Google Search tool in the config.
        Tool googleSearchTool = Tool.builder().googleSearch(GoogleSearch.builder().build()).build();

        GenerateContentConfig config =
            GenerateContentConfig.builder()
                .candidateCount(1)
                .maxOutputTokens(1024)
                .systemInstruction(systemInstruction)
                .build();

        String explanation = "You will be given the state of a game of Lasker Morris, following standard Lasker Morris rules. In Lasker Morris, Player 1 and Player 2 both start with 10 pieces in their hand. On alternating turns, they perform one of two actions:\n- Placing a new piece from their hand on a point on the board\n- Moving one of their existing pieces to an adjacent empty point on the board\nBoth players want to create 'mills', or three of THEIR pieces on legal board points on the same row or column (not diagonals). Mills are specifically three of the same player's pieces. If you create a mill, you are allowed to remove an opponent's piece on that turn. (You may only remove an opponent's piece that is NOT part of a mill, unless all opponent pieces are part of a mill, in which case any enemy piece can be removed.)\nAs such, both players want to block their opponent from forming mills. For instance, if one player has claimed g1 and g7, the opponent should claim g4 in order to block that mill.\nWhen a player's hand is empty and they have only three pieces left on the board, they are now flying! This gives them the ability to move their pieces to any empty board point, not just adjacent ones.\nA player loses when their hand is empty and they have only two pieces left on the board. Thus, a player wants to create mills in order to remove their opponent's pieces from the board.\n\nThe board is given as a seven-by-seven grid where each valid point in the game is represented as '.' if empty, '1' if holding one of Player 1's pieces, and '2' if holding one of Player 2's pieces. The complete list of valid board points is: a1, a4, a7, b2, b4, b6, c3, c4, c5, d1, d2, d3, d5, d6, d7, e3, e4, e5, f2, f4, f6, g1, g4, g7. On this board, coordinates are given in the format 'a4' where 'a' refers to the row and '4' refers to the column. Here is an example board:\n\n.      1 2 3 4 5 6 7\nrow a: 2     .     1\nrow b:   .   .   .\nrow c:     . . .\nrow d: 2 . .   . . .\nrow e:     . . .\nrow f:   1   .   .\nrow g: .     .     .\n\nHere, Player 1 has pieces at a7 and f2, while Player 2 has pieces at a1 and d1.\n\nIn this game, moves are stored in the form: (SOURCE DESTINATION REMOVAL). A piece is moved from the SOURCE to the DESTINATION, and the piece marked REMOVAL is removed.\n- The SOURCE is either the coordinate of a point on the board, or 'h1' (referring to Player 1's hand) or 'h2' (referring to Player 2's hand.)\n- The DESTINATION is the coordinate of a point on the board.\n- The REMOVAL is either the coordinate of a point on the board (if the point on the board is occupied by an opponent's piece) or 'r0' if no piece will be removed.\n\nHere are some example moves:\n- (h1 a4 r0): a piece is moved from h1 (Player 1's hand) to the board point a4, and no piece is removed.\n- (h2 f2 c3): a piece is moved from h2 (Player 2's hand) to the board point f2, and the piece at board point c3 is removed.\n- (d3 d2 r0): a piece is moved from the board point d3 to the board point d2, and no piece is removed.\n\nRemember to always include your move in your response using the format: (b4 b6 r0)\n\n";
    
        initializeAdjacentPoints();
        initializeMills();
        
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();
        String bestMove = "";
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(input.equals("blue")){
            mePlayer = "blue";
            String firstMove = "h1 a7 r0";
            
            System.out.println(firstMove);
            playMove(firstMove, currentBoard, currentCounts, mePlayer);
            //printBoard(currentBoard, currentCounts);
        }
        while (scanner.hasNextLine()) {
            input = scanner.nextLine().trim();
            startTime = System.currentTimeMillis();
            if (!isMoveValid(input, currentBoard, currentCounts, (mePlayer.equals("blue") ? "orange" : "blue"))){
                System.out.println("Invalid move! Gabor wins!");
                System.out.println("Error: " + errorMessage);
                scanner.close();
                return; 
            }
            if (isItATie(input)) {
                System.out.println("Tie!");
                scanner.close();
                return;
            }
            playMove(input, currentBoard, currentCounts, (mePlayer.equals("blue") ? "orange" : "blue"));
            if (utility(currentBoard, currentCounts) != 0) {
                if (utility(currentBoard, currentCounts) == 1000) {
                    System.out.println("Gabor wins!");
                    scanner.close();
                    return;
                }
                if (utility(currentBoard, currentCounts) == -1000) {
                    System.out.println("Gabor loses!");
                    scanner.close();
                    return;                
                }
            }
            //printBoard(currentBoard, currentCounts);

            /**WHERE WE REPLACED FINDBEST WITH GEMINI PROMPT*/
            GenerateContentResponse bestMoveFromGemini;
            String bestMoveFromGeminiString = "";
            String bestMoveFromGeminiStringUnrefined = "";
            Pattern pattern = Pattern.compile("\\((?:h1|h2|[a-g][1-7]) (?:[a-g][1-7]) (?:[a-g][1-7]|r0)\\)");
            boolean validSyntax = false;
            String correction = "";
            int iteration = 1;
            int geminiIteration = 1; 
            Boolean hasErrorOccured = false;

            do {
                if (iteration > 1) {
                    correction += "\n\nA previous response to this prompt, " + bestMoveFromGeminiString + ", was invalid for the following reason: " + errorMessage + "\n";
                }
                validSyntax = false;

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                hasErrorOccured = false;
                try {
                    bestMoveFromGemini = 
                        client.models.generateContent("gemini-2.0-flash-001", (explanation + createPrompt(currentBoard, currentCounts) + correction), config);
                    
                    bestMoveFromGeminiStringUnrefined = bestMoveFromGemini.text(); 
                    hasErrorOccured = false;               
                } //catch any errors the API returns 
                catch (HttpException e) {
                    Thread.sleep(4000); //added to slow it down 
                    geminiIteration++; 
                    hasErrorOccured = true;
                    correction += "No move was given or a move was written in the incorrect format. Please format the move as (SOURCE DESTINATION REMOVAL)";
                    if(geminiIteration > 2){
                        ArrayList<String> allValid = findAllValidMoves(currentBoard, currentCounts, mePlayer);
                        bestMoveFromGeminiString = allValid.get((int) (Math.random() * allValid.size()));
                        hasErrorOccured = false;
                    }
                }
                
                Matcher matcher = pattern.matcher(bestMoveFromGeminiStringUnrefined);
                if (matcher.find()) {
                    validSyntax = true;
                    bestMoveFromGeminiString = bestMoveFromGeminiStringUnrefined.substring(matcher.start() + 1, matcher.start() + 9);
                } else {
                    validSyntax = false;
                }
                iteration++;
            } while (iteration < 3 && (!validSyntax || !isMoveValid(bestMoveFromGeminiString, currentBoard, currentCounts, mePlayer) || hasErrorOccured));

            if (iteration >= 2 || !isMoveValid(bestMoveFromGeminiString, currentBoard, currentCounts, mePlayer) || bestMoveFromGeminiString.trim().isEmpty()) {
                Thread.sleep(5000); 
                ArrayList<String> allValid = findAllValidMoves(currentBoard, currentCounts, mePlayer);
                bestMoveFromGeminiString = allValid.get((int) (Math.random() * allValid.size()));
            }

            System.out.println(bestMoveFromGeminiString);

            if (isItATie(bestMoveFromGeminiString)) {
                System.out.println("Tie!");
                scanner.close();
                return;
            }
            playMove(bestMoveFromGeminiString, currentBoard, currentCounts, mePlayer);
            if (utility(currentBoard, currentCounts) != 0) {
                if (utility(currentBoard, currentCounts) == 1000) {
                    System.out.println("Gabor wins!");
                    scanner.close();
                    return;
                }
                if (utility(currentBoard, currentCounts) == -1000) {
                    System.out.println("Gabor loses!");
                    scanner.close();
                    return;                
                }
            }
            if (findAllValidMoves(currentBoard, currentCounts, (mePlayer.equals("blue") ? "orange" : "blue")).size() == 0) {
                System.out.println("No available moves for you! Gabor wins!");
                scanner.close();
                return;
            }
            //printBoard(currentBoard, currentCounts);
        }
        scanner.close();
    }
    catch (IOException e) {
        String randMove = "";
        Thread.sleep(5000); //added to slow it down 
        ArrayList<String> allValid = findAllValidMoves(currentBoard, currentCounts, mePlayer);
        randMove = allValid.get((int) (Math.random() * allValid.size()));
        System.out.println(randMove);
    } catch (InterruptedException e) {
        String randMove = "";
        Thread.sleep(5000); //added to slow it down 
        ArrayList<String> allValid = findAllValidMoves(currentBoard, currentCounts, mePlayer);
        randMove = allValid.get((int) (Math.random() * allValid.size()));
        System.out.println(randMove);
    } catch (Exception e) {
        String randMove = "";
        Thread.sleep(5000); //added to slow it down 
        ArrayList<String> allValid = findAllValidMoves(currentBoard, currentCounts, mePlayer);
        randMove = allValid.get((int) (Math.random() * allValid.size()));
        System.out.println(randMove);
    }
}
}
