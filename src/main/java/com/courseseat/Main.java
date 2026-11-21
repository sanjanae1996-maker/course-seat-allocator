package com.courseseat;

import com.courseseat.model.Course;
import com.courseseat.model.DropResult;
import com.courseseat.model.EnrollResult;
import com.courseseat.model.Learner;
import com.courseseat.repository.LearnerRepository;
import com.courseseat.service.EnrollmentService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Runs the demo scenario from DESIGN.md section 11. */
public class Main {

    private static final String CS101 = "CS101";

    private final Course course = new Course("CS201", 5, 2, Set.of(CS101));
    private final LearnerRepository learners = new LearnerRepository();
    private final EnrollmentService service = new EnrollmentService(course, learners);

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        addLearners();
        System.out.println("Course " + course.id() + ": capacity " + course.capacity()
                + ", waitlist cap " + course.maxWaitlistSize()
                + ", requires " + course.requiredCourseIds());

        header("Seats available");
        enroll("L001");
        enroll("L002");
        enroll("L003");
        enroll("L005");
        enroll("L006");

        header("Prerequisite not met");
        enroll("L004");

        header("Course full: waitlist and cap");
        enroll("L007");
        enroll("L008");
        enroll("L009");

        header("Duplicate request");
        enroll("L001");

        header("Drop with promotion (skip policy)");
        revokeCs101("L007");
        drop("L002");

        header("Repeated drop");
        drop("L002");
    }

    private void addLearners() {
        Set<String> cs101 = Set.of(CS101);
        learners.save(new Learner("L001", "Asha", cs101));
        learners.save(new Learner("L002", "Bala", cs101));
        learners.save(new Learner("L003", "Chitra", cs101));
        learners.save(new Learner("L004", "Dev", Set.of("MA101")));
        learners.save(new Learner("L005", "Esha", cs101));
        learners.save(new Learner("L006", "Farah", cs101));
        learners.save(new Learner("L007", "Gopi", cs101));
        learners.save(new Learner("L008", "Hari", cs101));
        learners.save(new Learner("L009", "Isha", cs101));
    }

    private void enroll(String learnerId) {
        EnrollResult result = service.enroll(learnerId);
        System.out.printf("%-22s-> %s%n", "enroll " + label(learnerId), result.status());
        printState();
    }

    private void drop(String learnerId) {
        DropResult result = service.drop(learnerId);
        String promoted = result.promotedId().map(this::label).orElse("none");
        List<String> skipped = result.skippedIds().stream().map(this::label).toList();
        System.out.printf("%-22s-> %s   promoted: %s   skipped: %s%n",
                "drop " + label(learnerId), result.status(), promoted, skipped);
        printState();
    }

    /** Saves an updated record, as another module would when a completion is revoked. */
    private void revokeCs101(String learnerId) {
        Learner learner = learners.find(learnerId).orElseThrow();
        Set<String> remaining = new HashSet<>(learner.completedCourseIds());
        remaining.remove(CS101);
        learners.save(new Learner(learner.id(), learner.name(), remaining));
        System.out.println("revoke " + CS101 + " for " + label(learnerId) + ", who is waiting");
    }

    private void header(String title) {
        System.out.println();
        System.out.println("--- " + title + " ---");
    }

    private void printState() {
        System.out.println("  enrolled: " + service.getEnrolled() + "   waitlist: " + service.getWaitlist());
    }

    private String label(String learnerId) {
        String name = learners.find(learnerId).map(Learner::name).orElse("?");
        return learnerId + " (" + name + ")";
    }
}
