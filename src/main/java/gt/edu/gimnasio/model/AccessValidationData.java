package gt.edu.gimnasio.model;

import java.time.LocalDate;

/** Reúne los datos necesarios para validar un código y su membresía asociada. */
public class AccessValidationData {

    private final int accessCodeId;
    private final String accessCode;
    private final boolean accessCodeActive;
    private final String memberName;
    private final String membershipStatus;
    private final LocalDate membershipStartDate;
    private final LocalDate membershipEndDate;

    public AccessValidationData(int accessCodeId, String accessCode, boolean accessCodeActive,
                                String memberName, String membershipStatus,
                                LocalDate membershipStartDate, LocalDate membershipEndDate) {
        this.accessCodeId = accessCodeId;
        this.accessCode = accessCode;
        this.accessCodeActive = accessCodeActive;
        this.memberName = memberName;
        this.membershipStatus = membershipStatus;
        this.membershipStartDate = membershipStartDate;
        this.membershipEndDate = membershipEndDate;
    }

    public int getAccessCodeId() {
        return accessCodeId;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public boolean isAccessCodeActive() {
        return accessCodeActive;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getMembershipStatus() {
        return membershipStatus;
    }

    public LocalDate getMembershipStartDate() {
        return membershipStartDate;
    }

    public LocalDate getMembershipEndDate() {
        return membershipEndDate;
    }
}
