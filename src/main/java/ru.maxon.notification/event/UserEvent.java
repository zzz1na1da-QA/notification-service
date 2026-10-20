package ru.maxon.notification.event;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserEvent(

        @NotNull(message = "Операция обязательна")
        UserOperation operation,

        @NotBlank(message = "Email обязателен")
        @Email(message = "Укажите корректный email")
        String email

) {
}