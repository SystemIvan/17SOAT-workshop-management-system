package br.com.fiap.workshop_management_system.registration.customer.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identificador fiscal brasileiro imutável. CPF e CNPJ são armazenados em sua
 * representação canônica, contendo apenas dígitos.
 */
public final class TaxId {

    private static final int CPF_LENGTH = 11;
    private static final int CNPJ_LENGTH = 14;
    private static final Pattern CPF_FORMAT = Pattern.compile("(?:\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})");
    private static final Pattern CNPJ_FORMAT = Pattern.compile("(?:\\d{14}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})");
    private static final int[] CPF_FIRST_DIGIT_WEIGHTS = {10, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CPF_SECOND_DIGIT_WEIGHTS = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_FIRST_DIGIT_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_SECOND_DIGIT_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private final String value;
    private final Type type;

    public TaxId(String rawValue) {
        String normalizedValue = normalize(rawValue);
        this.type = identifyType(normalizedValue);
        validate(normalizedValue, type);
        this.value = normalizedValue;
    }

    public String value() {
        return value;
    }

    public Type type() {
        return type;
    }

    private static String normalize(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("O CPF/CNPJ do cliente não pode estar em branco");
        }

        String candidate = rawValue.trim();
        if (!CPF_FORMAT.matcher(candidate).matches() && !CNPJ_FORMAT.matcher(candidate).matches()) {
            throw new IllegalArgumentException("O CPF/CNPJ do cliente possui formato inválido");
        }

        StringBuilder digits = new StringBuilder(candidate.length());
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            if (character >= '0' && character <= '9') {
                digits.append(character);
            }
        }
        return digits.toString();
    }

    private static Type identifyType(String normalizedValue) {
        return switch (normalizedValue.length()) {
            case CPF_LENGTH -> Type.CPF;
            case CNPJ_LENGTH -> Type.CNPJ;
            default -> throw new IllegalArgumentException("O documento do cliente deve ser um CPF ou CNPJ válido");
        };
    }

    private static void validate(String normalizedValue, Type type) {
        if (hasOnlyRepeatedDigits(normalizedValue)) {
            throw new IllegalArgumentException("O CPF/CNPJ do cliente não pode conter apenas dígitos repetidos");
        }

        int firstDigitIndex = normalizedValue.length() - 2;
        int firstDigit = calculateCheckDigit(normalizedValue, type.firstDigitWeights());
        int secondDigit = calculateCheckDigit(normalizedValue, type.secondDigitWeights());
        if (digitAt(normalizedValue, firstDigitIndex) != firstDigit
                || digitAt(normalizedValue, firstDigitIndex + 1) != secondDigit) {
            throw new IllegalArgumentException("O CPF/CNPJ do cliente possui dígitos verificadores inválidos");
        }
    }

    private static boolean hasOnlyRepeatedDigits(String value) {
        return value.chars().allMatch(character -> character == value.charAt(0));
    }

    private static int calculateCheckDigit(String value, int[] weights) {
        int sum = 0;
        for (int index = 0; index < weights.length; index++) {
            sum += digitAt(value, index) * weights[index];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static int digitAt(String value, int index) {
        return value.charAt(index) - '0';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        return object instanceof TaxId taxId && value.equals(taxId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "TaxId[tipo=" + type + "]";
    }

    public enum Type {
        CPF(CPF_FIRST_DIGIT_WEIGHTS, CPF_SECOND_DIGIT_WEIGHTS),
        CNPJ(CNPJ_FIRST_DIGIT_WEIGHTS, CNPJ_SECOND_DIGIT_WEIGHTS);

        private final int[] firstDigitWeights;
        private final int[] secondDigitWeights;

        Type(int[] firstDigitWeights, int[] secondDigitWeights) {
            this.firstDigitWeights = firstDigitWeights;
            this.secondDigitWeights = secondDigitWeights;
        }

        private int[] firstDigitWeights() {
            return firstDigitWeights;
        }

        private int[] secondDigitWeights() {
            return secondDigitWeights;
        }
    }
}
