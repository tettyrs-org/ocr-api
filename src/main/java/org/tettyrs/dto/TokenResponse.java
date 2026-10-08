package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TokenResponse {

    @JsonProperty("token")
    public String token;

    @JsonProperty("expires_in")
    public Integer expiresIn;

    @JsonProperty("token_type")
    public String tokenType;
}
