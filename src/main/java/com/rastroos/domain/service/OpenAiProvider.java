package com.rastroos.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Dialeto OpenAI: {@code /chat/completions} e {@code /embeddings}, corpo com
 * {@code messages}/{@code max_tokens} e resposta em
 * {@code choices[0].message.content}.
 *
 * <p>É também a base de qualquer serviço compatível com essa API. Para usar
 * outro, normalmente basta configurar {@code ai.base-url} e os nomes dos
 * modelos — ver {@link GeminiProvider}, que só troca padrões.
 */
public class OpenAiProvider implements AiProvider {

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.openai.com/v1";
    }

    /** O mais barato da linha 4o, e multimodal (serve também à visão). */
    @Override
    public String defaultChatModel() {
        return "gpt-4o-mini";
    }

    @Override
    public String defaultEmbeddingModel() {
        return "text-embedding-3-small";
    }

    @Override
    public int defaultEmbeddingDimensions() {
        return 1536;
    }

    @Override
    public String chatUrl(String baseRoot) {
        return baseRoot + "/chat/completions";
    }

    @Override
    public String embeddingsUrl(String baseRoot) {
        return baseRoot + "/embeddings";
    }

    @Override
    public Map<String, Object> chatBody(String model, List<Map<String, Object>> messages,
                                        int maxTokens, double temperature,
                                        Map<String, Object> responseFormat) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }
        return body;
    }

    @Override
    public Map<String, Object> embeddingBody(String model, List<String> inputs, int dimensions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", inputs);
        if (dimensions > 0) {
            body.put("dimensions", dimensions);
        }
        return body;
    }

    @Override
    public Map<String, Object> textPart(String text) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "text");
        part.put("text", text);
        return part;
    }

    @Override
    public Map<String, Object> imagePart(String dataUrl, String detail) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("url", dataUrl);
        image.put("detail", detail);

        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "image_url");
        part.put("image_url", image);
        return part;
    }

    @Override
    public Map<String, Object> filePart(String filename, String dataUrl) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("filename", filename);
        file.put("file_data", dataUrl);

        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "file");
        part.put("file", file);
        return part;
    }

    @Override
    public Map<String, Object> jsonSchemaFormat(String name, Map<String, Object> schema) {
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", name);
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", schema);

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("json_schema", jsonSchema);
        return format;
    }

    @Override
    public String readContent(JsonNode response) {
        return response.path("choices").path(0).path("message").path("content").asText(null);
    }

    @Override
    public AiTokenUsage readUsage(JsonNode response) {
        JsonNode u = response.path("usage");
        int prompt = u.path("prompt_tokens").asInt(0);
        int completion = u.path("completion_tokens").asInt(0);
        int total = u.path("total_tokens").asInt(prompt + completion);
        return new AiTokenUsage(prompt, completion, total);
    }

    @Override
    public List<float[]> readEmbeddings(JsonNode response) {
        JsonNode data = response.path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            JsonNode values = item.path("embedding");
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            vectors.add(vector);
        }
        return vectors;
    }

    @Override
    public boolean isOutOfCredit(int status, String responseBody) {
        return status == 429 && responseBody != null && responseBody.contains("insufficient_quota");
    }
}
