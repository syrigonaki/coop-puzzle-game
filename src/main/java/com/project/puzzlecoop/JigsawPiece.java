package com.project.puzzlecoop;

public class JigsawPiece {

    private int id;

    private int row;
    private int col;

    private double x;
    private double y;

    private int rightEdge;
    private int bottomEdge;
    private int topEdge;

    private int leftEdge;

    private String lockedBy;  


    private boolean snapped;  

    private int groupId; // pieces that have been grouped together by player

    public JigsawPiece() {}

    public JigsawPiece(int id, int row, int col, double x, double y, int topEdge, int rightEdge, int bottomEdge, int leftEdge) {
        this.id = id;

        this.row = row;
        this.col = col;
        this.x = x;
        this.y = y;

        this.topEdge = topEdge;
        this.rightEdge = rightEdge;
        this.bottomEdge = bottomEdge;
        this.leftEdge = leftEdge;
        this.lockedBy = null;
        this.snapped = false;
        this.groupId = id; 
    }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public int getId() { return id; }

    public int getRow() { return row; }
    public int getCol() { return col; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public int getTopEdge() { return topEdge; }
    public int getRightEdge() { return rightEdge; }
    public int getBottomEdge() { return bottomEdge; }
    public int getLeftEdge() { return leftEdge; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public boolean isSnapped() { return snapped; }
    public void setSnapped(boolean snapped) { this.snapped = snapped; }
}