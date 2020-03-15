package com.company;

public class Conflict {
    public String  row , column,conflict1, conflict2;

    public Conflict(String row, String column, String conflict1, String conflict2) {
        this.row = row;
        this.column = column;
        this.conflict1 = conflict1;
        this.conflict2 = conflict2;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("at state %s on %s, %s/%s happened.", row, column , conflict1, conflict2));
        return builder.toString();
    }
}
