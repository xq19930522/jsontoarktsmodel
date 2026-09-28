package com.sincegame.jsontomodel;

import com.sincegame.jsontomodel.json.MiniJson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ArkTsModelGeneratorTest {

    private static final String SAMPLE = "{\n" +
            "  \"code\": 200,\n" +
            "  \"message\": \"ok\",\n" +
            "  \"success\": true,\n" +
            "  \"data\": {\n" +
            "    \"userName\": \"Tom\",\n" +
            "    \"age\": 18,\n" +
            "    \"tags\": [\"a\", \"b\"],\n" +
            "    \"scores\": [1, 2, 3],\n" +
            "    \"flags\": [true, false],\n" +
            "    \"extra\": null,\n" +
            "    \"emptyList\": [],\n" +
            "    \"orders\": [\n" +
            "      {\"orderId\": 1001, \"amount\": 9.99},\n" +
            "      {\"orderId\": 1002, \"amount\": 20.5, \"coupon\": \"SAVE5\"}\n" +
            "    ]\n" +
            "  }\n" +
            "}";

    @Test
    public void interfaceGenerationMatchesHtmlBehavior() throws Exception {
        String expected = "export interface RootModel {\n" +
                "  code: number;\n" +
                "  message: string;\n" +
                "  success: boolean;\n" +
                "  data: Data;\n" +
                "}\n" +
                "\n" +
                "export interface Data {\n" +
                "  userName: string;\n" +
                "  age: number;\n" +
                "  tags: string[];\n" +
                "  scores: number[];\n" +
                "  flags: boolean[];\n" +
                "  extra: Object;\n" +
                "  emptyList: Object[];\n" +
                "  orders: Orders[];\n" +
                "}\n" +
                "\n" +
                "export interface Orders {\n" +
                "  orderId: number;\n" +
                "  amount: number;\n" +
                "  coupon: string;\n" +
                "}\n" +
                "\n";
        assertEquals(expected, ArkTsModelGenerator.generate(SAMPLE, "RootModel",
                ArkTsModelGenerator.Kind.INTERFACE, false));
    }

    @Test
    public void classWithDefaultConstructor() throws Exception {
        String json = "{\"a\": 1}";
        String expected = "export class Root {\n" +
                "  a: number;\n" +
                "  constructor() {}\n" +
                "}\n" +
                "\n";
        assertEquals(expected, ArkTsModelGenerator.generate(json, "Root",
                ArkTsModelGenerator.Kind.CLASS, false));
    }

    @Test
    public void classWithParameterizedConstructor() throws Exception {
        String json = "{\"a\": 1, \"b\": \"x\"}";
        String expected = "export class Root {\n" +
                "  a: number;\n" +
                "  b: string;\n" +
                "  constructor(\n" +
                "    a: number,\n" +
                "    b: string\n" +
                "  ) {\n" +
                "    this.a = a;\n" +
                "    this.b = b;\n" +
                "  }\n" +
                "}\n" +
                "\n";
        assertEquals(expected, ArkTsModelGenerator.generate(json, "Root",
                ArkTsModelGenerator.Kind.CLASS, true));
    }

    @Test
    public void pascalCaseConversion() {
        assertEquals("UserName", ArkTsModelGenerator.toPascalCase("userName"));
        assertEquals("RootModel", ArkTsModelGenerator.toPascalCase("root_model"));
        assertEquals("DataList", ArkTsModelGenerator.toPascalCase("data-list"));
        assertEquals("Order2", ArkTsModelGenerator.toPascalCase("order2"));
    }

    @Test
    public void typeConflictKeepsFirstRealType() throws Exception {
        // data 在第一个元素里是 number，第二个元素里是 string —— 以首次推断为准
        String json = "{\"items\": [{\"v\": 1}, {\"v\": \"s\"}]}";
        String out = ArkTsModelGenerator.generate(json, "Root",
                ArkTsModelGenerator.Kind.INTERFACE, false);
        assertTrue(out.contains("  v: number;"));
    }

    @Test
    public void invalidJsonThrows() {
        try {
            ArkTsModelGenerator.generate("{not json}", "Root",
                    ArkTsModelGenerator.Kind.INTERFACE, false);
            fail("should throw");
        } catch (MiniJson.JsonParseException expected) {
            // ok
        }
    }

    @Test
    public void escapedAndUnicodeStrings() throws Exception {
        String json = "{\"s\": \"a\\n\\t\\u0041\\\"b\"}";
        String out = ArkTsModelGenerator.generate(json, "Root",
                ArkTsModelGenerator.Kind.INTERFACE, false);
        assertTrue(out.contains("  s: string;"));
    }

    @Test
    public void nonObjectRootThrows() {
        try {
            ArkTsModelGenerator.generate("[1,2,3]", "Root",
                    ArkTsModelGenerator.Kind.INTERFACE, false);
            fail("should throw");
        } catch (MiniJson.JsonParseException expected) {
            assertTrue(expected.getMessage().contains("JSON object"));
        }
    }
}
