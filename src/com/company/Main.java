package com.company;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static ArrayList<String> grammar;
    private static HashMap<String, NonTerminal> nonTerminals;       // list of nonTerminals (e.x : A->Be .... maps "A" to it's NonTerminal instance)
    private static ArrayList<String> terminals;                     // list of Terminals
    private static HashMap<String, Integer> columnsMap;             // map table column names to their indexes
    private static HashMap<String, Integer> llRows;                 // map ll(1) table rows to nonTerminals
    private static HashMap<String, Integer> llColumns;              // map ll(1) table columns to terminals
    private static Stack<Integer> numStack;                         // a stack for state numbers
    private static Stack<String> tokenStack;                        // a stack for tokens received from input
    private static ArrayList<Token> tokens;
    private static int currentState = 0;

    static final String LR0 = "lr0";
    static final String SLR1 = "slr1";
    static final String LR1 = "lr1";

    public static void main(String[] args) throws FileNotFoundException {
        init();
        // create state machine
        ArrayList<State> dfa0 = generateDFA(0);
        ArrayList<State> dfa1 = generateDFA(1);

        writeDFA(dfa0, "dfa0.txt");
        writeDFA(dfa1, "dfa1.txt");

        ArrayList<Conflict> lr0Conflicts = new ArrayList<>(), slr1Conflicts = new ArrayList<>(), lr1Conflicts = new ArrayList<>(), llConflicts = new ArrayList<>();

        String[][] lr0Table = createTable(dfa0, LR0, lr0Conflicts);
        String[][] slr1Table = createTable(dfa0, SLR1, slr1Conflicts);
        String[][] lr1Table = createTable(dfa1, LR1, lr1Conflicts);
        String[][] llTable = createLLTable(llConflicts);

        writeTable(lr0Table, "lr0 table.txt");
        writeTable(slr1Table, "slr1 table.txt");
        writeTable(lr1Table, "lr1 table.txt");
        writeLLTable(llTable);

        System.out.println("-------------------------LL(1)-------------------------");
        if (!llConflicts.isEmpty()) {
            System.out.println("The grammar is not parsable with LL(1)\n");
            for (Conflict conf : llConflicts) {
                if (conf.conflict1.contains("#") || conf.conflict2.contains("#")) {
                    // first/follow conflict
                    System.out.println(String.format("First/Follow conflict on %s for %s \n  %s\n  %s\n"
                            , conf.row, conf.column, conf.conflict1, conf.conflict2));
                } else {
                    // first/first conflict
                    System.out.println(String.format("First/First conflict on %s for %s \n %s\n %s\n"
                            , conf.row, conf.column, conf.conflict1, conf.conflict2));
                }
            }
        } else {
            String res = parseLL(llTable);
            System.out.println(res);
        }

        System.out.println("-------------------------LR(0)-------------------------");
        if (!lr0Conflicts.isEmpty()) {
            System.out.println("The grammar is not parsable with LR(0)\n");
            for (Conflict conflict : lr0Conflicts) System.out.println(conflict.toString());
        } else {
            String res = parse(lr0Table);
            System.out.println(res);
        }

        System.out.println("-------------------------SLR(1)-------------------------");
        if (!slr1Conflicts.isEmpty()) {
            System.out.println("The grammar is not parsable with SLR(1)\n");
            for (Conflict conflict : slr1Conflicts) System.out.println(conflict.toString());
        } else {
            String res = parse(slr1Table);
            System.out.println(res);
        }

        System.out.println("-------------------------LR(1)-------------------------");
        if (!lr1Conflicts.isEmpty()) {
            System.out.println("The grammar is not parsable with LR(1)\n");
            for (Conflict conflict : lr1Conflicts) System.out.println(conflict.toString());
        } else {
            String res = parse(lr1Table);
            System.out.println(res);
        }

        writeFirstFollow();
    }

    /**
     * init
     **/
    private static void init() throws FileNotFoundException {
        grammar = new ArrayList<>();
        nonTerminals = new HashMap<>();
        columnsMap = new HashMap<>();
        llRows = new HashMap<>();
        llColumns = new HashMap<>();
        numStack = new Stack<>();
        tokenStack = new Stack<>();

        /**
         * symbols must be separated by space
         * there must be a single space at the end of each rule
         * epsilon is shown with '#'
         */
        readGrammarFromFile();


        // initial nonTerminals
        String symbol, rule;
        for (int i = 0; i < grammar.size(); i++) {
            rule = grammar.get(i);
            symbol = rule.substring(0, rule.indexOf("->"));
            if (nonTerminals.get(symbol) == null) {
                //if symbol doesn't exist in nonTerminals map, add it
                nonTerminals.put(symbol, new NonTerminal(symbol));
            }
        }

        // add $ to follow set of start symbol
        String firstRule = grammar.get(0);
        String startSymbol = firstRule.substring(0, firstRule.indexOf("->"));
        nonTerminals.get(startSymbol).followSet.add("$");

        // calculate all terminals in grammar
        terminals = terminals();

        // initial columnsMap
        // map terminals or not-terminals to their column indexes in table( for lr(0), slr(1) and lr(1) tables )
        int i = 0;
        // actions
        for (; i < terminals.size(); i++)
            columnsMap.put(terminals.get(i), i);
        // GOTO
        for (NonTerminal nonTerm : nonTerminals.values()) {
            columnsMap.put(nonTerm.name, i);
            i++;
        }

        // LL(1) table rows and columns
        // rows
        int j = 0;
        for (NonTerminal nonTerm : nonTerminals.values()) {
            llRows.put(nonTerm.name, j);
            j++;
        }
        // columns
        for (j = 0; j < terminals.size(); j++)
            llColumns.put(terminals.get(j), j);

        // read tokens from file
        tokens = readTokens();
    }

    private static ArrayList<Token> readTokens() throws FileNotFoundException {
        ArrayList<Token> tokens = new ArrayList<>();
        Scanner sc = new Scanner(new File("tokens2.txt"));
        while (sc.hasNext()) {
            String line = sc.nextLine();
            tokens.add(new Token(line.trim()));
        }
        return tokens;
    }

    /***
     * @param type can be 'lr0', 'lr1', 'slr1'
     */
    private static String[][] createTable(ArrayList<State> dfa, String type, ArrayList<Conflict> conflicts) {
        String[][] table = new String[dfa.size()][columnsMap.size()];
        State state;
        for (int i = 0; i < dfa.size(); i++) {
            state = dfa.get(i);
            for (Item item : state.itemSet)
                if (item.rule.indexOf("@") + 1 == item.rule.length()) {
                    // it's a reducible item

                    // determines for which terminals this item should be reduced based on parser type
                    ArrayList<String> terminalsToReduce;
                    switch (type) {
                        case LR0:
                            terminalsToReduce = terminals();
                            break;
                        case SLR1:
                            terminalsToReduce = followSet(item.rule.substring(0, item.rule.indexOf("->")));
                            break;
                        case LR1:
                            terminalsToReduce = item.lookahead;
                            break;
                        default:
                            terminalsToReduce = new ArrayList<>();
                    }

                    String firstRule = grammar.get(0);
                    String startSymbol = firstRule.substring(0, firstRule.indexOf("->"));

                    for (String terminal : terminalsToReduce) {
                        int column = columnsMap.get(terminal);
                        if (item.rule.startsWith(startSymbol + "->") && terminal.equals("$")) {
                            table[i][column] = "acc";
                            break;
                        } else {
                            // reduce
                            if (table[i][column] != null)
                                // reduce/reduce conflicts
                                conflicts.add(new Conflict(String.valueOf(i), terminal, table[i][column]
                                        , String.format("r%d", item.ruleNum)));
                            table[i][column] = String.format("r%d", item.ruleNum);
                        }
                    }
                }

            // for all types(LR(0), SLR(1), LR(1)) shift & GOTO are the same
            for (String symb : state.moveMap.keySet()) {
                int column = columnsMap.get(symb);
                if (symb.matches("[a-z_]+")) {
                    // it's a non-terminal
                    // GOTO
                    table[i][column] = String.valueOf(state.moveMap.get(symb));
                } else {
                    // it's terminal
                    // shift
                    if (table[i][column] != null)
                        // shift/reduce or shift/shift conflict
                        conflicts.add(new Conflict(String.valueOf(i), symb, table[i][column]
                                , String.format("s%d", state.moveMap.get(symb))));
                    table[i][column] = String.format("s%d", state.moveMap.get(symb));
                }
            }
        }
        return table;
    }

    private static String parse(String[][] table) {
        currentState = 0;
        numStack.clear();
        tokenStack.clear();

        numStack.push(0);
        tokenStack.push("$");

        String result;
        for (int i = 0; i < tokens.size(); i++) {
            result = onTokenReceived(table, tokens.get(i));
            if (result.equals("acc") || result.equals("error")) {
                return result;
            }
            printStacks();
        }
        return null;
    }

    private static String[][] createLLTable(ArrayList<Conflict> conflicts) {
        String[][] llTable = new String[llRows.keySet().size()][llColumns.keySet().size()];
        for (int i = 0; i < grammar.size(); i++) {
            String rule = grammar.get(i);
            String startSymbol = rule.substring(0, rule.indexOf("->"));

            String[] arr = rule.trim().substring(rule.indexOf("->") + 3).split(" ");
            ArrayList<String> terms = new ArrayList<>();
            for (String symb : arr) {
                union(terms, firstSet(symb));
                if (!firstSet(symb).contains("#")) {
                    break;
                }
            }
            terms.remove("#");
            for (String term : terms) {
                if (llTable[llRows.get(startSymbol)][llColumns.get(term)] != null)
                    conflicts.add(new Conflict(startSymbol, term, llTable[llRows.get(startSymbol)][llColumns.get(term)], rule));
                llTable[llRows.get(startSymbol)][llColumns.get(term)] = rule;
            }

            // if first(startSymbol) contains epsilon then write startSymbol-> # for follow set
            if (firstSet(startSymbol).contains("#")) {
                for (String term : followSet(startSymbol)) {
                    String bef = llTable[llRows.get(startSymbol)][llColumns.get(term)];
                    llTable[llRows.get(startSymbol)][llColumns.get(term)] = startSymbol + "->" + " # ";
                    String aft = llTable[llRows.get(startSymbol)][llColumns.get(term)];

                    if (bef != null && !bef.equals(aft))
                        conflicts.add(new Conflict(startSymbol, term, bef, aft));
                }
            }
        }
        return llTable;
    }

    private static String parseLL(String[][] table) {
        tokenStack.clear();
        tokenStack.push("$");
        // push start symbol to stack
        tokenStack.push(grammar.get(0).substring(0, grammar.get(0).indexOf("->")));
        ArrayList<Token> inputStream = new ArrayList<>(tokens);
        String topStack;
        Token token;
        while (!tokenStack.empty()) {
            topStack = tokenStack.peek();
            token = inputStream.get(0);
            System.out.println("\nStack: " + Arrays.toString(tokenStack.toArray()));
            System.out.println("Token: " + token.token);
            if (token.token.equals(topStack)) {
                // if most left token is equal to top stack
                // it will be removed from input stream and will be poped from stack
                inputStream.remove(0);
                tokenStack.pop();
            } else {
                if (topStack.matches("[a-z_]+")) {
                    tokenStack.pop();
                    // if top stack is non terminal
                    String rule = table[llRows.get(topStack)][llColumns.get(token.token)];
                    System.out.println("Rule:  " + rule);
                    if (rule != null && !rule.contains("#")) {
                        String[] arr = rule.substring(rule.indexOf("->") + 2).trim().split(" ");
                        // first pop the non-terminal
                        for (int i = arr.length - 1; i >= 0; i--)
                            // push all the symbols in the rule to the stack from right to left
                            tokenStack.push(arr[i]);
                    } else if (!rule.contains("#"))
                        return "error";
                } else {
                    // if top stack is terminal and it is not equal to most left token of input stream
                    return "error";
                }
            }
        }
        return "acc";
    }

    /**
     * this function will check table whenever a new token receives
     **/
    private static String onTokenReceived(String[][] table, Token token) {
        String tokenName = token.token;
        String element = table[currentState][columnsMap.get(tokenName)];
        if (element == null) {
            return "error";
        } else if (element.startsWith("s")) {
            // shift
            tokenStack.push(tokenName);
            numStack.push(Integer.valueOf(element.substring(1)));
            currentState = numStack.peek();
            return "shifted";
        } else if (element.startsWith("r")) {
            // reduce
            int ruleNum = Integer.valueOf(element.substring(1));
            String rule = grammar.get(ruleNum);

            // pop stack until all the symbols in right hand of the specified grammar has been removed
            if (!rule.contains("#")) {
                int popCount = rule.substring(rule.indexOf("->") + 2).trim().split(" ").length;
                for (int i = 0; i < popCount; i++) {
                    tokenStack.pop();
                    numStack.pop();
                }
            }

            // e.x: A->BCe. reduced
            // push A to tokenStack
            String symb = rule.substring(0, rule.indexOf("->"));
            tokenStack.push(symb);

            //find next state number from GOTO part of table and push it to numStack
            int lastState = numStack.peek();
            int idx = Integer.valueOf(table[lastState][columnsMap.get(symb)]);
            numStack.push(idx);


            currentState = numStack.peek();
            printStacks();
            // until here we just poped stacks and didn't consumed the token
            // so we'll call this function again recursively
            String result = onTokenReceived(table, token);
            if (result != null)
                if (result.equals("acc"))
                    return result;
            return "reduced";
        } else if (element.equals("acc")) {
            // accept
            return "acc";
        } else
            return null;
    }

    /**
     * will add all of items from second list to first list if first list doesn't have them
     */
    private static void union(ArrayList<String> toSet, ArrayList<String> fromSet) {
        if (fromSet != null)
            for (String s : fromSet) {
                if (!toSet.contains(s))
                    toSet.add(s);
            }
    }

    private static String nextSymbol(String rule, int index) {
        try {
            if (rule.indexOf(" ", index) == index)
                return nextSymbol(rule, index + 1);
            else
                return rule.substring(index, rule.indexOf(" ", index));
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * finds the first set of symbol
     **/
    private static ArrayList<String> firstSet(String symbol) {
        if (symbol == null || symbol == "#") {
            return null;
        } else if (!symbol.matches("[a-z_]+")) {
            //it's a terminal
            //return itself
            return new ArrayList<>(Collections.singletonList(symbol));
        } else if (!nonTerminals.get(symbol).firstSet.isEmpty()) {
            //if firstSet of symbol already calculated
            return nonTerminals.get(symbol).firstSet;
        } else {
            ArrayList<String> set = nonTerminals.get(symbol).firstSet;
            for (String rule : grammar) {
                if (rule.startsWith(symbol + "->")) {
                    String nextSymbol = nextSymbol(rule, rule.indexOf("->") + 3);
                    if (nextSymbol.matches("[a-z_]+")) {
                        // it's non-terminal
                        if (nextSymbol.equals(symbol) && !set.contains("#")) {
                            // e.x: A->ABd and first(A) doesn't contain "#"ignore this
                            // if first(A) contains "#" then first(A) += first(B)
                        } else {
                            String[] array = rule.substring(rule.indexOf("->") + 3).trim().split(" ");
                            for (int i = 0; i < array.length; i++) {
                                union(set, firstSet(array[i]));
                                if (!firstSet(array[i]).contains("#"))
                                    break;
                            }
                        }
                    } else {
                        // it's terminal or it's epsilon
                        if (!set.contains(nextSymbol))
                            set.add(nextSymbol);
                    }
                }
            }
            return set;
        }
    }

    /**
     * @return the follow set of symbol
     **/
    private static ArrayList<String> followSet(String symbol) {
        if (symbol == null) {
            return null;
        } else if (!nonTerminals.get(symbol).followSet.isEmpty()) {
            // if firstSet of symbol already calculated return it
            return nonTerminals.get(symbol).followSet;
        } else {
            ArrayList<String> set = nonTerminals.get(symbol).followSet;
            for (String rule : grammar) {
                int index = rule.indexOf("->") + 2;
                while (index < rule.length()) {
                    // this while is needed because of rules like bellow
                    // ex: A->aBFcBg
                    // to calculate follow(B) we should add first(F) and 'g' terminal to follow(B)
                    index = rule.indexOf(" " + symbol + " ", index);
                    if (index > -1) {
                        String nextSymbol = nextSymbol(rule, index + symbol.length() + 1);
                        if (nextSymbol == null) {
                            index += symbol.length() + 1;
                            // if there is no next symbol
                            // it means that symbol is the last token in rule
                            // ex: if rule be like: A->aB
                            // follow(A) would be added to follow(B)
                            if (!rule.substring(0, rule.indexOf("->")).equals(symbol)) {
                                // to prevent loop like ex: A->aA that follow(A) would be added to follow(A) that can make infinite loop
                                union(set, followSet(rule.substring(0, rule.indexOf("->"))));
                            }
                        } else if (nextSymbol.matches("[a-z_]+")) {
                            // next symbol is non-Terminal
                            String[] arr = rule.substring(index + symbol.length() + 1).trim().split(" ");
                            for (int i = 0; i < arr.length; i++) {
                                index += arr[i].length() + 1;
                                union(set, firstSet(arr[i]));
                                if (!firstSet(arr[i]).contains("#")) {
                                    break;
                                }
                            }

                        } else {
                            index += symbol.length() + 1;
                            // next symbol is terminal so add it to follow set
                            if (!set.contains(nextSymbol))
                                set.add(nextSymbol);
                        }
                    } else
                        // if there was no (@param)symbol in right hand of the rule
                        break;
                }
            }
            // remove epsilon from follow set if it is added somehow
            set.remove("#");
            return set;
        }
    }

    /**
     * calculates lookAhead for given item
     */
    private static ArrayList<String> Lookahead(Item parent) {
        ArrayList<String> output = new ArrayList<>();
        String rule = parent.rule;
        ArrayList<String> parentLookSet = new ArrayList<>(parent.lookahead);
        int idx = rule.indexOf("@");
        idx = rule.indexOf(" ", idx) + 1;
        if (idx == rule.length()) {
            //e.x: parent == [A->a.B ,D]
            union(output, parentLookSet);
        } else {
            // e.x: parent == [A->a.BCDE ,x/y]
            String str = rule.substring(idx);
            // e.x: str = CDE
            String[] arr = str.trim().split(" ");
            boolean breaked = false;
            for (int i = 0; i < arr.length; i++) {
                union(output, firstSet(arr[i]));
                if (!firstSet(arr[i]).contains("#")) {
                    breaked = true;
                    break;
                }
            }

            if (output.contains("#"))
                output.remove("#");

            if (!breaked) {
                // e.x : e.x: parent == [A->a.BCDE ,a]
                // !breaked means first(CDE) contains "#"
                // so all of parent lookahead also should be added to output
                union(output, parentLookSet);
            }
        }
        return output;
    }

    /**
     * in state's item set, it will check if this item exists or not
     * if exists it will add all of item lookaheads to existing item and returns true
     * else if doesn't exist will return false
     */
    private static boolean itemSetContains(ArrayList<Item> set, Item item) {
        for (int i = 0; i < set.size(); i++) {
            if (set.get(i).rule.equals(item.rule)) {
                union(set.get(i).lookahead, item.lookahead);
                return true;
            }
        }
        return false;
    }

    /**
     * @param lr0_1 can be 0 or 1
     * @value 0 is for LR(0) or SLR(1)
     * @value 1 is for LR(1) or LALR(1)
     */
    private static ArrayList<State> generateDFA(int lr0_1) {
        ArrayList<State> dfa = new ArrayList<>();
        Queue<State> queue = new LinkedList<>();
        //create first state
        Item item = new Item(grammar.get(0), 0);
        if (lr0_1 == 1)
            item.lookahead.add("$");
        State firstState = createState(new ArrayList<>(Collections.singletonList(item)), lr0_1);
        firstState.stateNum = 0;
        dfa.add(firstState);
        queue.add(firstState);

        ArrayList<String> moves;
        State currState, newState;
        while (!queue.isEmpty()) {
            currState = queue.poll();
            // all symbols that cause move from currState to another state
            moves = possibleMoves(currState);
            for (int i = 0; i < moves.size(); i++) {
                newState = createState(movesBySymbol(moves.get(i), currState), lr0_1);
                int result = dfaContains(dfa, newState, lr0_1);
                if (result != -1) {
                    // state exists
                    currState.moveMap.put(moves.get(i), result);
                } else {
                    // new state created
                    dfa.add(newState);
                    queue.add(newState);
                    newState.stateNum = dfa.size() - 1;
                    currState.moveMap.put(moves.get(i), dfa.size() - 1);
                }
            }
        }
        return dfa;
    }

    private static State createState(ArrayList<Item> items, int lr0_1) {
        State state = new State();
        state.itemSet.addAll(closure(items, lr0_1));
        return state;
    }

    private static ArrayList<Item> closure(ArrayList<Item> items, int lr0_1) {
        ArrayList<Item> set = new ArrayList<>(items);      // closure set
        // create a queue and add all the items that we want to calculate their closure
        Queue<Item> queue = new LinkedList<>(items);
        while (!queue.isEmpty()) {
            Item parentItem = queue.poll();
            String rule = parentItem.rule;
            if (nextSymbol(rule, rule.indexOf("@") + 1) == null) {
                //do nothing with this item because it's a Reducible item
            } else {
                int idx = rule.indexOf("@");
                String nextSymb = nextSymbol(rule, idx + 1);
                if (nextSymb.matches("[a-z_]+")) {
                    // if it's a non-Terminal
                    for (int i = 0; i < grammar.size(); i++) {
                        String pr = grammar.get(i);
                        if (pr.startsWith(nextSymb + "->")) {
                            // for all rules that starts with nextSymb
                            Item newItem = new Item(pr, i);
                            if (lr0_1 == 1)
                                newItem.lookahead = Lookahead(parentItem);
                            if (!itemSetContains(set, newItem)) {
                                set.add(newItem);
                                queue.add(newItem);
                            }
                        }
                    }
                }
            }
        }
        return set;
    }

    private static boolean areStatesEqual(State a, State b, int lr0_1) {
        if (a.itemSet.size() == b.itemSet.size()) {
            for (int i = 0; i < b.itemSet.size(); i++) {
                if (!itemExist(a.itemSet, b.itemSet.get(i), lr0_1))
                    return false;
            }
            return true;
        } else return false;
    }

    private static boolean itemExist(ArrayList<Item> set, Item item1, int lr0_1) {
        for (int i = 0; i < set.size(); i++) {
            Item item2 = set.get(i);
            if (item2.rule.equals(item1.rule))
                if (lr0_1 == 0)
                    return true;
                else if (lr0_1 == 1 && item2.lookahead.size() == item1.lookahead.size()) {
                    for (int j = 0; j < item1.lookahead.size(); j++) {
                        if (!item2.lookahead.contains(item1.lookahead.get(j)))
                            return false;
                    }
                    return true;
                }
        }
        return false;
    }

    /**
     * @return index of state in dfa if state exists
     * or -1 if doesn't exists
     **/
    private static int dfaContains(ArrayList<State> dfa, State state, int lr0_1) {
        for (int i = 0; i < dfa.size(); i++) {
            if (areStatesEqual(dfa.get(i), state, lr0_1))
                return i;
        }
        return -1;
    }

    /**
     * @return all possible moves that this state can have
     */
    private static ArrayList<String> possibleMoves(State state) {
        ArrayList<String> moves = new ArrayList<>();
        String symb, rule;
        int idx;
        for (int i = 0; i < state.itemSet.size(); i++) {
            rule = state.itemSet.get(i).rule;
            idx = rule.indexOf("@");
            if (idx + 1 != rule.length()) {
                // if the item isn't reducible
                symb = rule.substring(idx + 1, rule.indexOf(" ", idx));     // symb is the first symbol after '@'
                if (!moves.contains(symb))
                    moves.add(symb);
            }
        }
        return moves;
    }

    /**
     * @return items that can move to next step by (@param)symbol in state will be moved to next step
     * and all the itemSet would be returned
     **/
    private static ArrayList<Item> movesBySymbol(String symbol, State state) {
        ArrayList<Item> items = new ArrayList<>();
        for (Item item : state.itemSet) {
            String rule = item.rule;
            if (rule.contains("@" + symbol + " ")) {
                String newRule;
                // create new item after moving '@' forward
                if (rule.indexOf(" ", rule.indexOf("@")) + 1 == rule.length()) {
                    // the item will become reducible item after move
                    newRule = rule.substring(0, rule.indexOf("@"))
                            + " " + symbol + "@";
                } else {
                    newRule = rule.substring(0, rule.indexOf("@"))
                            + " " + symbol + "@" + rule.substring(rule.indexOf(" ", rule.indexOf("@")) + 1);
                }
                items.add(new Item(newRule, item.ruleNum, new ArrayList<>(item.lookahead)));
            }
        }
        return items;
    }

    /**
     * @return all terminals
     */
    private static ArrayList<String> terminals() {
        ArrayList<String> terminals = new ArrayList<>();
        for (int i = 0; i < grammar.size(); i++) {
            String rule = grammar.get(i);
            rule = rule.substring(rule.indexOf("->") + 3);
            String[] array = rule.split(" ");
            for (String symb : array) {
                if (!symb.matches("[a-z_]+") && !symb.equals("#")) {
                    // it's terminal
                    if (!terminals.contains(symb))
                        // if it doesn't already exist in terminals
                        terminals.add(symb);
                }
            }
        }
        terminals.add("$");
        return terminals;
    }

    /**
     * print stacks
     */
    private static void printStacks() {
        System.out.println("numStack:   " + Arrays.toString(numStack.toArray()));
        System.out.println("tokenStack: " + Arrays.toString(tokenStack.toArray()) + '\n');
    }

    private static void writeDFA(ArrayList<State> dfa, String fileName) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
            for (State s : dfa)
                writer.write(s.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeFirstFollow() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("FF.txt"));
            writer.write("First:" + "\n");
            for (NonTerminal nonTerm : nonTerminals.values()) {
                writer.write(String.format("%20s -> %s\n", nonTerm.name, firstSet(nonTerm.name).toString()));
            }
            writer.write("Follow:" + "\n");
            for (NonTerminal nonTerm : nonTerminals.values()) {
                writer.write(String.format("%20s -> %s\n", nonTerm.name, followSet(nonTerm.name).toString()));
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void readGrammarFromFile() throws FileNotFoundException {
        Scanner scanner = new Scanner(new File("gram2.txt"));
        Pattern nonTermPattern = Pattern.compile("<[a-z_]+>");
        System.out.println("-------------------------Grammar-------------------------");
        while (scanner.hasNext()) {
            String line = scanner.nextLine();
            String[] arr = line.substring(line.indexOf("::=") + 3).split("\\|");
            String firstPart = line.substring(0, line.indexOf("::=")).trim();
            for (String second : arr) {
                String rule;
                rule = firstPart + "-> " + second.trim();
                rule = rule.replace("\"", "");
                Matcher m = nonTermPattern.matcher(rule);
                while (m.find()) {
                    String str = m.group();
                    str = str.replace("<", "").replace(">", "");
                    int start = m.start();
                    int end = m.end() - 1;
                    rule = rule.substring(0, start) + str + rule.substring(end + 1);
                    m = nonTermPattern.matcher(rule);
                }
                rule = rule + " ";
                System.out.println(rule);
                grammar.add(rule);
            }
        }
    }

    public static void writeTable(String[][] table, String fileName) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
            writer.write(String.format("%-15s", "state number"));
            for (int i = 0; i < table[1].length; i++) {
                for (String key : columnsMap.keySet()) {
                    if (columnsMap.get(key) == i) {
                        writer.write(String.format("%-15s", key));
                        break;
                    }
                }
            }
            writer.write('\n');
            for (int i = 0; i < table.length; i++) {
                writer.write(String.format("%-15d", i));
                for (int j = 0; j < table[i].length; j++) {
                    writer.write(String.format("%-15s", table[i][j]));
                }
                writer.write("\n");
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeLLTable(String[][] llTable) { 
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("ll1 Table.txt"));
            writer.write(String.format("%-30s", " "));
            for (int i = 0; i < llTable[0].length; i++) {
                for (String key : columnsMap.keySet()) {
                    if (columnsMap.get(key) == i) {
                        writer.write(String.format("%-30s", key));
                        break;
                    }
                }
            }
            writer.write("\n");
            for (String nonTerm : llRows.keySet()) {
                writer.write(String.format("%-30s", nonTerm));
                for (int i = 0; i < llTable[0].length; i++) {
                    writer.write(String.format("%-30s", llTable[llRows.get(nonTerm)][i]));
                }
                writer.write("\n");
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}