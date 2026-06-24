package com.borinquenkid.branchtest.repository;

import com.borinquenkid.branchtest.model.response.UserResponse;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Converter
@Component
public class UserResponseConverter implements AttributeConverter<UserResponse, String> {

    private final ObjectMapper objectMapper;

    public UserResponseConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(UserResponse response) {
        return objectMapper.writeValueAsString(response);
    }

    @Override
    public UserResponse convertToEntityAttribute(String json) {
        return objectMapper.readValue(json, UserResponse.class);
    }
}
