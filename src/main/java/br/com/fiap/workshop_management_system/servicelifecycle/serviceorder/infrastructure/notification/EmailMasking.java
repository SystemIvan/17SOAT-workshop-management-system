package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

/**
 * Masks a customer e-mail for log lines (AGENTS.md: no raw personal data in logs), keeping only the first character
 * of the local part and of the domain, e.g. {@code j***@e***}. Shared by every customer notification adapter of
 * {@code servicelifecycle}.
 */
public final class EmailMasking {

    private EmailMasking() {
    }

    public static String mask(String email) {
        if (email == null) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        String maskedLocal = email.charAt(0) + "***";
        String domain = email.substring(atIndex + 1);
        String maskedDomain = domain.isEmpty() ? "***" : domain.charAt(0) + "***";
        return maskedLocal + "@" + maskedDomain;
    }
}
