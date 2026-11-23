package io.github.parkiyong.jaybee.core.el;

import io.github.parkiyong.jaybee.api.exception.JaybeeException;
import io.github.parkiyong.jaybee.api.spi.ExpressionEvaluator;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Zero-dependency clean-room expression evaluator supporting {@code #{...}} and {@code ${...}}
 * expressions with variables, property/map/index navigation, arithmetic, comparisons,
 * logical operators, {@code empty}, and ternary conditionals.
 */
public final class SimpleExpressionEvaluator implements ExpressionEvaluator {

    @Override
    public Object evaluate(String expression, Function<String, Object> variableLookup) {
        if (expression == null) {
            return null;
        }
        Function<String, Object> lookup = variableLookup == null ? k -> null : variableLookup;
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        // Single wrapped expression: #{...} or ${...}
        if (isSingleWrappedExpression(trimmed)) {
            String inner = trimmed.substring(2, trimmed.length() - 1).trim();
            return evaluateInner(inner, lookup);
        }

        // Template string containing one or more #{...} / ${...} segments
        if (containsWrapper(expression)) {
            return evaluateTemplate(expression, lookup);
        }

        // Bare expression (e.g. "amount > 100")
        return evaluateInner(trimmed, lookup);
    }

    private boolean isSingleWrappedExpression(String s) {
        if (s.length() < 4) {
            return false;
        }
        char first = s.charAt(0);
        if ((first != '#' && first != '$') || s.charAt(1) != '{' || s.charAt(s.length() - 1) != '}') {
            return false;
        }
        int closing = findClosingBrace(s, 2);
        return closing == s.length() - 1;
    }

    private boolean containsWrapper(String s) {
        for (int i = 0; i < s.length() - 2; i++) {
            char c = s.charAt(i);
            if ((c == '#' || c == '$') && s.charAt(i + 1) == '{') {
                return true;
            }
        }
        return false;
    }

    private String evaluateTemplate(String template, Function<String, Object> lookup) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            if (i + 1 < template.length()
                    && (template.charAt(i) == '#' || template.charAt(i) == '$')
                    && template.charAt(i + 1) == '{') {
                int end = findClosingBrace(template, i + 2);
                if (end == -1) {
                    throw new JaybeeException("Unclosed expression in template: " + template);
                }
                String inner = template.substring(i + 2, end).trim();
                Object val = evaluateInner(inner, lookup);
                if (val != null) {
                    sb.append(val);
                }
                i = end + 1;
            } else {
                sb.append(template.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    private int findClosingBrace(String s, int startIdx) {
        int depth = 1;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = startIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && (inSingle || inDouble)) {
                i++;
                continue;
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private Object evaluateInner(String expr, Function<String, Object> lookup) {
        if (expr.isEmpty()) {
            return null;
        }
        List<Token> tokens = tokenize(expr);
        Parser parser = new Parser(tokens, lookup, expr);
        Object result = parser.parseExpression();
        if (!parser.isAtEnd()) {
            throw new JaybeeException("Unexpected token '" + parser.peek().text + "' in expression: " + expr);
        }
        return result;
    }

    private enum TokenType {
        NUMBER, STRING, IDENT,
        TRUE, FALSE, NULL,
        AND, OR, NOT, EMPTY,
        EQ, NE, LT, LE, GT, GE,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        QUESTION, COLON, DOT, LPAREN, RPAREN, LBRACKET, RBRACKET,
        EOF
    }

    private record Token(TokenType type, String text) {
    }

    private List<Token> tokenize(String expr) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = expr.length();
        while (i < n) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                StringBuilder sb = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < n) {
                    char ch = expr.charAt(i++);
                    if (ch == '\\' && i < n) {
                        char esc = expr.charAt(i++);
                        switch (esc) {
                            case 'n' -> sb.append('\n');
                            case 'r' -> sb.append('\r');
                            case 't' -> sb.append('\t');
                            case '\\' -> sb.append('\\');
                            case '\'' -> sb.append('\'');
                            case '"' -> sb.append('"');
                            default -> sb.append(esc);
                        }
                    } else if (ch == quote) {
                        closed = true;
                        break;
                    } else {
                        sb.append(ch);
                    }
                }
                if (!closed) {
                    throw new JaybeeException("Unterminated string literal in expression: " + expr);
                }
                tokens.add(new Token(TokenType.STRING, sb.toString()));
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i;
                boolean hasDot = false;
                while (i < n) {
                    char ch = expr.charAt(i);
                    if (Character.isDigit(ch)) {
                        i++;
                    } else if (ch == '.' && !hasDot && i + 1 < n && Character.isDigit(expr.charAt(i + 1))) {
                        hasDot = true;
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(TokenType.NUMBER, expr.substring(start, i)));
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < n && Character.isJavaIdentifierPart(expr.charAt(i))) {
                    i++;
                }
                String word = expr.substring(start, i);
                TokenType kw = switch (word) {
                    case "true" -> TokenType.TRUE;
                    case "false" -> TokenType.FALSE;
                    case "null" -> TokenType.NULL;
                    case "and" -> TokenType.AND;
                    case "or" -> TokenType.OR;
                    case "not" -> TokenType.NOT;
                    case "empty" -> TokenType.EMPTY;
                    case "eq" -> TokenType.EQ;
                    case "ne" -> TokenType.NE;
                    case "lt" -> TokenType.LT;
                    case "le" -> TokenType.LE;
                    case "gt" -> TokenType.GT;
                    case "ge" -> TokenType.GE;
                    case "div" -> TokenType.SLASH;
                    case "mod" -> TokenType.PERCENT;
                    default -> TokenType.IDENT;
                };
                tokens.add(new Token(kw, word));
                continue;
            }
            // Two-character operators
            if (i + 1 < n) {
                String two = expr.substring(i, i + 2);
                TokenType twoType = switch (two) {
                    case "&&" -> TokenType.AND;
                    case "||" -> TokenType.OR;
                    case "==" -> TokenType.EQ;
                    case "!=" -> TokenType.NE;
                    case "<=" -> TokenType.LE;
                    case ">=" -> TokenType.GE;
                    default -> null;
                };
                if (twoType != null) {
                    tokens.add(new Token(twoType, two));
                    i += 2;
                    continue;
                }
            }
            TokenType single = switch (c) {
                case '!' -> TokenType.NOT;
                case '<' -> TokenType.LT;
                case '>' -> TokenType.GT;
                case '+' -> TokenType.PLUS;
                case '-' -> TokenType.MINUS;
                case '*' -> TokenType.STAR;
                case '/' -> TokenType.SLASH;
                case '%' -> TokenType.PERCENT;
                case '?' -> TokenType.QUESTION;
                case ':' -> TokenType.COLON;
                case '.' -> TokenType.DOT;
                case '(' -> TokenType.LPAREN;
                case ')' -> TokenType.RPAREN;
                case '[' -> TokenType.LBRACKET;
                case ']' -> TokenType.RBRACKET;
                default -> throw new JaybeeException("Unexpected character '" + c + "' in expression: " + expr);
            };
            tokens.add(new Token(single, String.valueOf(c)));
            i++;
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final Function<String, Object> lookup;
        private final String source;
        private int pos;

        Parser(List<Token> tokens, Function<String, Object> lookup, String source) {
            this.tokens = tokens;
            this.lookup = lookup;
            this.source = source;
        }

        boolean isAtEnd() {
            return peek().type == TokenType.EOF;
        }

        Token peek() {
            return tokens.get(pos);
        }

        Token advance() {
            Token t = tokens.get(pos);
            if (t.type != TokenType.EOF) {
                pos++;
            }
            return t;
        }

        boolean match(TokenType type) {
            if (peek().type == type) {
                advance();
                return true;
            }
            return false;
        }

        Token expect(TokenType type, String message) {
            if (peek().type != type) {
                throw new JaybeeException(message + " (found '" + peek().text + "') in expression: " + source);
            }
            return advance();
        }

        Object parseExpression() {
            return parseTernary(true);
        }

        private Object parseTernary(boolean eval) {
            Object cond = parseOr(eval);
            if (match(TokenType.QUESTION)) {
                boolean takeThen = eval && toBoolean(cond);
                Object thenVal = parseTernary(eval && takeThen);
                expect(TokenType.COLON, "Expected ':' in ternary expression");
                Object elseVal = parseTernary(eval && !takeThen);
                return takeThen ? thenVal : elseVal;
            }
            return cond;
        }

        private Object parseOr(boolean eval) {
            Object left = parseAnd(eval);
            while (match(TokenType.OR)) {
                boolean leftBool = eval && toBoolean(left);
                Object right = parseAnd(eval && !leftBool);
                if (eval) {
                    left = leftBool || toBoolean(right);
                }
            }
            return left;
        }

        private Object parseAnd(boolean eval) {
            Object left = parseEquality(eval);
            while (match(TokenType.AND)) {
                boolean leftBool = eval && toBoolean(left);
                Object right = parseEquality(eval && leftBool);
                if (eval) {
                    left = leftBool && toBoolean(right);
                }
            }
            return left;
        }

        private Object parseEquality(boolean eval) {
            Object left = parseRelational(eval);
            while (peek().type == TokenType.EQ || peek().type == TokenType.NE) {
                TokenType op = advance().type;
                Object right = parseRelational(eval);
                if (eval) {
                    boolean eq = areEqual(left, right);
                    left = (op == TokenType.EQ) ? eq : !eq;
                }
            }
            return left;
        }

        private Object parseRelational(boolean eval) {
            Object left = parseAdditive(eval);
            while (peek().type == TokenType.LT || peek().type == TokenType.LE
                    || peek().type == TokenType.GT || peek().type == TokenType.GE) {
                TokenType op = advance().type;
                Object right = parseAdditive(eval);
                if (eval) {
                    int cmp = compareValues(left, right);
                    left = switch (op) {
                        case LT -> cmp < 0;
                        case LE -> cmp <= 0;
                        case GT -> cmp > 0;
                        case GE -> cmp >= 0;
                        default -> throw new IllegalStateException();
                    };
                }
            }
            return left;
        }

        private Object parseAdditive(boolean eval) {
            Object left = parseMultiplicative(eval);
            while (peek().type == TokenType.PLUS || peek().type == TokenType.MINUS) {
                TokenType op = advance().type;
                Object right = parseMultiplicative(eval);
                if (eval) {
                    if (op == TokenType.PLUS) {
                        if (left instanceof String || right instanceof String) {
                            left = String.valueOf(left) + String.valueOf(right);
                        } else {
                            left = addNumbers(left, right);
                        }
                    } else {
                        left = subtractNumbers(left, right);
                    }
                }
            }
            return left;
        }

        private Object parseMultiplicative(boolean eval) {
            Object left = parseUnary(eval);
            while (peek().type == TokenType.STAR || peek().type == TokenType.SLASH || peek().type == TokenType.PERCENT) {
                TokenType op = advance().type;
                Object right = parseUnary(eval);
                if (eval) {
                    left = switch (op) {
                        case STAR -> multiplyNumbers(left, right);
                        case SLASH -> divideNumbers(left, right);
                        case PERCENT -> moduloNumbers(left, right);
                        default -> throw new IllegalStateException();
                    };
                }
            }
            return left;
        }

        private Object parseUnary(boolean eval) {
            if (match(TokenType.NOT)) {
                Object val = parseUnary(eval);
                return eval ? !toBoolean(val) : null;
            }
            if (match(TokenType.EMPTY)) {
                Object val = parseUnary(eval);
                return eval ? isEmpty(val) : null;
            }
            if (match(TokenType.MINUS)) {
                Object val = parseUnary(eval);
                return eval ? negateNumber(val) : null;
            }
            if (match(TokenType.PLUS)) {
                return parseUnary(eval);
            }
            return parsePostfix(eval);
        }

        private Object parsePostfix(boolean eval) {
            Object target = parsePrimary(eval);
            while (true) {
                if (match(TokenType.DOT)) {
                    Token name = advance();
                    if (name.type != TokenType.IDENT && !isKeywordAllowedAsProperty(name.type)) {
                        throw new JaybeeException("Expected property name after '.' in expression: " + source);
                    }
                    if (eval) {
                        target = resolveProperty(target, name.text);
                    }
                } else if (match(TokenType.LBRACKET)) {
                    Object key = parseTernary(eval);
                    expect(TokenType.RBRACKET, "Expected ']' after index expression");
                    if (eval) {
                        target = resolveIndex(target, key);
                    }
                } else {
                    break;
                }
            }
            return target;
        }

        private boolean isKeywordAllowedAsProperty(TokenType type) {
            return switch (type) {
                case EMPTY, AND, OR, NOT, EQ, NE, LT, LE, GT, GE -> true;
                default -> false;
            };
        }

        private Object parsePrimary(boolean eval) {
            Token t = peek();
            return switch (t.type) {
                case NUMBER -> {
                    advance();
                    yield parseNumber(t.text);
                }
                case STRING -> {
                    advance();
                    yield t.text;
                }
                case TRUE -> {
                    advance();
                    yield Boolean.TRUE;
                }
                case FALSE -> {
                    advance();
                    yield Boolean.FALSE;
                }
                case NULL -> {
                    advance();
                    yield null;
                }
                case IDENT -> {
                    advance();
                    yield eval ? lookup.apply(t.text) : null;
                }
                case LPAREN -> {
                    advance();
                    Object inner = parseTernary(eval);
                    expect(TokenType.RPAREN, "Expected ')'");
                    yield inner;
                }
                default -> throw new JaybeeException(
                        "Unexpected token '" + t.text + "' in expression: " + source
                );
            };
        }

        private Number parseNumber(String text) {
            if (text.contains(".")) {
                return Double.parseDouble(text);
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return Long.parseLong(text);
            }
        }

        private boolean toBoolean(Object value) {
            if (value == null) {
                return false;
            }
            if (value instanceof Boolean b) {
                return b;
            }
            if (value instanceof Number n) {
                return n.doubleValue() != 0.0;
            }
            if (value instanceof String s) {
                return Boolean.parseBoolean(s.trim());
            }
            return true;
        }

        private boolean isEmpty(Object value) {
            if (value == null) {
                return true;
            }
            if (value instanceof String s) {
                return s.isEmpty();
            }
            if (value instanceof Collection<?> c) {
                return c.isEmpty();
            }
            if (value instanceof Map<?, ?> m) {
                return m.isEmpty();
            }
            if (value.getClass().isArray()) {
                return Array.getLength(value) == 0;
            }
            return false;
        }

        private boolean areEqual(Object left, Object right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            if (left instanceof Number l && right instanceof Number r) {
                return toBigDecimal(l).compareTo(toBigDecimal(r)) == 0;
            }
            if (left instanceof Enum<?> e && right instanceof String s) {
                return e.name().equals(s);
            }
            if (left instanceof String s && right instanceof Enum<?> e) {
                return s.equals(e.name());
            }
            return Objects.equals(left, right);
        }

        @SuppressWarnings("unchecked")
        private int compareValues(Object left, Object right) {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return -1;
            }
            if (right == null) {
                return 1;
            }
            if (left instanceof Number l && right instanceof Number r) {
                return toBigDecimal(l).compareTo(toBigDecimal(r));
            }
            if (left instanceof Comparable<?> c && left.getClass().isAssignableFrom(right.getClass())) {
                return ((Comparable<Object>) c).compareTo(right);
            }
            return String.valueOf(left).compareTo(String.valueOf(right));
        }

        private BigDecimal toBigDecimal(Number n) {
            if (n instanceof BigDecimal bd) {
                return bd;
            }
            if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
                return BigDecimal.valueOf(n.longValue());
            }
            return BigDecimal.valueOf(n.doubleValue());
        }

        private Number toNumber(Object value) {
            if (value == null) {
                return 0L;
            }
            if (value instanceof Number n) {
                return n;
            }
            if (value instanceof String s) {
                return parseNumber(s.trim());
            }
            throw new JaybeeException("Cannot convert value to number: " + value + " in expression: " + source);
        }

        private boolean isFloating(Number a, Number b) {
            return a instanceof Double || a instanceof Float || b instanceof Double || b instanceof Float
                    || a instanceof BigDecimal || b instanceof BigDecimal;
        }

        private Number addNumbers(Object left, Object right) {
            Number a = toNumber(left);
            Number b = toNumber(right);
            if (isFloating(a, b)) {
                return a.doubleValue() + b.doubleValue();
            }
            return a.longValue() + b.longValue();
        }

        private Number subtractNumbers(Object left, Object right) {
            Number a = toNumber(left);
            Number b = toNumber(right);
            if (isFloating(a, b)) {
                return a.doubleValue() - b.doubleValue();
            }
            return a.longValue() - b.longValue();
        }

        private Number multiplyNumbers(Object left, Object right) {
            Number a = toNumber(left);
            Number b = toNumber(right);
            if (isFloating(a, b)) {
                return a.doubleValue() * b.doubleValue();
            }
            return a.longValue() * b.longValue();
        }

        private Number divideNumbers(Object left, Object right) {
            Number a = toNumber(left);
            Number b = toNumber(right);
            return a.doubleValue() / b.doubleValue();
        }

        private Number moduloNumbers(Object left, Object right) {
            Number a = toNumber(left);
            Number b = toNumber(right);
            if (isFloating(a, b)) {
                return a.doubleValue() % b.doubleValue();
            }
            return a.longValue() % b.longValue();
        }

        private Number negateNumber(Object val) {
            Number n = toNumber(val);
            if (n instanceof Double || n instanceof Float || n instanceof BigDecimal) {
                return -n.doubleValue();
            }
            return -n.longValue();
        }

        private Object resolveIndex(Object target, Object key) {
            if (target == null) {
                return null;
            }
            if (target instanceof Map<?, ?> map) {
                return map.get(key);
            }
            if (target instanceof List<?> list && key instanceof Number n) {
                int idx = n.intValue();
                return idx >= 0 && idx < list.size() ? list.get(idx) : null;
            }
            if (target.getClass().isArray() && key instanceof Number n) {
                int idx = n.intValue();
                return idx >= 0 && idx < Array.getLength(target) ? Array.get(target, idx) : null;
            }
            if (key instanceof String prop) {
                return resolveProperty(target, prop);
            }
            return null;
        }

        private Object resolveProperty(Object target, String property) {
            if (target == null) {
                return null;
            }
            if (target instanceof Map<?, ?> map) {
                return map.get(property);
            }
            Class<?> clazz = target.getClass();
            String cap = Character.toUpperCase(property.charAt(0)) + property.substring(1);
            // Try JavaBean getter: getXxx()
            Object val = tryInvokeNoArg(clazz, target, "get" + cap);
            if (val != NOT_FOUND) {
                return val;
            }
            // Try boolean getter: isXxx()
            val = tryInvokeNoArg(clazz, target, "is" + cap);
            if (val != NOT_FOUND) {
                return val;
            }
            // Try Java record accessor: xxx()
            val = tryInvokeNoArg(clazz, target, property);
            if (val != NOT_FOUND) {
                return val;
            }
            // Try public field
            try {
                Field field = clazz.getField(property);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                return null;
            } catch (IllegalAccessException e) {
                throw new JaybeeException("Cannot read field '" + property + "' on " + clazz.getName(), e);
            }
        }

        private static final Object NOT_FOUND = new Object();

        private Object tryInvokeNoArg(Class<?> clazz, Object target, String methodName) {
            try {
                Method method = clazz.getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                return NOT_FOUND;
            } catch (Exception e) {
                throw new JaybeeException("Failed to invoke " + clazz.getName() + "." + methodName + "()", e);
            }
        }
    }
}
