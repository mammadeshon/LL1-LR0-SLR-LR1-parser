package com.company;

import java.util.ArrayList;


public class NonTerminal {
    String name;
    ArrayList<String> firstSet;
    ArrayList<String> followSet;

    public NonTerminal(String name) {
        this.name = name;
        firstSet = new ArrayList<>();
        followSet = new ArrayList<>();
    }

}
