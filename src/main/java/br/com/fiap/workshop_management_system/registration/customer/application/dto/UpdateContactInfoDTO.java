package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.Optional;
import java.util.regex.Pattern;

@JsonDeserialize(using = UpdateContactInfoDTO.Deserializer.class)
public record UpdateContactInfoDTO(
        @Schema(description = "Novo e-mail; quando omitido, preserva o valor atual")
        Optional<String> email,
        @Schema(description = "Novo telefone brasileiro ou E.164; quando omitido, preserva o valor atual")
        Optional<String> phone,
        @Schema(description = "Novo endereço completo; quando omitido, preserva o endereço atual")
        Optional<@Valid AddressDTO> address) {

    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public UpdateContactInfoDTO {
        email = email == null ? Optional.empty() : email;
        phone = phone == null ? Optional.empty() : phone;
        address = address == null ? Optional.empty() : address;
    }

    @AssertTrue(message = "Informe ao menos um dado de contato para atualização")
    @Schema(hidden = true)
    public boolean isAnyContactFieldProvided() {
        return email.isPresent() || phone.isPresent() || address.isPresent();
    }

    @AssertTrue(message = "O e-mail deve ser válido e ter até 254 caracteres")
    @Schema(hidden = true)
    public boolean isEmailValidWhenProvided() {
        return email.map(value -> !value.isBlank()
                && value.length() <= 254
                && EMAIL_FORMAT.matcher(value).matches()).orElse(true);
    }

    @AssertTrue(message = "O telefone não pode estar em branco e deve ter até 32 caracteres")
    @Schema(hidden = true)
    public boolean isPhoneValidWhenProvided() {
        return phone.map(value -> !value.isBlank() && value.length() <= 32).orElse(true);
    }

    public static final class Deserializer extends ValueDeserializer<UpdateContactInfoDTO> {

        @Override
        public UpdateContactInfoDTO deserialize(JsonParser parser, DeserializationContext context)
                throws JacksonException {
            JsonNode object = context.readTree(parser);
            if (!object.isObject()) {
                return context.reportInputMismatch(UpdateContactInfoDTO.class,
                        "As informações de contato devem ser um objeto JSON");
            }
            return new UpdateContactInfoDTO(
                    readText(object, "email", context),
                    readText(object, "phone", context),
                    readAddress(object, context));
        }

        private static Optional<String> readText(
                JsonNode object,
                String fieldName,
                DeserializationContext context) throws JacksonException {
            JsonNode value = object.get(fieldName);
            if (value == null) {
                return Optional.empty();
            }
            if (!value.isTextual()) {
                return context.reportInputMismatch(UpdateContactInfoDTO.class,
                        "O campo " + fieldName + " deve ser um texto não nulo");
            }
            return Optional.of(value.textValue());
        }

        private static Optional<AddressDTO> readAddress(
                JsonNode object,
                DeserializationContext context) throws JacksonException {
            JsonNode value = object.get("address");
            if (value == null) {
                return Optional.empty();
            }
            if (!value.isObject()) {
                return context.reportInputMismatch(UpdateContactInfoDTO.class,
                        "O endereço informado deve ser um objeto JSON não nulo");
            }
            return Optional.of(context.readTreeAsValue(value, AddressDTO.class));
        }
    }
}
