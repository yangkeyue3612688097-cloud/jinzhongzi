package org.example.backend.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class RegisterDTO {
    @NotBlank(message = "username required")
    @Size(min = 3, max = 20)
    private String username;
    @NotBlank(message = "password required")
    @Size(min = 6, max = 30)
    private String password;
    private String email;
}
