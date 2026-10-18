package cat.albirar.dynmod.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Test to verify that Instant fields are serialized as ISO-8601 strings, not as numbers.
 */
class InstantSerializationTest {

    @Test
    void testInstantSerializedAsString() throws Exception {
        // Create ObjectMapper with JavaTimeModule
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        
        // Create a test object with Instant and @JsonFormat annotation
        class TestObject {
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            public Instant timestamp;
        }
        
        TestObject obj = new TestObject();
        obj.timestamp = Instant.now();

        // When: Serializing to JSON
        String jsonString = objectMapper.writeValueAsString(obj);
        
        // Then: The Instant field should be serialized as string, not number
        // Check that the JSON contains quotes around the timestamp
        assertTrue(jsonString.contains("\"timestamp\":\""), 
            "timestamp should be serialized as string, got: " + jsonString);
    }
}
