package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginRequest {
    @JsonProperty("email")
    public String email;

    @JsonProperty("password")
    public String password;
}
