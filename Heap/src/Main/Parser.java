package Main;

import java.util.*;

import Helpers.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.swing.plaf.nimbus.State;

/**
 * A class that takes in a stream of tokens from the Main.Lexer and parses them
 * into an Abstract Syntax Tree (AST), which is easier to run through
 * when interpreting.
 */
public class Parser {

    /** *************************************************************************************************
     *  FORMALITIES
     *  Basic formalities for the parser
     */

    public Parser(Lexer lexer) {
        _lexer = lexer;
        _current = lexer.next();
        _peekTokens = new ArrayDeque();
    }

    /** Entry method into Main.Parser - considers program as Block and parses it */
    public ASTNode parse() {
        return parseBlock();
    }

    /** Peeks the next token from the Lexer stream */
    private Token peek() {
        if (!peekTokens().isEmpty()) {
            return (Token) peekTokens().peek();
        } else {
            Token peekToken = _lexer.next();
            peekTokens().offer(peekToken);
            return peekToken;
        }
    }

    /**
     * Consumes a token from the lexer, if the passed in
     * token is equivalent to _current
     * @param token A token to compare to _current to
     *              determine whether to proceed
     */
    private void eat(Token token) {
        if (!peekTokens().isEmpty()) {
            current((Token) peekTokens().poll());
        } else if (token.equals(current())) {
            current(_lexer.next());
        } else {
            System.out.println("Unknown token");
        }
    }

    private void eat(Token[] tokens) {
        for (Token token : tokens) {
            if (!peekTokens().isEmpty()) {
                current((Token) peekTokens().poll());
                return;
            } else if (token.equals(current())) {
                current(_lexer.next());
                return;
            }
        }
        System.out.println("Unknown token");
    }

    private void eat(String name) {
        if (values.get(name).equals(current())) {
            current(_lexer.next());
        } else {
            System.out.println("Unknown token");
        }
    }

    private void eat(String[] names) {
        for (String name : names) {
            if (values.get(name).equals(current())) {
                current(_lexer.next());
                return;
            }
        }
        System.out.println("Unknown token");
    }

    private Token current() {
        return _current;
    }

    private void current(Token current) {
        this._current = current;
    }

    private ArrayDeque peekTokens() { return _peekTokens; }

    /**
     * Checks if _current is a token type by the token name
     * @param names List of token string names to check
     * @return If _current is any of the passed in token names
     */
    private boolean currentIs(String... names) {
        return Arrays.stream(names).anyMatch(name -> current().equals(values.get(name)));
    }

    /**
     * Check if _current is a token type by passing in the token itself
     * @param tokens List of tokens to check _current against
     * @return Whether _current is any of the passed in tokens
     */
    private boolean currentIs(Token... tokens) {
        return Arrays.stream(tokens).anyMatch(token -> current().equals(token));
    }


    /** A map of usable tokens that aren't dependent on user code, for comparisons. Avoids the
     * creation of unnecessary tokens. */
    public static final Map<String, Token> values = new HashMap<>() {{
        for (Token.TokenType type : Token.TokenType.values()) {
            if (type != Token.TokenType.VAR && type != Token.TokenType.INT_VAL &&
                    type != Token.TokenType.STR_VAL &&  type != Token.TokenType.REAL_VAL) {
                put(type.toString(), new Token(type));
            }
        }
    }};

    private Lexer _lexer;
    private Token _current;
    private ArrayDeque _peekTokens;




    private Block parseBlock(Token... endTokens) {
        List<Statement> statements = new ArrayList<>();
        List tokens = Arrays.asList(endTokens);
        while (!_lexer.isEmpty() && !tokens.contains(current())) {
            statements.add(parseStatement(values.get("EOL")));
        }
        return new Block(statements);
    }

    private Block parseClassBlock(Token... endTokens) {
        List<Statement> statements = new ArrayList<>();
        List tokens = Arrays.asList(endTokens);
        while (!tokens.contains(current())) {
            Statement statement = parseStatement(values.get("EOL"));
            assert statement instanceof Assignment;
            statements.add(statement);
        }
        return new Block(statements);
    }

    private Statement parseStatement(Token... endTokens) {
        Statement statement;
        if (current().isDirect()) {
            return parseDirect(endTokens);
        } else if (currentIs("RETURN")) {
            statement = parseReturn();
        } else if (currentIs("DEFER")) {
            statement = parseDefer(endTokens);
        } else if (currentIs("PASS")) {
            eat("PASS");
            statement = new Pass();
        } else if (currentIs("CONTINUE")) {
            eat("CONTINUE");
            statement = new Continue();
        } else if (currentIs("BREAK")) {
            eat("BREAK");
            statement = new Break();
        } else if (currentIs("ANNOTATION")) {
            statement = parseAssignment(endTokens);
        } else {
            statement = parseExpression(true, endTokens);
        }

        if (endTokens.length > 0)
            eat(endTokens);
        return statement;
    }

    private Return parseReturn(Token... endTokens) {
        eat("RETURN");
        Expression expression = parseExpression(endTokens);
        return new Return(expression);
    }

    private Defer parseDefer(Token... endTokens) {
        eat("DEFER");
        Expression expression = parseExpression(endTokens);
        return new Defer(expression);
    }

    private Assignment parseAssignment(Token... endTokens) {
        return parseAssignment(parseDeclare(false), endTokens);
    }

    private Assignment parseAssignment(ASTNode var, Token... endTokens) {
        boolean cast = false;
        if (currentIs("ASSIGN")) {
            eat("ASSIGN");
        } else if (currentIs("CAST_ASSIGN")) {
            eat("CAST_ASSIGN");
            cast = true;
        }

        // TODO make sure expression isn't assignment when casting!
        Expression expression = parseExpression(endTokens);
        return new Assignment(var, expression, cast);
    }

    private List<Type> parseType(Token endToken) {
        List<Type> types = new ArrayList<Type>();

        while(!currentIs(endToken)) {

            /* Check if is a pointer */
            boolean isPointer = false;
            if (current().type() == Token.TokenType.MULTIPLY) {
                eat("MULTIPLY");
                isPointer = true;
            }

            /* The type of the token */
            String typeString = current().type().toString();

            /* Check if is a container */
            List<String> containerTypes = new ArrayList<String>() {{
                add("ARR_OPEN"); add("ARR_OPEN"); add("ARR_OPEN");
                add("SCOPE_OPEN"); add("SET_OPEN");
                add("UNDIR_OPEN"); add("DIR_OPEN");
            }};

            if (containerTypes.contains(current().type().toString())) {
                /* Take out the "_OPEN" part of type */
                typeString = typeString.substring(typeString.length() - 5);
                /* Convert between scope token and more definite map */
                if (typeString.equals("SCOPE")) {
                    typeString = "MAP";
                }

                types.add(parseContainerType(typeString, isPointer));
                continue;
            }

            /* Check if is a short hand container */
            List<String> shortHandContainerTypes = new ArrayList<String>() {{
                add("ARR_TYPE"); add("L_ARR_TYPE"); add("DL_ARR_TYPE");
                add("UNDIR_TYPE"); add("DIR_TYPE"); add("MAP_TYPE");
                add("SET_TYPE");
            }};

            if (shortHandContainerTypes.contains(current().type().toString())) {
                typeString = typeString.substring(typeString.length() - 5);
                /* A new container type with no internal types */
                types.add(new ContainerType(isPointer, ContainerType.Container.valueOf(typeString), new ArrayList<>()));
                continue;
            }

            /* Check if is a shorthand for graph */
            if (current().type() == Token.TokenType.DIR_TYPE) {
                eat("DIR_GRAPH_TYPE");
                List<Type> containedType = new ArrayList<>();
                types.add(new ContainerType(isPointer, ContainerType.Container.DIR, containedType));
                continue;
            } else if (current().type() == Token.TokenType.UNDIR_TYPE) {
                eat("UNDIR_GRAPH_TYPE");
                List<Type> containedType = new ArrayList<>();
                types.add(new ContainerType(isPointer, ContainerType.Container.UNDIR, containedType));
                continue;
            }

            /* Check current is either a built-in type, or a user made type
            * that isn't reserved */
            assert current().isType() || !current().isReserved();
            types.add(new Type(current(), isPointer));
            eat(current());

            if(!currentIs(endToken)) {
                eat("COMMA");
            }
        }

        return types;
    }

    private ContainerType parseContainerType(String containerType, boolean isPointer) {
        eat(values.get(containerType + "_OPEN"));
        List<Type> containerTypes =  parseType(values.get(containerType + "_CLOSE"));
        eat(values.get(containerType + "_CLOSE"));
        return new ContainerType(isPointer, ContainerType.Container.valueOf(containerType), containerTypes);
    }

    private ParamDefs parseParamDefs(Token endBlock) {
        List<Assignment> paramDefs = new ArrayList<>();
        while (!current().equals(endBlock)) {
            paramDefs.add(parseAssignment(values.get("COMMA")));
            eat("COMMA");
        }
        return new ParamDefs(paramDefs);
    }

    private Declare parseDeclare(Var var) {
        eat("LESS_THAN");
        List<Type> types = parseType(values.get("GREATER_THAN"));
        eat("GREATER_THAN");

        return new Declare(new ArrayList<>(), var, types);
    }

    private ASTNode parseDeclare(boolean forceType) {
        List<Modifier> modifiers = new ArrayList<>();
        while (current().isModifier()) {
            modifiers.add(new Modifier(current()));
            eat(current());
        }

        Expression var = new Var(current());
        eat(current());

        List<Type> types = new ArrayList<>();
        if (currentIs("LESS_THAN") || forceType) {
            eat("LESS_THAN");
            types = parseType(values.get("GREATER_THAN"));
            eat("GREATER_THAN");
        } else {
            var = postFix(var);
        }

        if (modifiers.isEmpty() && types.isEmpty()){
            return var;
        }
        return new Declare(modifiers, (Var) var, types);
    }


    /** *************************************************************************************************
     *  EXPRESSIONS
     *  Strings of chained logic that includes operators, property calls, indexes, and references
     */

    private static Token[] afterTokens = new Token[] {
            values.get("ARR_OPEN"),
            values.get("PERIOD"),
            values.get("PAR_OPEN")
    };

    private Expression parseExpression(Token... endTokens) {
        // TODO make sure expression isn't assignment when casting!
        return (Expression) parseExpression(false, endTokens);
    }

    /** Entry point for all expression parsing
     * */
    private Statement parseExpression(boolean canBeAssignment, Token... endTokens) {
        Expression expression = parseTerm(endTokens);
        Token current;
        do {
            if (currentIs("ASSIGN", "CAST_ASSIGN")) {
                assert(canBeAssignment); // TODO raise error
                return parseAssignment(expression, endTokens);
            } else if (currentIs("LESS_THAN") && peek().isType() && expression instanceof Var) {
                //Can only instantiate with a single identifier
                assert(canBeAssignment); // TODO raise error
                return parseAssignment(parseDeclare((Var) expression));
            }

            if (current().isOperator()) {
                if (currentIs("TERNARY")) {
                    eat("TERNARY");
                    Expression first = parseExpression(values.get("COLON"));
                    eat("COLON");
                    Expression second = parseExpression(endTokens);
                    expression = new TernaryOp(expression, first, second, values.get("TERNARY"));
                } else {
                    current = current();
                    eat(current);
                    expression = new BinaryOp(expression, parseTerm(endTokens), current);
                }
            }
        } while (!currentIs(endTokens));

        expression = parsePseudoBinary(expression);
        return expression;
    }

    /**
     * Takes in a expression tree in a weave (a series of operations branching
     * through the left side of the tree, such as one made initially by
     * parseExpression). Applies operator precedence to the tree by shuffling
     * nodes to make sure that higher precedence operators are lower in the
     * tree, and therefore are executed first by the interpreter.
     *
     * Terms are leaves (at least in this abstraction - terms can contain their
     * own expressions, but because they are bounded by parenthesis, they are
     * automatically a higher precedence than anything in this expression,so we
     * can ignore them as leaves. They can also contain unitary operators,
     * which also have higher precedence.
     *
     * Example -> -g + f + e**d * c**b + a ? x : y
     *
     * Converts this (what I call a weave):
     *
     *                               ?
     *                            /  |  \
     *                           +   x   y
     *                          / \
     *                        **   a
     *                        / \
     *                      *    b
     *                     / \
     *                   **   c
     *                  / \
     *                 +   d
     *                / \
     *               +   e
     *              / \
     *             -   f
     *            /
     *           g
     *
     * Into this:
     *
     *                  ?
     *               /  |  \
     *              x   +   y
     *                /   \
     *               +    a
     *             /   \
     *           +      *
     *         /  \    /  \
     *        -    f  **   **
     *       /       /  \ /  \
     *      g       e  d  c   b
     *
     *
     * This works for unary, binary, and ternary operations. For unary, both
     * left and right point to its only child, and for ternary operations,
     * left and right are also unchanged, with center usually not cause for
     * concern.
     *
     * *** MUTATIVE ***
     *
     * @param node Head of expression tree
     */
    private Expression parsePseudoBinary(Expression node) {
        if (!(node instanceof Op)) {
            return node;
        }

        Op op = (Op) node;
        Expression left = node;

        /* Run down left side of tree (which here in the tree is only non-trivial side)
         * until you hit a node with an equal to or lower precedence than the head node
         */
        do {
            left = ((Op) left).left();
        } while (left instanceof Op && !op.higherPrecedenceThan(left));

        Op opLeft;
        if (!(left instanceof Op)) {
            return node;
        } else {
            opLeft = (Op) left;
        }


        /* If left has the same precedence as the root op,
         * then op can stay at its relative position
         *
         * Point op's left child to left, point left's right child
         * to op's left child, point op's parent's left child
         * to left's right child, and recurse onto left's new right
         * child
         */
        if (op.equalPrecedenceTo(opLeft)) {
            ((Op) opLeft.parent()).setLeft(opLeft.right());

            if (!(opLeft instanceof UnaryOp))
                parsePseudoBinary(op.left());

            opLeft.setRight(op.left());
            op.setLeft(opLeft);
        }

        /* If left has a lower precedence than root op,
         * then left and op needs to switch
         *
         * Point op's parent's child (left or right) to left,
         *
         */
        else {
            if (op.parent() != null) {
                if (((Op) op.parent()).left() == op) {
                    ((Op) op.parent()).setLeft(opLeft);
                } else {
                    ((Op) op.parent()).setRight(opLeft);
                }
            }
            ((Op) opLeft.parent()).setLeft(opLeft.right());
            parsePseudoBinary(op);
            opLeft.setRight(op);
            op = opLeft;
        }

        /* Recurse down the left side */
        parsePseudoBinary(left);
        return op;
    }


    /**
     * Parses terms, which are expressions that denote:
     *  1. variables or literals with property calls, unary operations, or indexes applied,
     *  2. nested expressions within parenthesis with same applications,
     *  3. container literals,
     *  4. construct definitions.
     * ** Except in special cases, use with postfix() to get full term **
     * @param endTokens A token to check for to see if the expression is finished
     * @return A term, which can later be combined with other terms
     */
    private Expression parseTerm(Token... endTokens) {
        /* Expression is over */
        if (currentIs(endTokens)) {
            return noop;
        }

        Expression expression = noop;

        if (current().isLiteral()) {
            expression = parseLiteral();
            if (!(expression instanceof StringLiteral)) {
                return expression;
            }
        } else if (current().isVar()) {
            expression = new Var(current());
            eat(current());
        }

        /* Is a nested expression */
        else if (currentIs(values.get("PAR_OPEN"))) {
            eat("PAR_OPEN");
            expression = parseExpression(values.get("PAR_CLOSE"));
            eat("PAR_CLOSE");
        }

        /* Prefix unary operators */
        else if (currentIs(values.get("MULTIPLY"))) {
            eat("MULTIPLY");
            expression = new UnaryOp(parseTerm(afterTokens), values.get("POINT"));
        } else if (currentIs("SUBTRACT")) {
            eat("SUBTRACT");
            expression = new UnaryOp(parseTerm(afterTokens), values.get("SUBTRACT"));
        } else if (currentIs("ADD")) {
            eat("ADD");
            return parseTerm(endTokens);
        } else if (currentIs("L_NOT")) {
            eat("L_NOT");
            expression = new UnaryOp(parseTerm(afterTokens), values.get("L_NOT"));
        } else if (currentIs("B_NOT")) {
            eat("B_NOT");
            expression = new UnaryOp(parseTerm(afterTokens), values.get("B_NOT"));
        } else if (currentIs("INCREMENT")) {
            eat("INCREMENT");
            expression = new UnaryOp(parseTerm(afterTokens), values.get("INCREMENT"), Meta.PREFIX);
        } else if (currentIs("DECREMENT")) {
            eat("DECREMENT");
            expression = new UnaryOp(parseTerm(afterTokens), values.get("DECREMENT"), Meta.PREFIX);
        }

        /* Creation expressions */
        else if (current().isConstruct()) {
            return parseConstruct();
        } else if (current().isContainer()) {
            return (Container) parseContainer();
        }

        /* Any term that has not already been returned can have after-effects (property
         * calls, after-unary operators, and indexes) */
        return postFix(expression);
    }

    /**
     * Handles postfix operations, such as property calls, method calls, and postfix operators
     * @param expression Expression to attach postfix operations on
     * @return A full term made up of expression and added postfix operations
     */
    private Expression postFix(Expression expression) {
        if (currentIs("ARR_OPEN")) {
            return postFix((Expression) parseIndex(expression));
        } else if (currentIs("PERIOD")) {
            return postFix(parseProperty(expression, false));
        } else if (currentIs("OPT_CHAIN")) {
            return postFix(parseProperty(expression, true));
        } else if (currentIs("PAR_OPEN")) {
            return parseCall(expression);
        } else if (currentIs("INCREMENT")) {
            eat("INCREMENT");
            return postFix(new UnaryOp(expression, values.get("INCREMENT"), Meta.POSTFIX));
        } else if (currentIs("DECREMENT")) {
            eat("DECREMENT");
            return postFix(new UnaryOp(expression, values.get("DECREMENT"), Meta.POSTFIX));
        } else if (currentIs("DEREF")) {
            eat("DEREF");
            return postFix(new UnaryOp(expression, values.get("DEREF")));
        } else if (currentIs("TOTAL_REF")) {
            eat("TOTAL_REF");
            return postFix(new UnaryOp(expression, values.get("TOTAL_REF")));
        } else if (currentIs("ROUND")) {
            eat("ROUND");
            if (current().isLiteral()) {
                return new BinaryOp(expression, parseLiteral(), values.get("ROUND"));
            } else if (current().isVar()) {
                return new BinaryOp(expression, new Var(current()), values.get("ROUND"));
            }
            return new UnaryOp(expression, values.get("ROUND"));
        } else {
            return expression;
        }
    }

    private ArrayOp parseIndex(Expression var) {
        eat("ARR_OPEN");
        Expression index = parseExpression(values.get("ARR_CLOSE"), values.get("COLON"));

        if (currentIs("COLON")) {
            eat("COLON");
            Expression stop = parseExpression(values.get("ARR_CLOSE"), values.get("COLON"));

            if (currentIs("COLON")) {
                eat("COLON");
                Expression step = parseExpression(values.get("ARR_CLOSE"));
                return new Slice(var, index, stop, step);
            }

            eat("ARR_CLOSE");
            return new Slice(var, index, stop, noop);
        }

        eat("ARR_CLOSE");
        return new Index(var, index);
    }

    private Get parseProperty(Expression var, boolean optChain) {
        eat("PERIOD");
        if (current().isVar()) {
            Var getVar = new Var(current());
            eat(current());
            return new Get(var, getVar, optChain);
        }
        eat("PAR_OPEN");
        Get get = new Get(var, parseExpression(values.get("PAR_CLOSE")), optChain);
        eat("PAR_CLOSE");
        return get;
    }

    private Call parseCall(Expression var) {
        eat("PAR_OPEN");
        List<Expression> expressions = new ArrayList<>();
        while(!currentIs("PAR_CLOSE")) {
            expressions.add(parseExpression(values.get("COMMA"), values.get("PAR_CLOSE")));
        }
        eat("PAR_CLOSE");
        return new Call(new Params(expressions), var);
    }

    private Literal parseLiteral() {
        Token current = current();
        eat(current);

        if (current.type().equals(Token.TokenType.INT_VAL)) {
            return new IntLiteral(current);
        } else if (current.type() == Token.TokenType.REAL_VAL) {
            return new RealLiteral(current);
        } else if (current.type() == Token.TokenType.STR_VAL) {
            return new StringLiteral(current);
        } else if (current.type() == Token.TokenType.TRUE || current.type() == Token.TokenType.FALSE) {
            return new BooleanLiteral(current);
        } else if (current.type().equals(Token.TokenType.NULL)) {
            return new NullLiteral(current);
        }
        return null;
    }


    /** *************************************************************************************************
     *  DIRECTS
     *  Blocks that direct control flow. Only scheme that isn't set to a variable.
     */


    private Direct parseDirect(Token... endTokens) {
        if (current().type() == Token.TokenType.IF) {
            return parseIf(endTokens);
        } else if (currentIs("LOOP")) {
            return parseLoop();
        } else if (current().type() == Token.TokenType.SWITCH) {
            return parseSwitch();
        } else if (current().type() == Token.TokenType.TRY) {
            return parseTry();
        }
        // oops
        return null;
    }

    private Loop parseLoop() {
        eat("LOOP");

        //TODO

        List<Assignment> initClauses = new ArrayList<>();
        while(!currentIs("COLON")) {
            initClauses.add(parseAssignment(values.get("COLON"), values.get("COMMA"), values.get("PAR_CLOSE")));
            if (currentIs("COMMA")) {
                eat("COMMA");
            } else if (currentIs("PAR_CLOSE")) {
                return parseLoopFinish(new ArrayList<Assignment>(), null, new ArrayList<Expression>());
            }
        }
        eat("COLON");

        List<Expression> breakClauses = new ArrayList<>();
        while(!currentIs("COLON")) {
            breakClauses.add(parseExpression(values.get("COMMA"), values.get("COLON")));
            if (currentIs("COMMA")) {
                eat("COMMA");
            }
        }
        eat("COLON");

        List<Expression> loopClauses = new ArrayList<>();
        while(!currentIs("PAR_CLOSE")) {
            loopClauses.add(parseExpression(values.get("COMMA"), values.get("PAR_CLOSE")));
            if (currentIs("COMMA")) {
                eat("COMMA");
            }
        }

        return parseLoopFinish(initClauses, breakClauses, loopClauses);
    }

    private Loop parseLoopFinish(List<Assignment> initClauses, List<Expression> breakClauses, List<Expression> loopClauses) {

        eat("DIRECT");

        DirectBody loopBlock;
        if (currentIs("SCOPE_OPEN")) {
            eat("SCOPE_OPEN");
            loopBlock = parseBlock(values.get("SCOPE_CLOSE"));
            eat("SCOPE_CLOSE");
        } else {
            loopBlock = parseStatement(values.get("EOL"));
        }


        DirectBody elseBlock = noop;
        if (currentIs("ELSE")) {
            eat("SCOPE_OPEN");
            elseBlock = parseBlock(values.get("SCOPE_CLOSE"));
            eat("SCOPE_CLOSE");
        }

        return new Loop(initClauses, breakClauses, loopClauses, loopBlock, elseBlock);
    }

    private If parseIf(Token... endTokens) {
        eat("IF");

        Expression expression = parseExpression(values.get("PAR_CLOSE"));

        eat("DIRECT");

        DirectBody block;

        if (currentIs("SCOPE_OPEN")) {
            eat("SCOPE_OPEN");
            block = parseBlock(values.get("SCOPE_CLOSE"));
            eat("SCOPE_CLOSE");
        } else {
            block = parseStatement(endTokens);
        }

        List<IfBlock> ifblocks = new ArrayList<>() {{
            add(new IfBlock(expression, block));
        }};

        /* Create an else block to be populated */
        DirectBody elseBlock = null;

        /* Check if (an) else if(s), or just an else, exist */
        if (currentIs("ELSE")) {
            eat("ELSE");

            if (currentIs("DIRECT")) {
                /* If just an else */
                eat("DIRECT");

                eat("SCOPE_OPEN");
                elseBlock = parseBlock(values.get("SCOPE_CLOSE"));
                eat("SCOPE_CLOSE");
            } else {
                /* If an else if */
                If nextIf = parseIf(endTokens);
                ifblocks.addAll(nextIf.ifblocks);
                elseBlock = nextIf.elseBlock;
            }
        }

        return new If(ifblocks, elseBlock);
    }

    private Direct parseSwitch() {
        eat(values.get("SWITCH"));

        Expression switchExpression = parseExpression(values.get("PAR_CLOSE"));

        eat("DIRECT");

        eat("SCOPE_OPEN");

        List<Case> cases = new ArrayList<>();
        Block defaultBlock = null;
        Block elseBlock = null;

        while (!currentIs("SCOPE_CLOSE")) {
            if (currentIs("CASE")) {
                eat("CASE");
                Expression caseExpression = parseExpression(values.get("COLON"));
                eat("COLON");
                Block caseBlock = parseBlock(values.get("CASE"), values.get("DEFAULT"));

                Case newCase = new Case(caseExpression, caseBlock);
                cases.add(newCase);
            } else if (currentIs("DEFAULT")) {
                eat("DEFAULT");
                eat("COLON");

                defaultBlock = parseBlock(values.get("SCOPE_CLOSE"));
            }
        }

        eat("SCOPE_CLOSE");

        if (currentIs("ELSE")) {
            eat("SCOPE_OPEN");
            elseBlock = parseBlock(values.get("SCOPE_CLOSE"));
            eat("SCOPE_CLOSE");
        }

        return new Switch(switchExpression, cases, defaultBlock, elseBlock);
    }

    private Try parseTry() {
        eat("TRY");

        eat("SCOPE_OPEN");
        Block tryBlock = parseBlock(values.get("SCOPE_CLOSE"));
        eat("SCOPE_CLOSE");

        Declare exception;
        Block catchBlock, elseBlock = null;
        List<Catch> catches = new ArrayList<>();

        while (currentIs("CATCH")) {
            eat("CATCH");

            exception = (Declare) parseDeclare(true);

            eat("SCOPE_OPEN");
            catchBlock = parseBlock(values.get("SCOPE_CLOSE"));
            eat("SCOPE_CLOSE");

            catches.add(new Catch(exception, catchBlock));
        }

        if (currentIs("ELSE")) {
            eat("SCOPE_OPEN");
            elseBlock = parseBlock(values.get("SCOPE_CLOSE"));
            eat("SCOPE_CLOSE");
        }

        return new Try(tryBlock, catches, elseBlock);
    }


    /** *************************************************************************************************
     *  CONSTRUCTS
     *  Reusable constructions that create objects
     */

    private Construct parseConstruct() {
        if (currentIs("FUNC")) {
            return parseFuncDeclare();
        } else if (currentIs("CLASS")) {
            return parseClass();
        } else if (currentIs("STRUCT")) {
            return parseStruct();
        } else if (currentIs("INTERFACE")) {
            return parseInterface();
        } else if (currentIs("ENUM")) {
            return parseEnum();
        }
        return null;
    }

    private Func parseFuncDeclare() {
        eat("FUNC");

        eat("PAR_OPEN");
        ParamDefs paramDefs = parseParamDefs(values.get("PAR_CLOSE"));
        eat("PAR_CLOSE");

        eat("DIRECT");

        ASTNode operations;
        if (currentIs("SCOPE_OPEN")) {
            eat("SCOPE_OPEN");
            operations = parseBlock(values.get("SCOPE_CLOSE"));
            eat("SCOPE_CLOSE");
        } else {
            operations = parseExpression(values.get("EOL"));
        }

        return new Func(paramDefs, operations);
    }

    private Construct constructHelper(boolean hasinstances) {
        eat("PAR_OPEN");

        List<Var> superclasses = new ArrayList<>();
        while (!currentIs("COLON", "PAR_CLOSE")) {
            superclasses.add(new Var(current()));
            eat(current());
            eat("COMMA");
        }

        List<Var> interfaces = new ArrayList<>();
        if (currentIs("COLON")) {
            eat("COLON");
            while (!currentIs("COLON", "PAR_CLOSE")) {
                interfaces.add(new Var(current()));
                eat(current());
                eat("COMMA");
            }
        }
        eat("PAR_CLOSE");

        eat("DIRECT");

        eat("SCOPE_OPEN");

        List<Call> instances = new ArrayList<>();
        if (hasinstances) {
            while (!currentIs("EOL")) {
                Var var = new Var(current());
                eat(current());
                if (currentIs("PAR_OPEN")) {
                    instances.add(parseCall(var));
                } else {
                    instances.add(new Call(new Params(new ArrayList<>()), var));
                }
            }
            eat("EOL");
        }

        Block block = parseClassBlock(values.get("SCOPE_CLOSE"));
        eat("SCOPE_CLOSE");

        if (hasinstances)
            return new Enum(instances, superclasses, interfaces, block);
        else
            return new Class(superclasses, interfaces, block);
    }

    private Class parseClass() {
        eat("CLASS");
        return (Class) constructHelper(false);
    }

    private Struct parseStruct() {
        eat("STRUCT");
        return (Struct) constructHelper(false);
    }

    private Interface parseInterface() {
        eat("INTERFACE");

        eat("PAR_OPEN");
        List<Var> interfaces = new ArrayList<>();
        while (!currentIs("COLON", "PAR_CLOSE")) {
            interfaces.add(new Var(current()));
            eat(current());
            eat("COMMA");
        }
        eat("PAR_CLOSE");

        eat("DIRECT");

        eat("SCOPE_OPEN");
        Block block = parseClassBlock(values.get("SCOPE_CLOSE"));
        eat("SCOPE_CLOSE");

        return new Interface(interfaces, block);
    }

    private Enum parseEnum() {
        eat("ENUM");
        return (Enum) constructHelper(true);
    }


    /** *************************************************************************************************
     *  CONTAINERS
     *  Containers hold objects and add relations between them
     */

    private ContainerCreation parseContainer() {
        if (currentIs("ARR_OPEN")) {
            return parseArrayList();
        } else if (currentIs("L_ARR_OPEN")) {
            return parseLinkedList();
        } else if (currentIs("SCOPE_OPEN")) {
            return parseMap();
        } else if (currentIs("SET_OPEN")) {
            return parseSet();
        } else if (currentIs("UNDIR_OPEN")) {
            return parseUndirectedGraph();
        } else if (currentIs("DIR_OPEN")) {
            return parseDirectedGraph();
        }

        if (currentIs("ARR_TYPE")) {
            return new HArrayList(new ArrayList<>());
        } else if (currentIs("L_ARR_TYPE")) {
            return new HLinkedList(new ArrayList<>());
        } else if (currentIs("MAP_TYPE")) {
            return new HValueMap(new HashMap<>());
        } else if (currentIs("SET_TYPE")) {
            return new HSet(new ArrayList<>());
        } else if (currentIs("UNDIR_TYPE")) {
            return new HUndirectedGraph(new ArrayList<>(), new ArrayList<>());
        } else if (currentIs("DIR_TYPE")) {
            return new HDirectedGraph(new ArrayList<>(), new ArrayList<>());
        }

        return null;
    }

    private Expression[] rangeHelper(Expression start) {
        eat("DIRECT");
        Expression stop = parseExpression(values.get("COLON"), values.get("ARR_CLOSE"));
        if (currentIs("COLON")) {
            Expression step = parseExpression(values.get("ARR_CLOSE"));
            return new Expression[] {start, stop, step};
        }
        return new Expression[] {start, stop, noop};
    }

    private ContainerCreation parseArrayList() {
        eat("ARR_OPEN");
        List<Expression> items = new ArrayList<>();

        Expression start = parseExpression(values.get("COMMA"), values.get("ARR_CLOSE"), values.get("DIRECT"));
        if (currentIs("DIRECT")) {
            Expression[] rangeValues = rangeHelper(start);
            return new ArrayListRange(rangeValues[0], rangeValues[1], rangeValues[2]);
        } else if (currentIs("ARR_CLOSE")) {
            return new HArrayList(items);
        }

        items.add(start);
        while (!currentIs("ARR_CLOSE")) {
            items.add(parseExpression(values.get("COMMA"), values.get("ARR_CLOSE")));
            eat("COMMA");
        }
        eat("ARR_CLOSE");
        return new HArrayList(items);
    }

    private ContainerCreation parseLinkedList() {
        eat("L_ARR_OPEN");
        List<Expression> items = new ArrayList<>();

        Expression start = parseExpression(values.get("COMMA"), values.get("ARR_CLOSE"), values.get("DIRECT"));
        if (currentIs("DIRECT")) {
            Expression[] rangeValues = rangeHelper(start);
            return new LinkedListRange(rangeValues[0], rangeValues[1], rangeValues[2]);
        } else if (currentIs("ARR_CLOSE")) {
            return new HLinkedList(items);
        }

        items.add(start);
        while (!currentIs("ARR_CLOSE")) {
            items.add(parseExpression(values.get("COMMA"), values.get("ARR_CLOSE")));
            eat("COMMA");
        }
        eat("ARR_CLOSE");
        return new HLinkedList(items);
    }


    private HSet parseSet() {
        eat("SET_OPEN");
        List<Expression> items = new ArrayList<>();
        while (!currentIs("SET_CLOSE")) {
            items.add(parseExpression(values.get("COMMA"), values.get("SET_CLOSE")));
            eat("COMMA");
        }
        eat("SET_CLOSE");
        return new HSet(items);
    }

    private HMap parseMap() {
        eat("SCOPE_OPEN");

        Expression key = parseExpression(values.get("COLON"));
        eat("COLON");
        Expression val = parseExpression(values.get("COMMA"), values.get("SCOPE_CLOSE"));

        if (key instanceof Var) {
            return parseObjectMap((Var) key, val);
        } else {
            return parseValueMap(key, val);
        }
    }

    private HObjectMap parseObjectMap(Var firstKey, Expression firstVal) {
        Map<Var, Expression> items = new HashMap<>();
        items.put(firstKey, firstVal);

        while (!currentIs("SCOPE_CLOSE")) {
            Expression key = parseExpression(values.get("COLON"));
            assert key instanceof Var;

            eat("COLON");

            Expression value = parseExpression(values.get("COMMA"), values.get("SCOPE_CLOSE"));
            items.put((Var) key, value);

            if (currentIs("COMMA"))
                eat("COMMA");
        }

        eat("SCOPE_CLOSE");
        return new HObjectMap(items);
    }

    private HValueMap parseValueMap(Expression firstKey, Expression firstVal) {
        Map<Expression, Expression> items = new HashMap<>();
        items.put(firstKey, firstVal);

        while (!currentIs("SCOPE_CLOSE")) {
            Expression key = parseExpression(values.get("COLON"));

            eat("COLON");

            Expression value = parseExpression(values.get("COMMA"), values.get("SCOPE_CLOSE"));
            items.put(key, value);

            if (currentIs("COMMA"))
                eat("COMMA");
        }

        eat("SCOPE_CLOSE");
        return new HValueMap(items);
    }

    private HDirectedGraph parseDirectedGraph() {
        eat("DIR_OPEN");

        List<Expression> nodes = new ArrayList<>();
        while (!currentIs("EOL")) {
            nodes.add(parseExpression(values.get("COMMA")));
            eat("COMMA");
        }
        eat("EOL");

        List<HDirectedGraph.HDirectedEdge> edges = new ArrayList<>();
        while (!currentIs("DIR_CLOSE")) {
            Expression first = parseExpression(values.get("DIR_EDGE"),
                    values.get("DIR_2_EDGE"));

            assert currentIs("DIR_EDGE") || currentIs("DIR_2_EDGE");
            boolean doubleEdge = currentIs("DIR_EDGE");
            eat(current());

            Expression second = parseExpression(values.get("COMMA"), values.get("GRAPH_CLOSE"));

            edges.add(new HDirectedGraph.HDirectedEdge(first, second, doubleEdge));
            eat("COMMA");
        }

        eat("DIR_CLOSE");
        return new HDirectedGraph(nodes, edges);
    }

    private HUndirectedGraph parseUndirectedGraph() {
        eat("UNDIR_OPEN");

        List<Expression> nodes = new ArrayList<>();
        while (!currentIs("EOL")) {
            nodes.add(parseExpression(values.get("COMMA")));
            eat("COMMA");
        }
        eat("EOL");

        List<HGraph.HEdge> edges = new ArrayList<>();
        while (!currentIs("DIR_CLOSE")) {
            Expression first = parseExpression(values.get("DIR_EDGE"),
                    values.get("DIR_2_EDGE"));

            eat("MINUS");

            Expression second = parseExpression(values.get("COMMA"), values.get("GRAPH_CLOSE"));

            edges.add(new HGraph.HEdge(first, second));
            eat("COMMA");
        }

        eat("UNDIR_CLOSE");
        return new HUndirectedGraph(nodes, edges);
    }


    /** ************************************************************************************************* **/
    /** ************************************************************************************************* **/
    /** NODES                                                                                             **/
    /** Abstract Syntax Tree node classes                                                                 **/
    /** ************************************************************************************************* **/
    /** ************************************************************************************************* **/

    public static abstract class ASTNode {
        public Token token;
        ASTNode parent;

        public void setParent(ASTNode... nodes) {
            Arrays.asList(nodes).forEach(node -> node.parent = this);
        }

        public void setParent(List<? extends ASTNode>... nodes) {
            Arrays.asList(nodes).forEach(list -> list.forEach(node -> node.parent = this));
        }

        public void setParent(Map<? extends ASTNode, ? extends ASTNode>... nodes) {
            Arrays.asList(nodes).forEach(map -> map.forEach((name, node) -> node.parent = this));
        }

        public ASTNode parent() { return parent; }

        public abstract JSONObject toJSON();
    }

    public interface DirectBody {}

    /** A sequential list of statements */
    public static final class Block extends ASTNode implements DirectBody {
        public List<Statement> statements;

        public Block(List<Statement> statements) {
            setParent(statements);

            this.parent = parent;
            this.statements = statements;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Block");
                put("token", null);
                put("statements", new JSONArray() {{
                    for (Statement statement : statements) {
                        add(statement.toJSON());
                    }
                }});
            }};
        }
    }

    /** A complete line of instruction */
    public static abstract class Statement extends ASTNode implements DirectBody {}

    /** Assigns a variable a value */
    public static final class Assignment extends Statement {
        public ASTNode var;
        public Expression value;
        public boolean cast;

        Assignment(ASTNode var, Expression value, boolean cast) {
            setParent(var, value);

            assert var instanceof Declare || var instanceof Expression;
            this.var = var;
            this.value = value;

            this.cast = cast;
            this.token = values.get("ASSIGN");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Assignment");
                put("token", token.toString());
                put("var", var.toJSON());
                put("value", value.toJSON());
            }};
        }
    }

    /** Declares a variable in the namespace */
    public static final class Declare extends ASTNode {
        public List<Modifier> modifiers;
        public Var var;
        public List<Type> type;

        Declare(List<Modifier> modifier, Var var, List<Type> type) {
            setParent(modifier, type);
            setParent(var);

            this.modifiers = modifier;
            this.var = var;
            this.type = type;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Declare");
                put("token", null);
                put("modifiers", new JSONArray() {{
                    for (Modifier modifier : modifiers) {
                        add(modifier.toJSON());
                    }
                }});
                put("var", var.toJSON());
                put("types", new JSONArray() {{
                    for (Type type : type) {
                        add(type.toJSON());
                    }
                }});
            }};
        }
    }

    /** A series of logical steps that returns a value */
    public static abstract class Expression extends Statement {}

    /** An empty expression */
    public static final class NoOp extends Expression implements DirectBody {
        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "NoOp");
                put("token", null);
            }};
        }
    }

    public static final NoOp noop = new NoOp();

    /** A statement on what to return from a function */
    public static final class Continue extends Statement {
        public Continue() {
            this.token = values.get("CONTINUE");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Continue");
                put("token", token.toString());
            }};
        }
    }

    /** A statement on what to return from a function */
    public static final class Break extends Statement {
        public Break() {
            this.token = values.get("BREAK");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Break");
                put("token", token.toString());
            }};
        }
    }

    /** A statement on what to return from a function */
    public static final class Pass extends Statement {
        public Pass() {
            this.token = values.get("PASS");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Pass");
                put("token", token.toString());
            }};
        }
    }

    /** A statement on what to return from a function */
    public static final class Return extends Statement {
        public Expression expression;

        public Return(Expression expression) {
            setParent(expression);

            this.expression = expression;
            this.token = values.get("RETURN");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Return");
                put("token", token.toString());
                put("expression", expression.toJSON());
            }};
        }
    }

    /** A statement to only be executed upon a block's end */
    public static final class Defer extends Statement {
        public Expression expression;

        public Defer(Expression expression) {
            setParent(expression);

            this.expression = expression;
            this.token = values.get("DEFER");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Defer");
                put("token", token.toString());
                put("expression", expression.toJSON());
            }};
        }
    }


    /** A variable identifier */
    public static final class Var extends Expression {
        public String value;

        Var(Token token) {
            this.token = token;
            this.value = token.value();
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Var");
                put("token", token.toString());
                put("value", value);
            }};
        }
    }

    /** A (possibly nested) type */
    public static class Type extends ASTNode {
        boolean isPointer;

        Type(Token token, boolean isPointer) {
            assert token.isType() || token.isVar();
            this.token = token;
            this.isPointer = isPointer;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Type");
                put("token", token.toString());
                put("isPointer", isPointer);
            }};
        }
    }

    public static final class ContainerType extends Type {
        public Container container;
        public List<Type> types;

        ContainerType(boolean isPointer, Container container, List<Type> types) {
            super(null, isPointer);
            setParent(types);
            this.container = container;
            this.types = types;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "ContainerType");
                put("token", token.toString());
                put("container", container.toString());
                put("types", new JSONArray() {{
                    for (Type type : types) {
                        add(type.toJSON());
                    }
                }});
            }};
        }

        enum Container { NONE, SET, LIST, MAP, DIR, UNDIR, OTHER }
    }

    /** A @modifier that can be applied to a method, class, or variable */
    public static final class Modifier extends ASTNode {
        Modifier(Token token) {
            assert token.isModifier();
            this.token = token;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Modifer");
                put("token", token.toString());
            }};
        }
    }

    /** A list of definitions for parameters, which are syntactically Declares */
    public static final class ParamDefs extends ASTNode {
        public List<Assignment> parameters;

        ParamDefs(List<Assignment> parameters) {
            setParent(parameters);
            this.parameters = parameters;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "ParamDefs");
                put("token", token.toString());
                put("parameters", new JSONArray() {{
                    for (Assignment parameter : parameters) {
                        add(parameter.toJSON());
                    }
                }});
            }};
        }
    }

    /** A list of passed in parameters, which are syntactically Expressions */
    public static final class Params extends ASTNode {
        public List<Expression> parameters;

        Params(List<Expression> parameters) {
            setParent(parameters);
            this.token = values.get("COMMA");
            this.parameters = parameters;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Params");
                put("token", token.toString());
                put("parameters", new JSONArray() {{
                    for (Expression parameter : parameters) {
                        add(parameter.toJSON());
                    }
                }});
            }};
        }
    }


    /** Object-building blocks */
    public static abstract class Construct extends Expression {}

    /** A defined operation taking a Param node */
    public static final class Func extends Construct {
        public ParamDefs paramDefs;
        public ASTNode operations;

        Func(ParamDefs paramDefs, ASTNode operations) {
            assert operations instanceof Block || operations instanceof Expression;

            setParent(paramDefs, operations);
            this.token = values.get("FUNC");
            this.paramDefs = paramDefs;
            this.operations = operations;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Func");
                put("token", token.toString());
                put("paramDefs", paramDefs.toJSON());
                put("operations", operations.toJSON());
            }};
        }
    }

    /** A blueprint for objects */
    public static class Class extends Construct {
        public List<Var> superClasses;
        public List<Var> interfaces;
        public Block block;

        public Class(List<Var> superClasses, List<Var> interfaces, Block block) {
            setParent(superClasses, interfaces);
            setParent(block);

            this.superClasses = superClasses;
            this.interfaces = interfaces;
            this.block = block;
            this.token = values.get("CLASS");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Class");
                put("token", token.toString());
                put("superClasses", new JSONArray() {{
                    for (Var superClass : superClasses) {
                        add(superClass.toJSON());
                    }
                }});
                put("interfaces", new JSONArray() {{
                    for (Var interFace : interfaces) {
                        add(interFace.toJSON());
                    }
                }});
                put("block", block.toJSON());
            }};
        }
    }

    /** A class definition that follows the "value object" pattern */
    public static final class Struct extends Class {
        public Struct(List<Var> superClasses, List<Var> interfaces, Block block) {
            super(superClasses, interfaces, block);
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Struct");
                put("token", token.toString());
                put("superClasses", new JSONArray() {{
                    for (Var superClass : superClasses) {
                        add(superClass.toJSON());
                    }
                }});
                put("interfaces", new JSONArray() {{
                    for (Var interFace : interfaces) {
                        add(interFace.toJSON());
                    }
                }});
                put("block", block.toJSON());
            }};
        }
    }

    /** A blueprint for classes, that cannot be instantiated */
    public static final class Interface extends Construct {
        public List<Var> interfaces;
        public Block block;

        Interface(List<Var> interfaces, Block block) {
            setParent(interfaces);
            setParent(block);

            this.interfaces = interfaces;
            this.block = block;
            this.token = values.get("INTERFACE");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Interface");
                put("token", token.toString());
                put("interfaces", new JSONArray() {{
                    for (Var interFace : interfaces) {
                        add(interFace.toJSON());
                    }
                }});
                put("block", block.toJSON());
            }};
        }
    }

    /** A class that comes with an immutable set of instances */
    public static final class Enum extends Construct {
        public List<Call> instances;
        public List<Var> superClasses;
        public List<Var> interfaces;
        public Block block;

        Enum(List<Call> instances, List<Var> superClasses, List<Var> interfaces, Block block) {
            setParent(instances, superClasses, interfaces);
            setParent(block);

            this.instances = instances;
            this.superClasses = superClasses;
            this.interfaces = interfaces;
            this.block = block;
            this.token = values.get("ENUM");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Enum");
                put("token", token.toString());
                put("instances", new JSONArray() {{
                    for (Call instance : instances) {
                        add(instance.toJSON());
                    }
                }});
                put("superClasses", new JSONArray() {{
                    for (Var superClass : superClasses) {
                        add(superClass.toJSON());
                    }
                }});
                put("interfaces", new JSONArray() {{
                    for (Var interFace : interfaces) {
                        add(interFace.toJSON());
                    }
                }});
                put("block", block.toJSON());
            }};
        }
    }


    /** Control-flow blocks */
    public static abstract class Direct extends Statement {
        public DirectBody elseBlock;

        protected Direct(DirectBody elseBlock) {
            assert elseBlock instanceof Block || elseBlock instanceof Statement;
            this.elseBlock = elseBlock;
        }
    }

    /** A repeated set of statements */
    public static final class Loop extends Direct {
        public List<Assignment> initClauses;
        public List<Expression> breakClauses, loopClauses;
        public DirectBody block;

        Loop(List<Assignment> initClauses, List<Expression> breakClauses,
                    List<Expression> loopClauses, DirectBody block, DirectBody elseBlock) {
            super(elseBlock);

            setParent(initClauses, breakClauses, loopClauses);
            setParent((ASTNode) block, (ASTNode) elseBlock);

            this.token = values.get("LOOP");
            this.initClauses = initClauses;
            this.breakClauses = breakClauses;
            this.loopClauses = loopClauses;
            this.block = block;
            this.elseBlock = elseBlock;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Loop");
                put("token", token.toString());
                put("initClauses", new JSONArray() {{
                    for (Assignment initClause : initClauses) {
                        add(initClause.toJSON());
                    }
                }});
                put("breakClauses", new JSONArray() {{
                    for (Expression breakClause : breakClauses) {
                        add(breakClause.toJSON());
                    }
                }});

                put("loopClauses", new JSONArray() {{
                    for (Expression loopClause : loopClauses) {
                        add(loopClause.toJSON());
                    }
                }});
                put("block", ((ASTNode) block).toJSON());
                put("elseBlock", ((ASTNode) elseBlock).toJSON());
            }};
        }
    }

    /** A set of statements that execute conditionally */
    public static final class If extends Direct {
        public List<IfBlock> ifblocks;

        If(List<IfBlock> ifblocks, DirectBody elseBlock) {
            super(elseBlock);
            setParent(ifblocks);
            setParent((ASTNode) elseBlock);

            this.token = values.get("IF");
            this.ifblocks = ifblocks;
            this.elseBlock = elseBlock;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "If");
                put("token", token.toString());
                put("ifBlocks", new JSONArray() {{
                    for (IfBlock ifblock : ifblocks) {
                        add(ifblock.toJSON());
                    }
                }});
                put("elseBlock", ((ASTNode) elseBlock).toJSON());
            }};
        }
    }

    /** A single condition and resulting block */
    public static final class IfBlock extends ASTNode {
        public Expression condition;
        public DirectBody block;

        IfBlock(Expression condition, DirectBody block) {
            setParent(condition, (ASTNode) block);
            this.token = values.get("IF");
            this.condition = condition;
            this.block = block;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "IfBlock");
                put("token", token.toString());
                put("condition", condition.toJSON());
                put("block", ((ASTNode) block).toJSON());
            }};
        }
    }

    /** A set of cases done conditionally on a starting expression; each
     * statement is checked independently of each other (requires a break)*/
    public static final class Switch extends Direct {
        public Expression expression;
        public List<Case> cases;
        public DirectBody defaultBlock;

        Switch(Expression expression, List<Case> cases, DirectBody defaultBlock, DirectBody elseBlock) {
            super(elseBlock);
            setParent(expression, (ASTNode) defaultBlock, (ASTNode) elseBlock);
            setParent(cases);

            this.token = values.get("SWITCH");
            this.expression = expression;
            this.cases = cases;
            this.defaultBlock = defaultBlock;
            this.elseBlock = elseBlock;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Switch");
                put("token", token.toString());
                put("cases", new JSONArray() {{
                    for (Case aCase : cases) {
                        add(aCase.toJSON());
                    }
                }});
                put("defaultBlock", ((ASTNode)defaultBlock).toJSON());
            }};
        }
    }

    /** A single case and the resulting block */
    public static final class Case extends Direct {
        public Expression expression;
        public DirectBody block;

        Case(Expression expression, DirectBody block) {
            super(null);
            setParent(expression, (ASTNode) block);
            this.token = values.get("CASE");
            this.expression = expression;
            this.block = block;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Case");
                put("token", token.toString());
                put("expression", expression.toJSON());
                put("block", ((ASTNode) block).toJSON());
            }};
        }
    }

    /** A block that may throw an Exception */
    public static final class Try extends Direct {
        public DirectBody block;
        public List<Catch> catchBlocks;

        Try(DirectBody block, List<Catch> catchBlocks, DirectBody elseBlock) {
            super(elseBlock);
            setParent((ASTNode) block, (ASTNode) elseBlock);
            setParent(catchBlocks);
            this.token = values.get("TRY");

            this.block = block;
            this.catchBlocks = catchBlocks;
            this.elseBlock = elseBlock;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Try");
                put("token", token.toString());
                put("block", ((ASTNode) block).toJSON());
                put("catchBlocks", new JSONArray() {{
                    for (Catch catchBlock : catchBlocks) {
                        add(catchBlock.toJSON());
                    }
                }});
            }};
        }
    }

    /** A block that deals with caught exceptions in a try block */
    public static final class Catch extends ASTNode {
        public Declare exception;
        public DirectBody block;

        Catch(Declare exception, DirectBody block) {
            setParent(exception, (ASTNode) block);
            this.token = values.get("CATCH");
            this.exception = exception;
            this.block = block;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Catch");
                put("token", token.toString());
                put("exception", exception.toJSON());
                put("block", ((ASTNode) block).toJSON());
            }};
        }
    }


    /** Calls to a construct (functions, classes, interfaces, structs, etc. */
    public static final class Call extends Expression {
        public Params params;
        public Expression value;

        Call(Params params, Expression value) {
            setParent(params, value);
            this.token = values.get("PAR_OPEN");
            this.params = params;
            this.value = value;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Call");
                put("token", token.toString());
                put("params", params.toJSON());
                put("value", value.toJSON());
            }};
        }
    }


    /** Anything that creates a container */
    interface ContainerCreation {}

    /** Container literal nodes */
    public static abstract class Container extends Expression implements ContainerCreation {}

    /** A dynamic array of objects */
    public abstract static class HList extends Container {
        public List<Expression> items;
    }

    /** A container of objects with a dynamic array implementation */
    public static final class HArrayList extends HList {
        public List<Expression> items;

        HArrayList(List<Expression> items) {
            setParent(items);
            this.items = items;
            this.token = values.get("ARR_TYPE");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "HArrayList");
                put("token", token.toString());
                put("items", new JSONArray() {{
                    for (Expression item : items) {
                        add(item.toJSON());
                    }
                }});
            }};
        }
    }

    /** A container of objects with a doubly linked list implementation */
    public static final class HLinkedList extends HList {
        public List<Expression> items;

        HLinkedList(List<Expression> items) {
            setParent(items);
            this.items = items;
            this.token = values.get("L_ARR_TYPE");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "HLinkedList");
                put("token", token.toString());
                put("items", new JSONArray() {{
                    for (Expression item : items) {
                        add(item.toJSON());
                    }
                }});
            }};
        }
    }

    /** A bijective mapping between objects */
    public static abstract class HMap extends Container {
        public Map<? extends Expression, Expression> items;

        HMap(Map<? extends Expression, Expression> items) {
            setParent(items);
            this.items = items;
            this.token = Parser.values.get("MAP_TYPE");
        }
    }

    /** A map that maps expressions to expression */
    public static final class HValueMap extends HMap {
        public Map<Expression, Expression> items;

        HValueMap(Map<Expression, Expression> items) {
            super(items);
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "HValueMap");
                put("token", token.toString());
                put("items", new JSONArray() {{
                    for (Expression key : items.keySet()) {
                        add(new JSONArray() {{
                            add(key.toJSON());
                            add(items.get(key).toJSON());
                        }});
                    }
                }});
            }};
        }
    }

    /** A map that maps variables to expressions */
    public static final class HObjectMap extends HMap {
        public Map<Var, Expression> items;

        HObjectMap(Map<Var, Expression> items) {
            super(items);
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "HObjectMap");
                put("token", token.toString());
                put("items", new JSONArray() {{
                    for (Expression key : items.keySet()) {
                        add(new JSONArray() {{
                            add(key.toJSON());
                            add(items.get(key).toJSON());
                        }});
                    }
                }});
            }};
        }
    }

    /** A unique set of objects */
    public static final class HSet extends Container {
        public List<Expression> items;

        HSet(List<Expression> items) {
            setParent(items);
            this.items = items;
            this.token = values.get("SET_TYPE");
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "HSet");
                put("token", token.toString());
                put("items", new JSONArray() {{
                    for (Expression item : items) {
                        add(item.toJSON());
                    }
                }});
            }};
        }
    }

    /** A base class for collection of objects and edges connecting them */
    public static abstract class HGraph extends Container {
        public List<Expression> nodes;
        public List<? extends HEdge> edges;

        HGraph(List<Expression> nodes, List<? extends HEdge> edges, Token token) {
            setParent(nodes, edges);
            this.nodes = nodes;
            this.edges = edges;
            this.token = token;
        }

        static class HEdge extends ASTNode {
            ASTNode first, second;

            HEdge(Expression first, Expression second) {
                setParent(first, second);
                this.first = first;
                this.second = second;
                this.token = values.get("SUBTRACT");
            }

            @Override
            public JSONObject toJSON() {
                return new JSONObject() {{
                    put("type", "HEdge");
                    put("token", token.toString());
                    put("first", first.toJSON());
                    put("second", second.toJSON());
                }};
            }
        }
    }

    /** A graph where each edge is directed from one node to another */
    public static final class HDirectedGraph extends HGraph {
        HDirectedGraph(List<Expression> nodes, List<HDirectedEdge> edges) {
            super(nodes, edges, values.get("DIR_TYPE"));
        }

        static final class HDirectedEdge extends HEdge {
            boolean doubleEdge;
            HDirectedEdge(Expression first, Expression second, boolean doubleEdge) {
                super(first, second);
                setParent(first, second);
                this.doubleEdge = doubleEdge;
            }

            @Override
            public JSONObject toJSON() {
                return new JSONObject() {{
                    put("type", "HDirectedGraph");
                    put("token", token.toString());
                    put("first", first.toJSON());
                    put("second", second.toJSON());
                    put("doubleEdge", doubleEdge);
                }};
            }
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "HDirectedGraph");
                put("token", token.toString());
                put("nodes", new JSONArray() {{
                    for (Expression node : nodes) {
                        add(node.toJSON());
                    }
                }});
                put("edges", new JSONArray() {{
                    for (HEdge edge : edges) {
                        add(edge.toJSON());
                    }
                }});
            }};
        }
    }

    /** A graph where nodes are parity-constant */
    public static final class HUndirectedGraph extends HGraph {
        HUndirectedGraph(List<Expression> nodes, List<HEdge> edges) {
            super(nodes, edges, values.get("UNDIR_TYPE"));
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "HUndirectedGraph");
                put("token", token.toString());
                put("nodes", new JSONArray() {{
                    for (Expression node : nodes) {
                        add(node.toJSON());
                    }
                }});
                put("edges", new JSONArray() {{
                    for (HEdge edge : edges) {
                        add(edge.toJSON());
                    }
                }});
            }};
        }
    }


    /** Built-in operations between language members */
    public static abstract class Op extends Expression {
        abstract Expression left();
        abstract Expression right();

        abstract void setLeft(Expression expression);
        abstract void setRight(Expression expression);

        /**
         * Whether this has a higher operator precedence than op. If op is actually
         * just a literal, return false.
         * */
        public boolean higherPrecedenceThan(Expression op) {
            return op instanceof Op &&
                    Token.operatorPrecedence.get(this.token.type()) <
                    Token.operatorPrecedence.get(op.token.type());
        }

        /**
         * Whether this has equal operator precedence than op. If op is actually
         * just a literal, return false.
         * */
        public boolean equalPrecedenceTo(Expression op) {
            return op instanceof Op &&
                    Token.operatorPrecedence.get(this.token.type())
                            .equals(Token.operatorPrecedence.get(op.token.type()));
        }
    }

    /** Meta information about operators that tokens cannot provide */
    enum Meta {
        POSTFIX, PREFIX
    }

    /** An operation that acts on an array */
    interface ArrayOp {}

    /** An operation that takes in one child */
    public static final class UnaryOp extends Op {
        public Expression child;
        public Meta meta;

        UnaryOp(Expression child, Token token) {
            setParent(child);
            this.child = child;
            this.token = token;
        }

        UnaryOp(Expression child, Token token, Meta meta) {
            this(child, token);
            this.meta = meta;
        }

        @Override
        Expression right() {
            return child;
        }

        @Override
        Expression left() {
            return child;
        }

        @Override
        void setRight(Expression expression) {
            setChild(expression);
        }

        @Override
        void setLeft(Expression expression) {
            setChild(expression);
        }

        void setChild(Expression expression) {
            this.child = expression;
            setParent(expression);
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "UnaryOp");
                put("token", token.toString());
                put("child", child.toJSON());
                put("meta", meta == null ? meta : meta.toString());
            }};
        }
    }

    /** An operation that takes in two children */
    public static class BinaryOp extends Op {
        public Expression left, right;

        BinaryOp(Expression left, Expression right, Token token) {
            setParent(left, right);
            this.left = left;
            this.right = right;
            this.token = token;
        }

        public void setLeft(Expression expression) {
            this.left = expression;
            setParent(expression);
        }

        public void setRight(Expression expression) {
            this.right = expression;
            setParent(expression);
        }

        public Expression left() { return left; }

        public Expression right() { return right; }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "BinaryOp");
                put("token", token.toString());
                put("right", right.toJSON());
                put("left", left.toJSON());
            }};
        }
    }

    /** Applies a binary op then sets left to resulting value, e.g. x += 2 */
    public static final class SetOp extends BinaryOp {
        public SetOp(Expression left, Expression right, Token token) {
            super(left, right, token);
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "SetOp");
                put("token", token.toString());
                put("var", right.toJSON());
                put("value", left.toJSON());
            }};
        }
    }

    /** Indexes a container object, e.g. x[2] */
    public static final class Index extends BinaryOp implements ArrayOp {
        Index(Expression var, Expression index) {
            super(var, index, values.get("ARR_TYPE"));
            this.left = var;
            this.right = index;
        }

        public Expression var() { return left; }

        public Expression index() { return right; }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Index");
                put("token", token.toString());
                put("var", var().toJSON());
                put("index", index().toJSON());
            }};
        }
    }

    /** Gets a property from an object, e.g. person.height */
    public static final class Get extends BinaryOp {
        boolean optChain;

        Get(Expression var, Expression property, boolean optChain) {
            super(var, property, values.get("PERIOD"));
            this.optChain = optChain;
        }

        public Expression var() { return left; }

        public Expression property() { return right; }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Get");
                put("token", token.toString());
                put("var", var().toJSON());
                put("property", property().toJSON());
            }};
        }
    }


    /** An operation that takes in three children */
    public static class TernaryOp extends Op {
        public Expression left, center, right;

        TernaryOp(Expression left, Expression center, Expression right, Token token) {
            setParent(left, center, right);
            this.left = left;
            this.center = center;
            this.right = right;
            this.token = token;
        }

        public void setLeft(Expression expression) {
            this.left = expression;
            setParent(expression);
        }

        public void setCenter(Expression expression) {
            this.center = expression;
            setParent(expression);
        }

        public void setRight(Expression expression) {
            this.right = expression;
            setParent(expression);
        }

        public Expression left() { return left; }

        public Expression center() { return center; }

        public Expression right() { return right; }


        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "TernaryOp");
                put("token", token.toString());
                put("left", left.toJSON());
                put("center", center.toJSON());
                put("right", left.toJSON());
            }};
        }

    }

    public static abstract class Range extends TernaryOp implements ContainerCreation {
        public Range(Expression start, Expression stop, Expression step) {
            super(start, stop, step, values.get("ARR_TYPE"));
        }

        public Expression start() {
            return left;
        }

        public Expression stop() {
            return center;
        }

        public Expression step() {
            return right;
        }

        void setStart(Expression start) { setLeft(start); }

        void setStop(Expression stop) { setCenter(stop); }

        void setStep(Expression step) { setRight(step); }
    }

    public static final class ArrayListRange extends Range {
        ArrayListRange(Expression start, Expression stop, Expression step) {
            super(start, stop, step);
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "ArrayListRange");
                put("token", token.toString());
                put("start", start().toJSON());
                put("stop", stop().toJSON());
                put("step", step().toJSON());
            }};
        }
    }

    public static final class LinkedListRange extends Range {
        LinkedListRange(Expression start, Expression stop, Expression step) {
            super(start, stop, step);
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "LinkedListRange");
                put("token", token.toString());
                put("start", start().toJSON());
                put("stop", stop().toJSON());
                put("step", step().toJSON());
            }};
        }
    }

    /** An operation that takes four children */
    public static class QuaternaryOp  extends Op {
        public Expression left, centerLeft, centerRight, right;

        QuaternaryOp(Expression left, Expression centerLeft, Expression centerRight, Expression right, Token token) {
            setParent(left, centerLeft, centerRight, right);
            this.left = left;
            this.centerLeft = centerLeft;
            this.centerRight = centerRight;
            this.right = right;
            this.token = token;
        }

        void setLeft(Expression left) {
            this.left = left;
            setParent(left);
        }

        void setCenterLeft(Expression centerLeft) {
            this.centerLeft = centerLeft;
            setParent(centerLeft);
        }

        void setCenterRight(Expression centerRight) {
            this.centerRight = centerRight;
            setParent(centerRight);
        }

        void setRight(Expression right) {
            this.right = right;
            setParent(right);
        }

        @Override
        Expression left() {
            return left;
        }

        @Override
        Expression right() {
            return right;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "QuaternaryOp");
                put("token", token.toString());
                put("left", left.toJSON());
                put("centerLeft", centerLeft.toJSON());
                put("centerRight", centerRight.toJSON());
                put("right", right.toJSON());
            }};
        }
    }

    public static final class Slice extends QuaternaryOp implements ArrayOp, ContainerCreation {
        public Slice(Expression var, Expression start, Expression stop, Expression step) {
            super(var, start, stop, step, values.get("COLON"));
        }


        public Expression var() {
            return left;
        }

        public Expression start() {
            return centerLeft;
        }

        public Expression stop() {
            return centerRight;
        }

        public Expression step() {
            return right;
        }

        void setVar(Expression var) { setLeft(var); }

        void setStart(Expression start) { setCenterLeft(start); }

        void setStop(Expression stop) { setCenterRight(stop); }

        void setStep(Expression step) { setRight(step); }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "Slice");
                put("token", token.toString());
                put("var", var().toJSON());
                put("start", start().toJSON());
                put("stop", stop().toJSON());
                put("step", step().toJSON());
            }};
        }
    }


    /** A proper value that serves no abstraction */
    public static abstract class Literal extends Expression {}

    /** An integer literal */
    public static final class IntLiteral extends Literal {
        IntLiteral(Token token) {
            this.token = token;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "IntLiteral");
                put("token", token.toString());
                put("value", token.value());
            }};
        }
    }

    /** A real number literal */
    public static final class RealLiteral extends Literal {
        RealLiteral(Token token) {
            this.token = token;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "RealLiteral");
                put("token", token.toString());
                put("value", token.value());
            }};
        }
    }

    /** A String literal */
    public static final class StringLiteral extends Literal {
        StringLiteral(Token token) {
            this.token = token;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "StringLiteral");
                put("token", token.toString());
                put("value", token.value());
            }};
        }
    }

    /** A boolean literal */
    public static final class BooleanLiteral extends Literal {
        BooleanLiteral(Token token) {
            assert token.type() == Token.TokenType.TRUE || token.type() == Token.TokenType.FALSE;
            this.token = token;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "BooleanLiteral");
                put("token", token.toString());
                put("value", token.value());
            }};
        }
    }

    /** A null literal */
    public static final class NullLiteral extends Literal {
        NullLiteral(Token token) {
            assert token.type() == Token.TokenType.NULL;
            this.token = token;
        }

        @Override
        public JSONObject toJSON() {
            return new JSONObject() {{
                put("type", "NullLiteral");
                put("token", token.toString());
            }};
        }
    }
}
