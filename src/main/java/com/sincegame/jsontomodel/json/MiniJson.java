package com.sincegame.jsontomodel.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 零依赖的极简 JSON 解析器。
 *
 * 解析结果直接映射为 Java 原生结构：
 * object -> LinkedHashMap&lt;String, Object&gt;，array -> ArrayList&lt;Object&gt;，
 * string -> String，number -> BigDecimal，boolean -> Boolean，null -> null。
 *
 * 不引入第三方库（Gson/org.json），保证插件 jar 完全自包含，
 * 在 IntelliJ IDEA 与 DevEco Studio 任意版本上都不会有类冲突。
 */
public final class MiniJson {

    private MiniJson() {
    }

    public static Object parse(String text) throws JsonParseException {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    public static final class JsonParseException extends Exception {
        private final int offset;

        public JsonParseException(String message, int offset) {
            super(message);
            this.offset = offset;
        }

        public int getOffset() {
            return offset;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        return (List<Object>) value;
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        JsonParseException error(String message) {
            return new JsonParseException(message + " at offset " + pos, pos);
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        char peek() throws JsonParseException {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            return text.charAt(pos);
        }

        Object parseValue() throws JsonParseException {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
            }
        }

        private Map<String, Object> parseObject() throws JsonParseException {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // consume '{'
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("Expected object key string");
                }
                String key = parseString();
                skipWhitespace();
                if (peek() != ':') {
                    throw error("Expected ':' after object key");
                }
                pos++;
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                    continue;
                }
                if (next == '}') {
                    pos++;
                    return map;
                }
                throw error("Expected ',' or '}' in object");
            }
        }

        private List<Object> parseArray() throws JsonParseException {
            List<Object> list = new ArrayList<>();
            pos++; // consume '['
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                    continue;
                }
                if (next == ']') {
                    pos++;
                    return list;
                }
                throw error("Expected ',' or ']' in array");
            }
        }

        private String parseString() throws JsonParseException {
            StringBuilder sb = new StringBuilder();
            pos++; // consume opening quote
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("Unterminated escape sequence");
                    }
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > text.length()) {
                                throw error("Invalid unicode escape");
                            }
                            String hex = text.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("Invalid unicode escape \\u" + hex);
                            }
                            pos += 4;
                            break;
                        default:
                            throw error("Invalid escape character '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private BigDecimal parseNumber() throws JsonParseException {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (!atEnd()) {
                char c = text.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            String num = text.substring(start, pos);
            try {
                return new BigDecimal(num);
            } catch (NumberFormatException e) {
                throw error("Invalid number '" + num + "'");
            }
        }

        private void expectLiteral(String literal) throws JsonParseException {
            if (!text.startsWith(literal, pos)) {
                throw error("Expected '" + literal + "'");
            }
            pos += literal.length();
        }
    }
}
