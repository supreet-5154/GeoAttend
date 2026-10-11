package com.geoattend.backend.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

class MemberJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsPasswordOnInputButDoesNotExposeItOnOutput() throws Exception {
        Member member = objectMapper.readValue(
                "{\"password\":\"temporary-password\"}",
                Member.class
        );

        assertEquals("temporary-password", member.getPassword());

        String json = objectMapper.writeValueAsString(member);
        assertFalse(json.contains("temporary-password"));
        assertFalse(json.contains("passwordHash"));
    }
}