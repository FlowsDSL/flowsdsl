// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde;

import dsl.flows.sde.models.*;
import dsl.flows.sde.models.expressions.*;
import dsl.flows.sde.models.statements.*;
import dsl.flows.sde.models.types.OneOfType;
import dsl.flows.sde.models.types.StructType;
import dsl.flows.sde.models.types.TypeReference;

import java.util.LinkedList;
import java.util.TreeMap;

public class FlowsParser {
    public static abstract class Token {
        public final SourceLocation sourceLocation;

        Token(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
        }
    }

    static class TokenEnd extends Token {
        TokenEnd(SourceLocation sourceLocation) {
            super(sourceLocation);
        }
    }

    public static class TokenKeyword extends Token {
        public enum Kind {
            Struct, OneOf, Queue, Const, Transform, While, If, Else, Break, Continue, Step, True, False, System,
            Instance, Is, Wire, With, Backpressure
        }

        public final Kind kind;

        TokenKeyword(SourceLocation sourceLocation, Kind kind) {
            super(sourceLocation);
            this.kind = kind;
        }
    }

    public static class TokenPunctuation extends Token {
        public enum Kind {
            OpeningCurlyBracket(1), OpeningSquareBracket(1), OpeningRoundBracket(1), OpeningAngularBracket(1),
            ClosingSquareBracket(1), ClosingCurlyBracket(1), ClosingRoundBracket(1), ClosingAngularBracket(1),
            Semicolon(1), Colon(1), Comma(1), Dot(1), Arrow(2), Dash(1), Assignment(1), ComparisonEquals(2), Plus(1),
            Star(1), DoubleAmpersand(2), Pipe(1), DoublePipe(2), LessOrEqual(2), MoreOrEqual(2), Exclamation(1),
            NotEqual(2), Slash(1), Tilde(1);

            public final int length;

            Kind(int length) {
                this.length = length;
            }
        }

        public final Kind kind;

        TokenPunctuation(SourceLocation sourceLocation, Kind kind) {
            super(sourceLocation);
            this.kind = kind;
        }
    }

    public static class TokenComment extends Token {
        public final SourceLocation endLocation;

        TokenComment(SourceLocation start, SourceLocation end) {
            super(start);
            endLocation = end;
        }
    }

    static class TokenIntegerLiteral extends Token {
        public long value;

        TokenIntegerLiteral(SourceLocation sourceLocation, long value) {
            super(sourceLocation);
            this.value = value;
        }
    }

    static class TokenName extends Token {
        public String value;

        TokenName(SourceLocation sourceLocation, String value) {
            super(sourceLocation);
            this.value = value;
        }
    }

    private final String filename;
    private final String[] input;
    private final boolean produceCommentTokens;
    private SourceLocation lastTokenEndLocation;
    private Token nextToken;
    private int line, position;

    public FlowsParser(String filename, String[] input, boolean produceCommentTokens) throws ParseException {
        this.filename = filename;
        this.input = input;
        this.produceCommentTokens = produceCommentTokens;
        line = position = 0;
        consumeNextToken();
    }

    void consumeNextToken() throws ParseException {
        lastTokenEndLocation = getTokenLocation();
        nextToken = getNextToken();
    }

    private Token getNextToken() throws ParseException {
        while (true) {
            if (line == input.length) {
                return new TokenEnd(getTokenLocation());
            }
            if (atEndOfLine(0)) {
                continue;
            }
            String inputAtLine = input[line];
            char ch = inputAtLine.charAt(position++);
            if (ch == ' ' || ch == '\t') {
                continue;
            }
            SourceLocation sourceLocation = new SourceLocation(line, position - 1);
            switch (ch) {
                case '/':
                    if (position < inputAtLine.length() && inputAtLine.charAt(position) == '*') {
                        position++;
                        while (true) {
                            if (atEndOfLine(1)) {
                                if (line == input.length) {
                                    throwParseException("Unterminated comment at the end");
                                }
                                inputAtLine = input[line];
                                continue;
                            }
                            ch = inputAtLine.charAt(position++);
                            if (ch == '*' && inputAtLine.charAt(position) == '/') {
                                position++;
                                break;
                            }
                        }
                        if (produceCommentTokens) {
                            return new TokenComment(sourceLocation, getTokenLocation());
                        }
                        break;
                    }
                    if (position < inputAtLine.length() && inputAtLine.charAt(position) == '/') {
                        position = 0;
                        line++;
                        if (produceCommentTokens) {
                            return new TokenComment(sourceLocation,
                                    new SourceLocation(line - 1, input[line - 1].length()));
                        }
                        break;
                    }
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Slash);
                case '=':
                    if (position < inputAtLine.length() && inputAtLine.charAt(position) == '=') {
                        position++;
                        return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.ComparisonEquals);
                    }
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Assignment);
                case ',':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Comma);
                case '.':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Dot);
                case ':':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Colon);
                case ';':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Semicolon);
                case '~':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Tilde);
                case '[':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.OpeningSquareBracket);
                case ']':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.ClosingSquareBracket);
                case '{':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.OpeningCurlyBracket);
                case '}':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.ClosingCurlyBracket);
                case '(':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.OpeningRoundBracket);
                case ')':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.ClosingRoundBracket);
                case '<':
                    if (position < inputAtLine.length() && inputAtLine.charAt(position) == '=') {
                        position++;
                        return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.LessOrEqual);
                    }
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.OpeningAngularBracket);
                case '>':
                    if (position < inputAtLine.length() && inputAtLine.charAt(position) == '=') {
                        position++;
                        return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.MoreOrEqual);
                    }
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.ClosingAngularBracket);
                case '-':
                    if (position < inputAtLine.length() && inputAtLine.charAt(position) == '>') {
                        position++;
                        return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Arrow);
                    }
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Dash);
                case '&':
                    if (position < inputAtLine.length() && inputAtLine.charAt(position) == '&') {
                        position++;
                        return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.DoubleAmpersand);
                    }
                    throwParseException("Invalid character '" + ch + "'");
                case '|':
                    if (position < inputAtLine.length() && inputAtLine.charAt(position) == '|') {
                        position++;
                        return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.DoublePipe);
                    }
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Pipe);
                case '*':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Star);
                case '+':
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Plus);
                case '!':
                    if (position < inputAtLine.length() && inputAtLine.charAt(position) == '=') {
                        position++;
                        return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.NotEqual);
                    }
                    return new TokenPunctuation(sourceLocation, TokenPunctuation.Kind.Exclamation);
                default:
                    if (Character.isAlphabetic(ch)) {
                        int start = position - 1;
                        while (true) {
                            if (position == inputAtLine.length()) {
                                break;
                            }
                            ch = inputAtLine.charAt(position);
                            if (!Character.isAlphabetic(ch) && !Character.isDigit(ch) && ch != '_') {
                                break;
                            }
                            position++;
                        }
                        String value = inputAtLine.substring(start, position);
                        return switch (value) {
                            case "queue" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Queue);
                            case "wire" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Wire);
                            case "struct" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Struct);
                            case "oneof" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.OneOf);
                            case "const" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Const);
                            case "system" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.System);
                            case "instance" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Instance);
                            case "transform" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Transform);
                            case "while" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.While);
                            case "if" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.If);
                            case "else" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Else);
                            case "break" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Break);
                            case "continue" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Continue);
                            case "step" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Step);
                            case "with" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.With);
                            case "backpressure" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Backpressure);
                            case "true" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.True);
                            case "false" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.False);
                            case "is" -> new TokenKeyword(sourceLocation, TokenKeyword.Kind.Is);
                            default -> new TokenName(sourceLocation, value);
                        };
                    }
                    if (Character.isDigit(ch)) {
                        int radix;
                        if (ch == '0' && position + 1 < inputAtLine.length() && inputAtLine.charAt(position) == 'x' &&
                                Character.digit(inputAtLine.charAt(position + 1), 16) != -1) {
                            position++;
                            radix = 16;
                        } else {
                            radix = 10;
                        }
                        long value = Character.digit(ch, 10);
                        while (position < inputAtLine.length() &&
                                Character.digit(inputAtLine.charAt(position), radix) != -1) {
                            value = value * radix + Character.digit(inputAtLine.charAt(position), radix);
                            position++;
                        }
                        return new TokenIntegerLiteral(sourceLocation, value);
                    }
                    throwParseException("Invalid character '" + ch + "'");
            }
        }
    }

    private boolean atEndOfLine(int additionalSpace) {
        if (position + additionalSpace >= input[line].length()) {
            line++;
            position = 0;
            return true;
        }
        return false;
    }

    private long expectInteger() throws ParseException {
        if (!(nextToken instanceof TokenIntegerLiteral)) {
            throwParseException("Expecting integer literal");
        }
        long result = ((TokenIntegerLiteral) nextToken).value;
        consumeNextToken();
        return result;
    }

    private void throwParseException(String message) throws ParseException {
        throw new ParseException(
                new SourceFilenameAndLocationRange(filename, nextToken.sourceLocation, getTokenLocation()), message);
    }

    private String expectName() throws ParseException {
        if (!(nextToken instanceof TokenName)) {
            throwParseException("Expecting name");
        }
        String result = ((TokenName) nextToken).value;
        consumeNextToken();
        return result;
    }

    private void expectPunctuation(TokenPunctuation.Kind kind) throws ParseException {
        if (!(nextToken instanceof TokenPunctuation cast && cast.kind == kind)) {
            throwParseException("Expecting punctuation " + kind);
        }
        consumeNextToken();
    }

    private TypeReference parseTypeReference(TokenName nameToConsume) throws ParseException {
        SourceLocation start;
        String name;
        if (nameToConsume == null) {
            start = nextToken.sourceLocation;
            name = expectName();
        } else {
            start = nameToConsume.sourceLocation;
            name = nameToConsume.value;
        }
        Integer arraySize;
        if (nextToken instanceof TokenPunctuation &&
                ((TokenPunctuation) nextToken).kind == TokenPunctuation.Kind.OpeningSquareBracket) {
            consumeNextToken();
            arraySize = (int) expectInteger();
            expectPunctuation(TokenPunctuation.Kind.ClosingSquareBracket);
        } else {
            arraySize = null;
        }
        return new TypeReference(new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), name, null,
                arraySize);
    }

    private Expression parseExpression(IdGenerator idGenerator, TokenName nameToConsume) throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        Expression result = parseExpression5(idGenerator, nameToConsume);
        while (nextToken instanceof TokenPunctuation cast) {
            ExpressionBinaryOperator.Kind kind;
            if (cast.kind == TokenPunctuation.Kind.DoublePipe) {
                kind = ExpressionBinaryOperator.Kind.BooleanOr;
            } else if (cast.kind == TokenPunctuation.Kind.Pipe) {
                kind = ExpressionBinaryOperator.Kind.BitwiseOr;
            } else {
                break;
            }
            consumeNextToken();
            Expression b = parseExpression5(idGenerator, null);
            result = new ExpressionBinaryOperator(
                    new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), kind, result, b,
                    idGenerator.generateId());
        }
        return result;
    }

    private Expression parseExpression5(IdGenerator idGenerator, TokenName nameToConsume) throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        Expression result = parseExpression4(idGenerator, nameToConsume);
        while (nextToken instanceof TokenPunctuation cast) {
            ExpressionBinaryOperator.Kind kind;
            if (cast.kind == TokenPunctuation.Kind.DoubleAmpersand) {
                kind = ExpressionBinaryOperator.Kind.BooleanAnd;
            } else {
                break;
            }
            consumeNextToken();
            Expression b = parseExpression4(idGenerator, null);
            result = new ExpressionBinaryOperator(
                    new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), kind, result, b,
                    idGenerator.generateId());
        }
        return result;
    }

    private Expression parseExpression4(IdGenerator idGenerator, TokenName nameToConsume) throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        Expression result = parseExpression3(idGenerator, nameToConsume);
        while (true) {
            if (nextToken instanceof TokenKeyword keywordCast && keywordCast.kind == TokenKeyword.Kind.Is) {
                consumeNextToken();
                TypeReference checkedType = parseTypeReference(null);
                result = new ExpressionIs(new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation),
                        result, checkedType, idGenerator.generateId());
                continue;
            }
            if (!(nextToken instanceof TokenPunctuation cast)) {
                break;
            }
            ExpressionBinaryOperator.Kind kind;
            if (cast.kind == TokenPunctuation.Kind.ComparisonEquals) {
                kind = ExpressionBinaryOperator.Kind.Equals;
            } else if (cast.kind == TokenPunctuation.Kind.NotEqual) {
                kind = ExpressionBinaryOperator.Kind.NotEquals;
            } else if (cast.kind == TokenPunctuation.Kind.OpeningAngularBracket) {
                kind = ExpressionBinaryOperator.Kind.Less;
            } else if (cast.kind == TokenPunctuation.Kind.ClosingAngularBracket) {
                kind = ExpressionBinaryOperator.Kind.More;
            } else if (cast.kind == TokenPunctuation.Kind.LessOrEqual) {
                kind = ExpressionBinaryOperator.Kind.LessOrEqual;
            } else if (cast.kind == TokenPunctuation.Kind.MoreOrEqual) {
                kind = ExpressionBinaryOperator.Kind.MoreOrEqual;
            } else {
                break;
            }
            consumeNextToken();
            Expression b = parseExpression3(idGenerator, null);
            result = new ExpressionBinaryOperator(
                    new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), kind, result, b,
                    idGenerator.generateId());
        }
        return result;
    }

    private Expression parseExpression3(IdGenerator idGenerator, TokenName nameToConsume) throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        Expression result = parseExpression2(idGenerator, nameToConsume);
        while (nextToken instanceof TokenPunctuation cast) {
            ExpressionBinaryOperator.Kind kind;
            if (cast.kind == TokenPunctuation.Kind.Plus) {
                kind = ExpressionBinaryOperator.Kind.IntegerSum;
            } else if (cast.kind == TokenPunctuation.Kind.Dash) {
                kind = ExpressionBinaryOperator.Kind.IntegerDifference;
            } else {
                break;
            }
            consumeNextToken();
            Expression b = parseExpression2(idGenerator, null);
            result = new ExpressionBinaryOperator(
                    new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), kind, result, b,
                    idGenerator.generateId());
        }
        return result;
    }

    private Expression parseExpression2(IdGenerator idGenerator, TokenName nameToConsume) throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        Expression result = parseExpression1(idGenerator, nameToConsume);
        while (nextToken instanceof TokenPunctuation cast) {
            ExpressionBinaryOperator.Kind kind;
            if (cast.kind == TokenPunctuation.Kind.Star) {
                kind = ExpressionBinaryOperator.Kind.IntegerProduct;
            } else {
                break;
            }
            consumeNextToken();
            Expression b = parseExpression1(idGenerator, null);
            result = new ExpressionBinaryOperator(
                    new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), kind, result, b,
                    idGenerator.generateId());
        }
        return result;
    }

    private Expression parseExpression1(IdGenerator idGenerator, TokenName nameToConsume) throws ParseException {
        if (nameToConsume != null || nextToken instanceof TokenName) {
            SourceLocation start;
            String name;
            if (nameToConsume != null) {
                start = nameToConsume.sourceLocation;
                name = nameToConsume.value;
            } else {
                start = nextToken.sourceLocation;
                name = expectName();
            }
            if (nextToken instanceof TokenPunctuation castOuter) {
                boolean isRoundBracket = castOuter.kind == TokenPunctuation.Kind.OpeningRoundBracket;
                boolean isCurlyBracket = castOuter.kind == TokenPunctuation.Kind.OpeningCurlyBracket;
                if (isRoundBracket || isCurlyBracket) {
                    consumeNextToken();
                    LinkedList<Expression> positionalParameters = isRoundBracket ? new LinkedList<>() : null;
                    TreeMap<String, FieldOrParameter> parametersOrFields = new TreeMap<>();
                    if (nextToken instanceof TokenName || nextToken instanceof TokenIntegerLiteral) {
                        parseParameter(idGenerator, positionalParameters, parametersOrFields);
                        while (nextToken instanceof TokenPunctuation castInner &&
                                castInner.kind == TokenPunctuation.Kind.Comma) {
                            consumeNextToken();
                            parseParameter(idGenerator, positionalParameters, parametersOrFields);
                        }
                    }
                    if (isRoundBracket) {
                        expectPunctuation(TokenPunctuation.Kind.ClosingRoundBracket);
                        return new ExpressionFunction(
                                new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), name,
                                positionalParameters.toArray(Expression.EMPTY_ARRAY), parametersOrFields,
                                idGenerator.generateId());
                    }
                    expectPunctuation(TokenPunctuation.Kind.ClosingCurlyBracket);
                    SourceFilenameAndLocationRange locationRange = new SourceFilenameAndLocationRange(filename, start,
                            lastTokenEndLocation);
                    return new ExpressionObject(locationRange, new TypeReference(locationRange, name, null, null),
                            parametersOrFields, idGenerator.generateId());
                }
            }
            Expression result = new ExpressionName(
                    new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), name,
                    idGenerator.generateId());
            while (nextToken instanceof TokenPunctuation castOuter && castOuter.kind == TokenPunctuation.Kind.Dot) {
                SourceLocation dotSourceLocation = nextToken.sourceLocation;
                consumeNextToken();
                String fieldName = expectName();
                result = new ExpressionPart(
                        new SourceFilenameAndLocationRange(filename, dotSourceLocation, lastTokenEndLocation), result,
                        fieldName, idGenerator.generateId());
            }
            return result;
        }
        SourceLocation start = nextToken.sourceLocation;
        switch (nextToken) {
            case TokenPunctuation cast when (cast.kind == TokenPunctuation.Kind.Exclamation ||
                    cast.kind == TokenPunctuation.Kind.Tilde) -> {
                consumeNextToken();
                Expression sub = parseExpression(idGenerator, null);
                return new ExpressionUnaryOperator(
                        new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation),
                        cast.kind == TokenPunctuation.Kind.Exclamation ? ExpressionUnaryOperator.Kind.LogicalNot :
                                ExpressionUnaryOperator.Kind.BitwiseNot, sub, idGenerator.generateId());
            }
            case TokenIntegerLiteral cast -> {
                long value = cast.value;
                consumeNextToken();
                return new ExpressionConstant(new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation),
                        value, 64, idGenerator.generateId());
            }
            case TokenKeyword cast when (cast.kind == TokenKeyword.Kind.True ||
                    cast.kind == TokenKeyword.Kind.False) -> {
                long value = cast.kind == TokenKeyword.Kind.True ? 1 : 0;
                consumeNextToken();
                return new ExpressionConstant(new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation),
                        value, 1, idGenerator.generateId());
            }
            case TokenPunctuation cast when cast.kind == TokenPunctuation.Kind.OpeningRoundBracket -> {
                consumeNextToken();
                Expression result = parseExpression(idGenerator, null);
                expectPunctuation(TokenPunctuation.Kind.ClosingRoundBracket);
                return result;
            }
            default -> {
            }
        }
        throw new ParseException(
                new SourceFilenameAndLocationRange(filename, nextToken.sourceLocation, getTokenLocation()),
                "Expecting name, not, integer literal, boolean literal, opening round bracket or opening square bracket");
    }

    private SourceLocation getTokenLocation() {
        return new SourceLocation(line, position);
    }

    private void parseParameter(IdGenerator idGenerator, LinkedList<Expression> positionalParameters,
                                TreeMap<String, FieldOrParameter> namedParameters) throws ParseException {
        TokenName nameToConsume;
        if (nextToken instanceof TokenName castName) {
            SourceLocation start = nextToken.sourceLocation;
            consumeNextToken();
            if (nextToken instanceof TokenPunctuation castPunctuation &&
                    castPunctuation.kind == TokenPunctuation.Kind.Colon) {
                SourceFilenameAndLocationRange keyLocationRange = new SourceFilenameAndLocationRange(filename, start,
                        lastTokenEndLocation);
                consumeNextToken();
                namedParameters.put(castName.value,
                        new FieldOrParameter(keyLocationRange, parseExpression(idGenerator, null)));
                return;
            }
            nameToConsume = castName;
        } else {
            nameToConsume = null;
        }
        if (positionalParameters == null) {
            throwParseException("Expecting colon");
        }
        if (!namedParameters.isEmpty()) {
            throwParseException("Positional parameters cannot follow named parameters");
        }
        positionalParameters.add(parseExpression(idGenerator, nameToConsume));
    }

    public void parse(CompilationElements result) throws ParseException {
        while (true) {
            if (nextToken instanceof TokenKeyword cast) {
                switch (cast.kind) {
                    case Struct:
                        parseStruct(result);
                        break;
                    case OneOf:
                        parseOneOf(result);
                        break;
                    case Const:
                        parseConstant(result);
                        break;
                    case Transform:
                        parseTransform(result);
                        break;
                    case System:
                        parseSystemArchitecture(result);
                        break;
                    default:
                        throwParseException("Expecting struct, oneof, const, transform or system");
                }
            } else if (nextToken instanceof TokenEnd) {
                return;
            } else {
                throwParseException("Expecting struct, oneof, const, transform or system");
            }
        }
    }

    private void parseStruct(CompilationElements result) throws ParseException {
        consumeNextToken();
        SourceLocation nameStart = nextToken.sourceLocation;
        String typeName = expectName();
        SourceLocation nameEnd = lastTokenEndLocation;
        expectPunctuation(TokenPunctuation.Kind.OpeningCurlyBracket);
        LinkedList<StructType.Field> fields = new LinkedList<>();
        while (!(nextToken instanceof TokenPunctuation cast &&
                cast.kind == TokenPunctuation.Kind.ClosingCurlyBracket)) {
            String fieldName = expectName();
            expectPunctuation(TokenPunctuation.Kind.Colon);
            fields.add(new StructType.Field(fieldName, parseTypeReference(null)));
            expectPunctuation(TokenPunctuation.Kind.Semicolon);
        }
        expectPunctuation(TokenPunctuation.Kind.ClosingCurlyBracket);
        StructType type = new StructType(fields.toArray(new StructType.Field[0]));
        if (result.types.put(typeName, type) != null) {
            throw new ParseException(new SourceFilenameAndLocationRange(filename, nameStart, nameEnd),
                    "Type '" + typeName + "' is redefined");
        }
    }

    private void parseOneOf(CompilationElements result) throws ParseException {
        consumeNextToken();
        String name = expectName();
        expectPunctuation(TokenPunctuation.Kind.OpeningCurlyBracket);
        LinkedList<TypeReference> options = new LinkedList<>();
        options.add(parseTypeReference(null));
        while (nextToken instanceof TokenPunctuation &&
                ((TokenPunctuation) nextToken).kind == TokenPunctuation.Kind.Comma) {
            consumeNextToken();
            options.add(parseTypeReference(null));
        }
        expectPunctuation(TokenPunctuation.Kind.ClosingCurlyBracket);
        if (result.types.put(name, new OneOfType(options.toArray(new TypeReference[0]))) != null) {
            throwParseException("Type '" + name + "' is redefined");
        }
    }

    private void parseConstant(CompilationElements result) throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        consumeNextToken();
        String name = expectName();
        expectPunctuation(TokenPunctuation.Kind.Assignment);
        long value = expectInteger();
        expectPunctuation(TokenPunctuation.Kind.Semicolon);
        if (result.constants.put(name,
                new ConstantDeclaration(new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation),
                        value)) != null) {
            throwParseException("Constant '" + name + "' is redefined");
        }
    }

    private void parseTransform(CompilationElements result) throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        consumeNextToken();
        String name = expectName();
        expectPunctuation(TokenPunctuation.Kind.OpeningRoundBracket);
        LinkedList<Transform.PortGroup> portGroups = new LinkedList<>();
        if (!(nextToken instanceof TokenPunctuation cast && cast.kind == TokenPunctuation.Kind.ClosingRoundBracket)) {
            parseTransformPortGroup(portGroups);
            while (nextToken instanceof TokenPunctuation cast && cast.kind == TokenPunctuation.Kind.Comma) {
                consumeNextToken();
                parseTransformPortGroup(portGroups);
            }
        }
        expectPunctuation(TokenPunctuation.Kind.ClosingRoundBracket);
        IdGenerator idGenerator = new IdGenerator();
        StatementBlock body = parseStatementBlock(idGenerator);
        Transform transform = new Transform(new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation),
                portGroups, body, result.BIT_TYPE, idGenerator);
        if (result.transforms.put(name, transform) != null) {
            throwParseException("Transform '" + name + "' is redefined");
        }
    }

    private Connection.Endpoint parseConnectionEndpoint() throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        String instanceName = expectName();
        expectPunctuation(TokenPunctuation.Kind.Dot);
        String portName = expectName();
        return new Connection.Endpoint(new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation),
                instanceName, portName);
    }

    private void parseSystemArchitecture(CompilationElements result) throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        consumeNextToken();
        expectPunctuation(TokenPunctuation.Kind.OpeningCurlyBracket);
        TreeMap<String, Instance> instances = new TreeMap<>();
        LinkedList<Connection> connections = new LinkedList<>();
        while (!(nextToken instanceof TokenPunctuation cast &&
                cast.kind == TokenPunctuation.Kind.ClosingCurlyBracket)) {
            switch (nextToken) {
                case TokenKeyword cast when cast.kind == TokenKeyword.Kind.Instance -> {
                    SourceLocation instanceStart = nextToken.sourceLocation;
                    consumeNextToken();
                    String name = expectName();
                    expectPunctuation(TokenPunctuation.Kind.Colon);
                    String transformName = expectName();
                    expectPunctuation(TokenPunctuation.Kind.Semicolon);
                    if (instances.put(name, new Instance(
                            new SourceFilenameAndLocationRange(filename, instanceStart, lastTokenEndLocation),
                            transformName)) != null) {
                        throwParseException("Instance '" + name + "' is redefined");
                    }
                }
                case TokenKeyword cast when cast.kind == TokenKeyword.Kind.Wire -> {
                    consumeNextToken();
                    if (nextToken instanceof TokenKeyword castNext && castNext.kind == TokenKeyword.Kind.With) {
                        consumeNextToken();
                        if (!(nextToken instanceof TokenKeyword &&
                                ((TokenKeyword) nextToken).kind == TokenKeyword.Kind.Backpressure)) {
                            throwParseException("Expecting keyword backpressure");
                        }
                        consumeNextToken();
                        parseConnection(connections, Connection.Kind.WireWithBackpressure);
                    } else {
                        parseConnection(connections, Connection.Kind.Wire);
                    }
                }
                case TokenKeyword cast when cast.kind == TokenKeyword.Kind.Queue -> {
                    consumeNextToken();
                    parseConnection(connections, Connection.Kind.Queue);
                }
                case null, default -> throwParseException("Expecting instance or connection");
            }
        }
        expectPunctuation(TokenPunctuation.Kind.ClosingCurlyBracket);
        if (result.systemArchitecture != null) {
            throwParseException("There can only be one 'system' block in the whole compilation");
        }
        result.systemArchitecture = new SystemArchitecture(new SourceLocationRange(start, lastTokenEndLocation),
                instances, connections);
    }

    private void parseConnection(LinkedList<Connection> connections, Connection.Kind kind) throws ParseException {
        SourceLocation connectionStart = nextToken.sourceLocation;
        Connection.Endpoint source = parseConnectionEndpoint();
        expectPunctuation(TokenPunctuation.Kind.Arrow);
        Connection.Endpoint destination = parseConnectionEndpoint();
        expectPunctuation(TokenPunctuation.Kind.Semicolon);
        connections.add(
                new Connection(new SourceFilenameAndLocationRange(filename, connectionStart, lastTokenEndLocation),
                        kind, source, destination));
    }

    private void parseTransformPortGroup(LinkedList<Transform.PortGroup> portGroups) throws ParseException {
        SourceLocation start = nextToken.sourceLocation;
        String name = expectName();
        expectPunctuation(TokenPunctuation.Kind.Colon);
        TypeReference typeReference = parseTypeReference(null);
        portGroups.add(
                new Transform.PortGroup(new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), name,
                        typeReference));
    }

    private StatementBlock parseStatementBlock(IdGenerator idGenerator) throws ParseException {
        SourceLocation blockStart = nextToken.sourceLocation;
        expectPunctuation(TokenPunctuation.Kind.OpeningCurlyBracket);
        LinkedList<Statement> statementList = new LinkedList<>();
        while (!(nextToken instanceof TokenPunctuation cast &&
                cast.kind == TokenPunctuation.Kind.ClosingCurlyBracket)) {
            SourceLocation statementStart = nextToken.sourceLocation;
            if (nextToken instanceof TokenKeyword cast) {
                switch (cast.kind) {
                    case While: {
                        consumeNextToken();
                        expectPunctuation(TokenPunctuation.Kind.OpeningRoundBracket);
                        Expression condition = parseExpression(idGenerator, null);
                        expectPunctuation(TokenPunctuation.Kind.ClosingRoundBracket);
                        StatementBlock body = parseStatementBlock(idGenerator);
                        statementList.add(new StatementIfWhile(
                                new SourceFilenameAndLocationRange(filename, statementStart, lastTokenEndLocation),
                                StatementIfWhile.Kind.While, condition, body, null, null, idGenerator.generateId()));
                        continue;
                    }
                    case If: {
                        consumeNextToken();
                        expectPunctuation(TokenPunctuation.Kind.OpeningRoundBracket);
                        Expression condition = parseExpression(idGenerator, null);
                        expectPunctuation(TokenPunctuation.Kind.ClosingRoundBracket);
                        StatementBlock thenBranch = parseStatementBlock(idGenerator);
                        StatementBlock elseBranch = null;
                        if (nextToken instanceof TokenKeyword elseCast && elseCast.kind == TokenKeyword.Kind.Else) {
                            consumeNextToken();
                            elseBranch = parseStatementBlock(idGenerator);
                        }
                        statementList.add(new StatementIfWhile(
                                new SourceFilenameAndLocationRange(filename, statementStart, lastTokenEndLocation),
                                StatementIfWhile.Kind.If, condition, thenBranch, elseBranch, null,
                                idGenerator.generateId()));
                        continue;
                    }
                    case Break:
                        consumeNextToken();
                        expectPunctuation(TokenPunctuation.Kind.Semicolon);
                        statementList.add(new StatementLoopControl(
                                new SourceFilenameAndLocationRange(filename, statementStart, lastTokenEndLocation),
                                StatementLoopControl.Kind.Break, null, idGenerator.generateId()));
                        continue;
                    case Continue:
                        consumeNextToken();
                        expectPunctuation(TokenPunctuation.Kind.Semicolon);
                        statementList.add(new StatementLoopControl(
                                new SourceFilenameAndLocationRange(filename, statementStart, lastTokenEndLocation),
                                StatementLoopControl.Kind.Continue, null, idGenerator.generateId()));
                        continue;
                    case Step:
                        consumeNextToken();
                        expectPunctuation(TokenPunctuation.Kind.Semicolon);
                        statementList.add(new StatementStep(
                                new SourceFilenameAndLocationRange(filename, statementStart, lastTokenEndLocation),
                                null, idGenerator.generateId()));
                        continue;
                    default:
                        throwParseException("Expecting a statement");
                }
            }
            if (nextToken instanceof TokenName cast) {
                String name = cast.value;
                consumeNextToken();
                SourceLocation endOfNameLocation = lastTokenEndLocation;
                if (nextToken instanceof TokenPunctuation castPunctuation) {
                    if (castPunctuation.kind == TokenPunctuation.Kind.Assignment) {
                        consumeNextToken();
                        Expression expression = parseExpression(idGenerator, null);
                        expectPunctuation(TokenPunctuation.Kind.Semicolon);
                        statementList.add(new StatementExpression(
                                new SourceFilenameAndLocationRange(filename, statementStart, lastTokenEndLocation),
                                new SourceFilenameAndLocationRange(filename, statementStart, endOfNameLocation), name,
                                expression, null, idGenerator.generateId()));
                        continue;
                    }
                    if (castPunctuation.kind == TokenPunctuation.Kind.OpeningSquareBracket) {
                        TypeReference typeReference = parseTypeReference(cast);
                        parseVariableDeclarationAndAssignment(idGenerator, statementList, statementStart,
                                typeReference);
                        continue;
                    }
                    if (castPunctuation.kind == TokenPunctuation.Kind.OpeningRoundBracket) {
                        Expression expression = parseExpression(idGenerator, cast);
                        expectPunctuation(TokenPunctuation.Kind.Semicolon);
                        {
                            boolean writeFixedLength = name.equals("WriteFixedLength");
                            boolean writeVariableLength = name.equals("WriteVariableLength");
                            if (writeFixedLength || writeVariableLength) {
                                if (expression instanceof ExpressionFunction castFunction) {
                                    FieldOrParameter where = castFunction.namedParameters.get("where");
                                    FieldOrParameter what = castFunction.namedParameters.get("what");
                                    if (castFunction.positionalParameters.length != 0 || where == null ||
                                            what == null) {
                                        throwParseException("Write macros take two named parameters: where and what");
                                    }
                                    if (where.expression() instanceof ExpressionName castName) {
                                        statementList.add(new StatementMacroWrite(
                                                new SourceFilenameAndLocationRange(filename, statementStart,
                                                        lastTokenEndLocation), castName.locationRange, castName.name,
                                                what.expression(),
                                                writeFixedLength ? StatementMacroWrite.Kind.FixedLength :
                                                        StatementMacroWrite.Kind.VariableLength,
                                                idGenerator.generateId()));
                                        continue;
                                    } else {
                                        throwParseException("Write macro's where parameter must be port group name");
                                    }
                                } else {
                                    throwParseException("Expecting macro Write");
                                }
                            }
                        }
                        {
                            boolean readFixedLength = name.equals("ReadFixedLength");
                            boolean readVariableLength = name.equals("ReadVariableLength");
                            if (readFixedLength || readVariableLength) {
                                if (expression instanceof ExpressionFunction castFunction) {
                                    FieldOrParameter where = castFunction.namedParameters.get("where");
                                    FieldOrParameter what = castFunction.namedParameters.get("what");
                                    if (castFunction.positionalParameters.length != 0 || where == null ||
                                            what == null) {
                                        throwParseException(
                                                name + " macro take " + (readFixedLength ? "three" : "two") +
                                                        " named parameters: " +
                                                        (readFixedLength ? "where, what and complete" :
                                                                "where and what"));
                                    }
                                    ExpressionName castComplete = null;
                                    if (readFixedLength) {
                                        FieldOrParameter complete = castFunction.namedParameters.get("complete");
                                        if (complete != null &&
                                                complete.expression() instanceof ExpressionName castComplete2) {
                                            castComplete = castComplete2;
                                        } else {
                                            throwParseException("Read complete parameter must refer to variable");
                                        }
                                    }
                                    if (where.expression() instanceof ExpressionName castWhere &&
                                            what.expression() instanceof ExpressionName castWhat) {
                                        statementList.add(new StatementMacroRead(
                                                new SourceFilenameAndLocationRange(filename, statementStart,
                                                        lastTokenEndLocation), castWhere.locationRange, castWhere.name,
                                                castWhat, castComplete,
                                                readFixedLength ? StatementMacroRead.Kind.FixedLength :
                                                        StatementMacroRead.Kind.VariableLength,
                                                idGenerator.generateId()));
                                        continue;
                                    }
                                    throwParseException(
                                            "Read macro's where parameter must refer to port group name and what must refer to variable");
                                } else {
                                    throwParseException("Expecting macro Read");
                                }
                            }
                        }
                        {
                            boolean assertingPush = name.equals("AssertingPush");
                            boolean blockingPush = name.equals("BlockingPush");
                            if (assertingPush || blockingPush) {
                                if (expression instanceof ExpressionFunction castFunction) {
                                    FieldOrParameter where = castFunction.namedParameters.get("where");
                                    FieldOrParameter what = castFunction.namedParameters.get("what");
                                    if (castFunction.positionalParameters.length != 0 || where == null ||
                                            what == null) {
                                        throwParseException("Push macros take two named parameters: where and what");
                                    }
                                    if (where.expression() instanceof ExpressionName castName) {
                                        statementList.add(new StatementMacroPush(
                                                new SourceFilenameAndLocationRange(filename, statementStart,
                                                        lastTokenEndLocation),
                                                assertingPush ? StatementMacroPush.Kind.Asserting :
                                                        StatementMacroPush.Kind.Blocking, castName.name,
                                                what.expression(), idGenerator.generateId()));
                                        continue;
                                    } else {
                                        throwParseException("Push macro's 'where' parameter must be port group name");
                                    }
                                } else {
                                    throwParseException("Expecting macro Push");
                                }
                            }
                        }
                        {
                            boolean assertingPop = name.equals("AssertingPop");
                            boolean blockingPop = name.equals("BlockingPop");
                            if (assertingPop || blockingPop) {
                                if (expression instanceof ExpressionFunction castFunction) {
                                    FieldOrParameter where = castFunction.namedParameters.get("where");
                                    FieldOrParameter what = castFunction.namedParameters.get("what");
                                    if (castFunction.positionalParameters.length != 0 || where == null ||
                                            what == null) {
                                        throwParseException("Pop macros take two named parameters: where and what");
                                    }
                                    if (where.expression() instanceof ExpressionName castWhere &&
                                            what.expression() instanceof ExpressionName castWhat) {
                                        statementList.add(new StatementMacroPop(
                                                new SourceFilenameAndLocationRange(filename, statementStart,
                                                        lastTokenEndLocation),
                                                assertingPop ? StatementMacroPop.Kind.Asserting :
                                                        StatementMacroPop.Kind.Blocking, castWhere.name, castWhat,
                                                idGenerator.generateId()));
                                        continue;
                                    } else {
                                        throwParseException("Pop macro's where parameter must be port group name");
                                    }
                                } else {
                                    throwParseException("Expecting macro Pop");
                                }
                            }
                        }
                        statementList.add(new StatementExpression(
                                new SourceFilenameAndLocationRange(filename, statementStart, lastTokenEndLocation),
                                expression, null, idGenerator.generateId()));
                        continue;
                    }
                    throwParseException("Expecting colon, opening square bracket or opening round bracket");
                }
                parseVariableDeclarationAndAssignment(idGenerator, statementList, statementStart, new TypeReference(
                        new SourceFilenameAndLocationRange(filename, statementStart, lastTokenEndLocation), name, null,
                        null));
                continue;
            }
            throwParseException("Expecting while, if, break, continue or name");
        }
        expectPunctuation(TokenPunctuation.Kind.ClosingCurlyBracket);
        return new StatementBlock(new SourceFilenameAndLocationRange(filename, blockStart, lastTokenEndLocation),
                statementList);
    }

    private void parseVariableDeclarationAndAssignment(IdGenerator idGenerator, LinkedList<Statement> statements,
                                                       SourceLocation start, TypeReference typeReference)
            throws ParseException {
        SourceLocation nameStart = nextToken.sourceLocation;
        String variableName = expectName();
        SourceLocation nameEnd = lastTokenEndLocation;
        SourceFilenameAndLocationRange assignmentTargetLocationRange = new SourceFilenameAndLocationRange(filename,
                nameStart, nameEnd);
        statements.add(new StatementVariableDeclaration(
                new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation), null, typeReference,
                assignmentTargetLocationRange, variableName, idGenerator.generateId()));
        expectPunctuation(TokenPunctuation.Kind.Assignment);
        Expression expression = parseExpression(idGenerator, null);
        statements.add(
                new StatementExpression(new SourceFilenameAndLocationRange(filename, start, lastTokenEndLocation),
                        assignmentTargetLocationRange, variableName, expression, null, idGenerator.generateId()));
        expectPunctuation(TokenPunctuation.Kind.Semicolon);
    }

    public LinkedList<Token> findHighlightedTokens() throws ParseException {
        LinkedList<Token> result = new LinkedList<>();
        try {
            while (true) {
                if (nextToken instanceof TokenEnd) {
                    return result;
                }
                if (nextToken instanceof TokenKeyword || nextToken instanceof TokenPunctuation ||
                        nextToken instanceof TokenComment) {
                    result.add(nextToken);
                }
                consumeNextToken();
            }
        } catch (ParseException e) {
            return result;
        }
    }
}
