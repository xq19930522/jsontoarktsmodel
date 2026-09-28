package com.sincegame.jsontomodel;

import com.sincegame.jsontomodel.json.MiniJson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON -> ArkTS model 生成器。
 *
 * 功能与生成规则完全还原自 jsonToArkTS.html：
 * <ul>
 *   <li>null -> Object</li>
 *   <li>number -> number, string -> string, boolean -> boolean</li>
 *   <li>空数组 -> Object[]</li>
 *   <li>数组按首个元素推断：number[] / string[] / boolean[] / 对象数组递归生成 PascalCase(key)[]</li>
 *   <li>嵌套对象 -> 递归生成 PascalCase(key) 类/interface</li>
 *   <li>字段类型以首次推断为准，仅当占位类型 Object / Object[] 时才被后续推断覆盖</li>
 * </ul>
 */
public final class ArkTsModelGenerator {

    public enum Kind {
        INTERFACE, CLASS
    }

    private ArkTsModelGenerator() {
    }

    /**
     * 由 JSON 文本生成 ArkTS model 源码。
     *
     * @param json          JSON 文本
     * @param className     根类型名
     * @param kind          interface 或 class
     * @param parameterized 仅 class 生效：是否生成带参构造函数
     * @return ArkTS 源码（可能包含多个 export 块）
     * @throws MiniJson.JsonParseException JSON 解析失败
     */
    public static String generate(String json, String className, Kind kind, boolean parameterized)
            throws MiniJson.JsonParseException {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map)) {
            throw new MiniJson.JsonParseException("Root element must be a JSON object", 0);
        }
        Map<String, Map<String, String>> classes = new LinkedHashMap<>();
        collect(root, className, classes);

        String keyword = kind == Kind.CLASS ? "class" : "interface";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> classEntry : classes.entrySet()) {
            String typeName = toPascalCase(classEntry.getKey());
            Map<String, String> fields = classEntry.getValue();

            out.append("export ").append(keyword).append(' ').append(typeName).append(" {\n");
            for (Map.Entry<String, String> field : fields.entrySet()) {
                out.append("  ").append(field.getKey()).append(": ").append(field.getValue()).append(";\n");
            }

            if (kind == Kind.CLASS) {
                if (parameterized) {
                    out.append("  constructor(\n");
                    List<String> names = new ArrayList<>(fields.keySet());
                    for (int i = 0; i < names.size(); i++) {
                        String name = names.get(i);
                        out.append("    ").append(name).append(": ").append(fields.get(name));
                        out.append(i < names.size() - 1 ? ",\n" : "\n");
                    }
                    out.append("  ) {\n");
                    for (String name : names) {
                        out.append("    this.").append(name).append(" = ").append(name).append(";\n");
                    }
                    out.append("  }\n");
                } else {
                    out.append("  constructor() {}\n");
                }
            }
            out.append("}\n\n");
        }
        return out.toString();
    }

    /**
     * 递归收集所有类的字段定义（对应 HTML 中的 generateItem）。
     */
    private static void collect(Object value, String className, Map<String, Map<String, String>> classes) {
        if (!(value instanceof Map)) {
            return;
        }
        Map<String, Object> object = MiniJson.asObject(value);
        Map<String, String> fields = classes.computeIfAbsent(className, k -> new LinkedHashMap<>());

        for (Map.Entry<String, Object> entry : object.entrySet()) {
            String key = entry.getKey();
            Object fieldValue = entry.getValue();
            String type;
            try {
                type = inferType(fieldValue, key, classes);
            } catch (RuntimeException e) {
                // 对应 HTML 中 catch 后回退 Object 的行为
                type = "Object";
            }
            String existing = fields.get(key);
            if (existing == null || "Object".equals(existing) || "Object[]".equals(existing)) {
                fields.put(key, type);
            }
        }
    }

    private static String inferType(Object value, String key, Map<String, Map<String, String>> classes) {
        if (value == null) {
            return "Object";
        }
        if (value instanceof BigDecimal || value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof List) {
            List<Object> array = MiniJson.asArray(value);
            if (array.isEmpty()) {
                return "Object[]";
            }
            Object first = array.get(0);
            if (first == null) {
                return "Object[]";
            }
            if (first instanceof BigDecimal || first instanceof Number) {
                return "number[]";
            }
            if (first instanceof Boolean) {
                return "boolean[]";
            }
            if (first instanceof String) {
                return "string[]";
            }
            if (first instanceof Map) {
                String itemClass = toPascalCase(key);
                for (Object element : array) {
                    if (element instanceof Map) {
                        collect(element, itemClass, classes);
                    }
                }
                return itemClass + "[]";
            }
            // 数组的数组等嵌套情况，HTML 原始实现同样退化为 Object[]
            return "Object[]";
        }
        if (value instanceof Map) {
            String nestedClass = toPascalCase(key);
            collect(value, nestedClass, classes);
            return nestedClass;
        }
        return "Object";
    }

    /**
     * 命名转换。HTML 原版在单词首大写的基础上：
     * userName -> UserName；另外对 ArkTS 做了增强 ——
     * - / _ 等非字母数字分隔符会被去掉并使下一个字符大写（data-list -> DataList），
     *   原 HTML 实现会保留分隔符（data-list -> Data-list），生成非法 ArkTS 标识符。
     */
    static String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        // 与 JS 版本逐步等价：首字母转大写 -> 大写字母前插空格 -> 单词首字母大写 -> 去空白
        String step1 = input.substring(0, 1).toUpperCase() + input.substring(1);
        String step2 = step1.replaceAll("([A-Z])", " $1");
        StringBuilder sb = new StringBuilder(step2.length());
        boolean boundary = true;
        for (int i = 0; i < step2.length(); i++) {
            char c = step2.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(boundary ? Character.toUpperCase(c) : c);
                boundary = false;
            } else {
                // 分隔符：丢弃，并让下一个字母数字字符大写
                boundary = true;
            }
        }
        return sb.toString();
    }
}
