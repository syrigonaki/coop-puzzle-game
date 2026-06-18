package com.project.puzzlecoop;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class PuzzleManager {
    private static final int ROWS = 4;
    private static final int COLS = 4;

    private static final double GRID_START_X = 250;
    private static final double GRID_START_Y = 50;

    private static final double TILE_SIZE = 100;
    
    private static final double GRID_SNAP_TOLERANCE = 40.0; 

    private static final double PIECE_SNAP_TOLERANCE = 15.0; //when attaching pieces off the grid they must be closer together

    private final List<JigsawPiece> pieces = new CopyOnWriteArrayList<>(); 
    private final Random random = new Random();
    
    private final Set<String> votedSessions = ConcurrentHashMap.newKeySet();
    private volatile int totalUsers = 0; 

    private long currentRoundId = System.currentTimeMillis(); 

    public PuzzleManager() {
        generatePuzzle();
    }

    public long getCurrentRoundId() {
        return currentRoundId;
    }

    public boolean isPuzzleComplete() {
        if (pieces.isEmpty()) return false;
        for (JigsawPiece p : pieces) {
            if (!p.isSnapped()) return false;
        }
        return true;
    }

    private void generatePuzzle() {
        int[][] horizontalEdges = new int[ROWS + 1][COLS];
        int[][] verticalEdges = new int[ROWS][COLS + 1];

        // 1 = piece side sticks out, -1 = piece side has indent thing
        for (int row = 1; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                horizontalEdges[row][col] = random.nextBoolean() ? 1 : -1;
            }
        }

        for (int row = 0; row < ROWS; row++) {
            for (int col = 1; col < COLS; col++) {
                verticalEdges[row][col] = random.nextBoolean() ? 1 : -1;
            }
        }

        int id = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {

                // grid lines
                int top = horizontalEdges[row][col];
                int left = verticalEdges[row][col];
                
        
                int bottom = horizontalEdges[row + 1][col];
                if (row + 1 < ROWS) {
                    bottom = (bottom == 1) ? -1 : 1; // flip it to line up
                }
                
                int right = verticalEdges[row][col + 1];
                if (col + 1 < COLS) {
                    right = (right == 1) ? -1 : 1; 
                }
                
                boolean putOnLeft = random.nextBoolean(); 
                double scatterX = putOnLeft ? (20 + random.nextDouble() * 100) : (700 + random.nextDouble() * 100);
                double scatterY = 50 + random.nextDouble() * 450;

                pieces.add(new JigsawPiece(id, row, col, scatterX, scatterY, top, right, bottom, left));
                id++;
            }
        }
    }

   
    public List<JigsawPiece> getPieces() { 
        return pieces; 
    }

    public synchronized boolean grabPiece(int pieceId, String sessionId) {
        JigsawPiece target = pieces.get(pieceId);
        if (target.isSnapped()) return false;
        
        int groupId = target.getGroupId();
        
        // make sure no one is holding any piece in this cluster
        for (JigsawPiece p : pieces) { 
            if (p.getGroupId() == groupId && p.getLockedBy() !=null && !p.getLockedBy().equals(sessionId)) {
                //System.out.println("CANNOT GRAB PIECES " + groupId + " locked by " + p.getLockedBy() + " not " + sessionId);
                return false; 
            }
        }
        
        for (JigsawPiece p : pieces) {
            if (p.getGroupId() == groupId) p.setLockedBy(sessionId);
        }
        return true;
    }

    public synchronized void movePiece(int pieceId, double newX, double newY, String sessionId) {
        JigsawPiece target = pieces.get(pieceId);
        if (!sessionId.equals(target.getLockedBy()) || target.isSnapped()) return; 

        double deltaX = newX - target.getX(); 
        double deltaY = newY - target.getY();
        int gid = target.getGroupId();

        for (JigsawPiece p : pieces) { //need to move all pieces sharing the same group id
            if (p.getGroupId() == gid) {
                p.setX(p.getX() + deltaX);
                p.setY(p.getY() + deltaY);
            }
        }
    }

    public synchronized void dropPiece(int pieceId, double finalX, double finalY, String sessionId) {
        JigsawPiece target = pieces.get(pieceId);
        if (!sessionId.equals(target.getLockedBy())) return;

        movePiece(pieceId, finalX, finalY, sessionId);
        int gid = target.getGroupId();
        

        List<JigsawPiece> heldPieces = new ArrayList<>();
        for (JigsawPiece p : pieces) {
            if (p.getGroupId() == gid) {
                p.setLockedBy(null); // unlock at the same time
                heldPieces.add(p);
            }
        }

        // check if piece needs to snap to the grid
        boolean piecesShouldSnap = false;
        for (JigsawPiece p : heldPieces) {

            double idealX = GRID_START_X + (p.getCol() * TILE_SIZE);
            double idealY = GRID_START_Y + (p.getRow() * TILE_SIZE);

            double dx = p.getX() - idealX;
            double dy = p.getY() - idealY;
            
            if ((dx * dx + dy * dy) < (GRID_SNAP_TOLERANCE * GRID_SNAP_TOLERANCE)) {  
                piecesShouldSnap = true;
                break;
            
            }
        }

        
        if (piecesShouldSnap) {
            for (JigsawPiece p : heldPieces) {
                p.setX(GRID_START_X + (p.getCol() * TILE_SIZE));
                p.setY(GRID_START_Y + (p.getRow() * TILE_SIZE));
                p.setSnapped(true);
            }
            return; 
        }

        // check if piece needs to snap to another piece outside of the grid
        for (JigsawPiece p : pieces) {
            if (p.getGroupId() == gid || p.isSnapped()) continue;

            int rowDiff = Math.abs(target.getRow() - p.getRow());
            int colDiff = Math.abs(target.getCol() - p.getCol());

        
            if ((rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)) {
                
                // are they direct neighbors?
                double idealDistX = (target.getCol() - p.getCol()) * TILE_SIZE;
                double idealDistY = (target.getRow() - p.getRow()) * TILE_SIZE;

                double currentDistX = target.getX() - p.getX();
                double currentDistY = target.getY() - p.getY();

                double errX = currentDistX - idealDistX;
                double errY = currentDistY - idealDistY;
                if ((errX * errX + errY * errY) < (PIECE_SNAP_TOLERANCE * PIECE_SNAP_TOLERANCE)) {
                   // System.out.println("attached piece: Piece " + target.getId() + " w/ " + p.getId());
                    double alignShiftX = p.getX() + idealDistX - target.getX();
                    double alignShiftY = p.getY() + idealDistY - target.getY();
                    int targetGroupId = p.getGroupId();
                    
                    for (JigsawPiece k : heldPieces) {
                        k.setX(k.getX() + alignShiftX);
                        k.setY(k.getY() + alignShiftY);
                        k.setGroupId(targetGroupId);
                    }
                    break;
                }
            }
        }
    }

    public void setTotalUsers(int count) {
        this.totalUsers = count;
    }

    public synchronized void resetPuzzle() {
        pieces.clear();
        currentRoundId = System.currentTimeMillis(); 
        generatePuzzle();
    }

   
    public boolean castVote(String sessionId) {
        votedSessions.add(sessionId); 
        return votedSessions.size() >= totalUsers; //maybe do 75% of votes ?
    }

    public int getVotesToReset() {
        return votedSessions.size();
    }

    
    public void resetVotes() {
        votedSessions.clear();
    }

    public void removeVote(String sessionId) {
        votedSessions.remove(sessionId);
    }
}