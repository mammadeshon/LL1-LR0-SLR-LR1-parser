package com.company;

import java.util.ArrayList;
import java.util.HashMap;


public class State {
    int stateNum;
    ArrayList<Item> itemSet;
    HashMap<String, Integer> moveMap;

    public State() {
        itemSet = new ArrayList<>();
        moveMap = new HashMap<>();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("State %03d {\n", stateNum));
        builder.append("\t").append("itemSet: \n");
        for (Item item : itemSet)
            builder.append("\t\t").append(item).append("\n");
        builder.append("\t").append("MoveMap: \n");
        for (String key : moveMap.keySet())
            builder.append("\t\t").append(key).append(" -> ").append(moveMap.get(key)).append("\n");
        builder.append("}");
        return builder.toString();
    }
}
