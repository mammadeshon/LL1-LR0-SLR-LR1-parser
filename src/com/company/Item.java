package com.company;


import java.util.ArrayList;
import java.util.Arrays;


public class Item {
    // both LR(1) item and LR(0)
    // LR(0) items are Items with empty Lookahead
    int ruleNum;
    String rule;
    ArrayList<String> lookahead;

    public Item(String rule, int ruleNum) {
        int idx = rule.indexOf("->");
        if (rule.contains("#")) {
            // e.x: s-># becomes to s->@
            this.rule = rule.substring(0, idx + 2) + "@";
        } else {
            // e.x: s->ABC becomes to s->@ABC
            this.rule = rule.substring(0, idx + 2) + "@" + rule.substring(idx + 3);
        }
        this.ruleNum = ruleNum;
        this.lookahead = new ArrayList<>();
    }

    public Item(String rule, int ruleNum, ArrayList<String> lookahead) {
        this.rule = rule;
        this.ruleNum = ruleNum;
        this.lookahead = lookahead;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("%-56s", rule));
        if (lookahead != null && !lookahead.isEmpty())
            builder.append(Arrays.toString(lookahead.toArray()));
        return builder.toString();
    }

}
