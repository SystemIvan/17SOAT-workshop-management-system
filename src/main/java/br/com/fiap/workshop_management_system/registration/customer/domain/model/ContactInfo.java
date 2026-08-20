package br.com.fiap.workshop_management_system.registration.customer.domain.model;

public record ContactInfo(Email email, Phone phone, Address address) {

    public ContactInfo {
        if (email == null || phone == null) {
            throw new IllegalArgumentException("E-mail e telefone de contato devem ser informados");
        }
    }

    public ContactInfo(String email, String phone) {
        this(new Email(email), new Phone(phone), null);
    }

    public ContactInfo(String email, String phone, Address address) {
        this(new Email(email), new Phone(phone), address);
    }

    public ContactInfo withUpdates(Email newEmail, Phone newPhone, Address newAddress) {
        if (newEmail == null && newPhone == null && newAddress == null) {
            throw new IllegalArgumentException("Informe ao menos um dado de contato para atualização");
        }
        return new ContactInfo(
                newEmail == null ? email : newEmail,
                newPhone == null ? phone : newPhone,
                newAddress == null ? address : newAddress);
    }
}
