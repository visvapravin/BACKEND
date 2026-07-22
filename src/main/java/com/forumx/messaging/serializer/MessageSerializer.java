package com.forumx.messaging.serializer;
import com.fasterxml.jackson.databind.ObjectMapper; import com.fasterxml.jackson.databind.SerializationFeature; import com.fasterxml.jackson.databind.DeserializationFeature; import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
public final class MessageSerializer { private MessageSerializer() {} public static ObjectMapper objectMapper() { return new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); } }
