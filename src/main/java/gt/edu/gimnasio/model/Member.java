package gt.edu.gimnasio.model;

import java.time.LocalDateTime;

/** Representa a una persona registrada como miembro del gimnasio. */
public class Member {

    private final int id;
    private final String firstNames;
    private final String lastNames;
    private final String documentNumber;
    private final String phone;
    private final String email;
    private final LocalDateTime createdAt;

    public Member(int id, String firstNames, String lastNames, String documentNumber,
                  String phone, String email, LocalDateTime createdAt) {
        this.id = id;
        this.firstNames = firstNames;
        this.lastNames = lastNames;
        this.documentNumber = documentNumber;
        this.phone = phone;
        this.email = email;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getFirstNames() {
        return firstNames;
    }

    public String getLastNames() {
        return lastNames;
    }

    /** Devuelve el nombre completo para presentarlo en controles de selección. */
    public String getFullName() {
        return firstNames + " " + lastNames;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
