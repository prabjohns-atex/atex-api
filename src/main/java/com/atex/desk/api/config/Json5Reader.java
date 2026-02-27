package com.atex.desk.api.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.util.regex.Pattern;

/**
 * Reads JSON5-style content (comments, trailing commas) and produces standard JSON.
 */
public final class Json5Reader
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Matches // single-line comments (not inside strings)
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)^(\\s*)//.*$");
    // Matches /* block comments */
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");
    // Matches trailing commas before } or ]
    private static final Pattern TRAILING_COMMA = Pattern.compile(",\\s*([}\\]])");

    private Json5Reader() {}

    /**
     * Parse JSON5-style text and return a standard JSON string.
     */
    public static String toJson(String json5)
    {
        // Strip comments
        String cleaned = BLOCK_COMMENT.matcher(json5).replaceAll("");
        cleaned = LINE_COMMENT.matcher(cleaned).replaceAll("$1");
        // Remove trailing commas
        cleaned = TRAILING_COMMA.matcher(cleaned).replaceAll("$1");

        // Parse with Gson lenient mode to handle remaining JSON5 quirks
        JsonReader reader = new JsonReader(new StringReader(cleaned));
        reader.setLenient(true);
        JsonElement element = JsonParser.parseReader(reader);
        return GSON.toJson(element);
    }

    /**
     * Parse JSON5-style text and return a parsed JsonElement.
     */
    public static JsonElement parse(String json5)
    {
        String cleaned = BLOCK_COMMENT.matcher(json5).replaceAll("");
        cleaned = LINE_COMMENT.matcher(cleaned).replaceAll("$1");
        cleaned = TRAILING_COMMA.matcher(cleaned).replaceAll("$1");

        JsonReader reader = new JsonReader(new StringReader(cleaned));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
    }
}
