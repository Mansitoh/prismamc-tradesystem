package com.prismamc.trade.model;

import java.util.Map;
import java.util.HashMap;
import org.bson.Document;

public class Message {
    private final String key;
    private final Map<String, String> translations;

    public Message(String key, Map<String, String> translations) {
        this.key = key;
        this.translations = new HashMap<>(translations);
    }

    public Message(Document doc) {
        this.key = doc.getString("key");
        this.translations = new HashMap<>();
        Document translations = doc.get("translations", Document.class);
        if (translations != null) {
            for (String lang : translations.keySet()) {
                this.translations.put(lang, translations.getString(lang));
            }
        }
    }

    public Document toDocument() {
        Document translationsDoc = new Document();
        translations.forEach(translationsDoc::append);
        
        return new Document()
                .append("key", key)
                .append("translations", translationsDoc);
    }

    public String getKey() {
        return key;
    }

    public String getTranslation(String language) {
        return translations.getOrDefault(language, translations.getOrDefault("en", "Message not found"));
    }

    public Map<String, String> getTranslations() {
        return new HashMap<>(translations);
    }
}