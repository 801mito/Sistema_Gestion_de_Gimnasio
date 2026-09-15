package gt.edu.gimnasio.model;

import java.time.LocalDate;

/** Representa una membresía asignada a un miembro del gimnasio. */
public class Membership {

    private final int id;
    private final int memberId;
    private final String memberName;
    private final String planName;
    private final String status;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public Membership(int id, int memberId, String memberName, String planName,
                      String status, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.memberId = memberId;
        this.memberName = memberName;
        this.planName = planName;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public int getId() {
        return id;
    }

    public int getMemberId() {
        return memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getPlanName() {
        return planName;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
