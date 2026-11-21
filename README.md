# course-seat-allocator

Enrollment, drop and FIFO waitlist promotion for **one course offering**: capacity limits,
a prerequisite check, duplicate protection, and promotion from the waitlist when a seat frees
up. In-memory and thread-safe; plain Java 17 with JUnit 5.

> **Note:** the starter project link in the brief wasn't accessible, so this uses the
> in-memory service fallback the brief allows.

Full design and reasoning: [DESIGN.md](DESIGN.md).

## Prerequisites

- JDK 17 or later
- Git
- No Maven install needed: the Maven Wrapper (`mvnw`, `mvnw.cmd`) downloads it on first run.

## Run the tests

```
mvnw.cmd test        # Windows
./mvnw test          # macOS / Linux
```

The suite runs **321 tests**: 21 unit tests, plus the three concurrency tests, which repeat
100 times each.

## Run the demo

```
mvnw.cmd -q compile exec:java        # Windows
./mvnw -q compile exec:java          # macOS / Linux
```

Course CS201 (capacity 5, waitlist cap 2, requires CS101) fills up, rejects a learner without
CS101, waitlists two, and turns one away when the waitlist is full. Then a waiting learner
loses CS101 and a seat opens: they are skipped and the next eligible learner is promoted.

## Project structure

```
src/main/java/com/courseseat/
  model/        Course, Learner, EnrollStatus, DropStatus, EnrollResult, DropResult
  repository/   LearnerRepository
  service/      EnrollmentService
  Main.java     demo scenario
src/test/java/com/courseseat/
  TestData.java
  model/        CourseTest
  repository/   LearnerRepositoryTest
  service/      EnrollmentServiceTest, EnrollmentServiceConcurrencyTest
```

## Key decisions

- **Skip policy:** eligibility is re-checked at promotion. A waiting learner who is no longer
  eligible is removed from the waitlist and reported as skipped; the next eligible learner
  gets the seat.
- **Bounded waitlist:** one cap per course (2 for capacity 5, from an assumed 20% drop rate
  plus 1). When it is full, the request gets `WAITLIST_FULL`.
- **`LinkedHashSet` for enrolled and waitlist:** FIFO order, no duplicates, O(1)
  `contains` and `remove`.
- **`synchronized` on one service instance per offering:** each whole check-then-act
  sequence is atomic, including the promotion inside `drop`.
- **Results instead of exceptions:** every business outcome is returned as an
  `EnrollResult` or `DropResult` status.
- **Validation upstream:** courses and learners are assumed valid when they reach this
  module.

The reasoning behind each is in [DESIGN.md](DESIGN.md).

## Acceptance criteria → tests

| Criterion | Tests |
|---|---|
| 1. Enroll with capacity + prerequisite | `enrollsWhenSeatAvailable` |
| 2. Full → waitlist once, no duplicates | `waitlistsWhenCourseFull`, `rejectsDuplicateWaitlistRequest`, `rejectsDuplicateEnrollment`, `sameLearnerEnrollingTwiceAtOnceIsEnrolledOnce` |
| 3. Drop promotes earliest eligible | `dropPromotesEarliestEligible`, `keepsFifoOrder` |
| 4. Ineligible not enrolled/promoted; policy consistent | `rejectsWhenPrereqNotMet`, `ineligibleLearnerNotWaitlisted`, `skipsIneligibleLearnerAtFront`, `seatStaysOpenWhenNoneEligible` |
| 5. Invalid/duplicate drops don't corrupt state | `repeatedDropReturnsNotEnrolled`, `unknownLearnerDropIsRejected`, `rejectsNullOrBlankId` |
| 6. Concurrency | `simultaneousEnrollmentsDoNotOverbookCourse`, `sameLearnerEnrollingTwiceAtOnceIsEnrolledOnce`, `simultaneousDropsGiveEachSeatToDifferentLearner` |
| 7. AI assistance documented (prompts and verification) | see [AI_USAGE.md](AI_USAGE.md) |

## Limitations

- In-memory only: state is lost on restart.
- Single JVM and a single course offering.
- The lock isn't fair: FIFO follows processing order under the lock, not arrival time.
- Enrolled learners who later lose eligibility stay enrolled.
- The waitlist cap is an assumption, not derived from data.
- No notifications: promotions and skips are only reported in `DropResult`.

## AI usage

Built with AI assistance: prompts and verification are in [AI_USAGE.md](AI_USAGE.md), and the
working rules given to the assistant are in [CLAUDE.md](CLAUDE.md).
