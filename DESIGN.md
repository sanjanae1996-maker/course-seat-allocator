# Course Seat Allocator — Design Specification

Enrollment, drop, and FIFO waitlist promotion for **one course offering**.
This document is the single source of truth for the implementation. If a decision
changes, this file is updated in its own commit.

---

## 1. Scope

**In scope**
- Enroll a learner into one course offering, subject to capacity and a prerequisite rule.
- Bounded FIFO waitlist when the course is full.
- Drop an enrolled learner (frees a seat and promotes from the waitlist) or a waitlisted learner (leaves the queue).
- Thread-safe behaviour within a single JVM.
- JUnit 5 tests and a runnable demo (`Main`).

**Out of scope** (per the brief)
- Frontend, course catalog, payments, identity integration, multi-term registration, deployment, database.
- Multiple course offerings.
- Notifications to learners.
- A configurable policy engine.

**Starter project:** the starter link in the brief was not accessible, so this uses the
minimal in-memory service fallback the brief allows.

---

## 2. Domain model

All model types are immutable Java records, except the repository.

| Type | Kind | Fields / purpose |
|---|---|---|
| `Course` | record | `id`, `capacity`, `maxWaitlistSize`, `requiredCourseIds: Set<String>` |
| `Learner` | record | `id`, `name`, `completedCourseIds: Set<String>` |
| `LearnerRepository` | class | In-memory store of learners: `find(id) → Optional<Learner>`, `save(Learner)` |
| `EnrollStatus` | enum | Outcomes of `enroll` (section 5) |
| `DropStatus` | enum | Outcomes of `drop` (section 6) |
| `EnrollResult` | record | `status: EnrollStatus`, `learnerId` |
| `DropResult` | record | `status: DropStatus`, `droppedId`, `promotedId: Optional<String>`, `skippedIds: List<String>` |

Courses and learners are created and validated by other modules (e.g. a CourseService and
LearnerService that validate input before saving). They are out of scope here; this module
assumes it receives valid data. Records copy their sets with `Set.copyOf` so they stay
immutable.

### Collections

| Where | Type | Stores | Why |
|---|---|---|---|
| `LearnerRepository.store` | `ConcurrentHashMap<String, Learner>` | learner ID → learner | O(1) lookup; safe when updated outside the service lock (e.g. by tests) |
| `Course.requiredCourseIds` | immutable `Set<String>` | prerequisite course IDs | membership check only |
| `Learner.completedCourseIds` | immutable `Set<String>` | completed course IDs | `containsAll` eligibility check |
| `EnrollmentService.enrolled` | `LinkedHashSet<String>` | learner IDs holding a seat | no duplicates, O(1) operations, stable order for tests and demo |
| `EnrollmentService.waitlist` | `LinkedHashSet<String>` | learner IDs in arrival order | FIFO + no duplicates + O(1) `contains` / `remove(x)` |
| `promoteNext()` local | `ArrayList<String>` | IDs skipped during one promotion | temporary |
| `DropResult.skippedIds`, getters | immutable `List<String>` | copies | callers can never modify service state |

Only **learner IDs** are stored in `enrolled`, `waitlist` and results. Learner details live
only in the repository, and are read fresh at promotion time.

**Why `LinkedHashSet` for the waitlist:** it is backed by a `LinkedHashMap`, giving FIFO
iteration (head → tail), O(1) `contains`, O(1) `remove(x)`, and automatic duplicate
rejection. The front element is taken with `iterator().next()` + `iterator.remove()` (O(1)).
`ArrayDeque` would need O(n) `contains`/`remove(x)` and a manual duplicate check; blocking
queues are for producer/consumer threads, which this design does not have.

---

## 3. Service boundary and API

One `EnrollmentService` instance represents **one course offering**. It owns the offering's
state (`enrolled`, `waitlist`) and the lock that protects it.

```java
public EnrollmentService(Course course, LearnerRepository learners)

public synchronized EnrollResult enroll(String learnerId)
public synchronized DropResult   drop(String learnerId)
public synchronized List<String> getEnrolled()   // immutable copy, enrollment order
public synchronized List<String> getWaitlist()   // immutable copy, front first
```

- Business outcomes are **returned as results, never thrown**.
- Exceptions are reserved for programming errors (e.g. a null `Course` or repository passed
  to the `EnrollmentService` constructor).
- The getters are the read side of the API: used by tests, the demo, and a future `GET` endpoint.

### REST mapping (designed, not implemented)

The domain logic is framework-free; a REST controller would be a thin adapter over it.

| Endpoint | Maps to | Responses |
|---|---|---|
| `POST /offerings/{id}/enrollments` body `{ "learnerId": "L001" }` | `enroll` | `201` ENROLLED · `202` WAITLISTED · `409` ALREADY_ENROLLED / ALREADY_WAITLISTED / WAITLIST_FULL · `422` PREREQ_NOT_MET · `404` UNKNOWN_LEARNER · `400` INVALID_REQUEST |
| `DELETE /offerings/{id}/enrollments/{learnerId}` | `drop` | `200` DROPPED / REMOVED_FROM_WAITLIST · `404` NOT_ENROLLED / UNKNOWN_LEARNER · `400` INVALID_REQUEST |
| `GET /offerings/{id}` | getters | `200` capacity, enrolled, waitlist |

`DropResult` is an internal result describing every state change a drop caused. A public
response would return only the caller's own status; `promotedId` and `skippedIds` would feed
notifications and audit, not the dropping learner's response.

---

## 4. Prerequisite rule

- A learner is **eligible** if `learner.completedCourseIds` contains **all** of
  `course.requiredCourseIds`.
- A course with no required courses accepts every learner.
- Course IDs are compared exactly (case-sensitive).
- Eligibility is checked **on enroll** and **re-checked at promotion**, because a learner's
  record can change while they wait (e.g. a completion is revoked).
- A learner who loses eligibility **after** being enrolled stays enrolled. Re-checks happen
  only at promotion.

---

## 5. Enroll rules

Checks run in this order; the first match returns. Rejected requests are never stored.

| # | Condition | Result | State change |
|---|---|---|---|
| 1 | `learnerId` null or blank | `INVALID_REQUEST` | none |
| 2 | learner not in repository | `UNKNOWN_LEARNER` | none |
| 3 | already in `enrolled` | `ALREADY_ENROLLED` | none |
| 4 | already in `waitlist` | `ALREADY_WAITLISTED` | none (keeps their place) |
| 5 | prerequisite not met | `PREREQ_NOT_MET` | none |
| 6 | `enrolled.size() < capacity` | `ENROLLED` | added to `enrolled` |
| 7 | `waitlist.size() < maxWaitlistSize` | `WAITLISTED` | added to back of `waitlist` |
| 8 | otherwise | `WAITLIST_FULL` | none |

The prerequisite check (5) comes before capacity, so an ineligible learner always gets the
more useful answer, even when the course and waitlist are full.

A learner who dropped, or was skipped, may enroll again later; it is treated as a new request
(back of the queue if the course is full).

---

## 6. Drop and promotion rules

| # | Condition | Result | State change |
|---|---|---|---|
| 1 | `learnerId` null or blank | `INVALID_REQUEST` | none |
| 2 | learner not in repository | `UNKNOWN_LEARNER` | none |
| 3 | learner is in `waitlist` | `REMOVED_FROM_WAITLIST` | removed from `waitlist`; no seat opens, no promotion |
| 4 | learner not in `enrolled` (incl. repeated drop) | `NOT_ENROLLED` | none |
| 5 | learner is in `enrolled` | `DROPPED` | removed from `enrolled`, then `promoteNext()` |

### Promotion (`promoteNext`)

Runs inside `drop`, under the same lock, so the freed seat can never be taken by a new
request before the waitlist is served. Dropping is the only event that frees a seat, so this
is the only place promotion happens.

```
for each learner ID from the front of the waitlist:
    remove it from the waitlist
    look up the learner's current record in the repository
    if eligible  → add to enrolled, stop (promotedId = this ID)
    else         → add to skippedIds, continue
if the waitlist runs out → seat stays open (promotedId = empty)
```

### Ineligible learner at the front: **skip policy**

A waitlisted learner who is no longer eligible when a seat opens is **removed from the
waitlist** and reported in `DropResult.skippedIds`. The next eligible learner is promoted.

Reasons for skip over retain:
- The queue stays clean; an ineligible learner never blocks or re-scans on every drop.
- It keeps the invariant "if the waitlist is non-empty, the course is full" (section 8).

The policy is applied in exactly one place (`promoteNext`) and covered by tests.

---

## 7. Waitlist cap

Seats only open through drops, so the waitlist only needs to cover the drops expected,
plus a buffer. No historical data is available, so:

```
expected drops = capacity × 20% (assumed drop rate)
waitlist cap   = expected drops (rounded up) + 1 buffer
```

| Capacity | Expected drops | Waitlist cap |
|---|---|---|
| 5 (demo) | 1 | **2** |

The cap is a single per-course number (`Course.maxWaitlistSize`), fixed at creation.
In production it would be tuned from observed drop rates. The waitlist cannot grow with
request volume regardless: each learner can appear at most once.

---

## 8. Invariants

These hold after every operation, including rejected and concurrent ones:

1. `enrolled.size() <= capacity`
2. `waitlist.size() <= maxWaitlistSize`
3. No learner appears twice in `enrolled` or twice in `waitlist`.
4. No learner is in both `enrolled` and `waitlist`.
5. Every learner in `enrolled` or `waitlist` exists in the repository and was eligible when added.
6. If `waitlist` is non-empty, then `enrolled.size() == capacity` (holds because of the skip policy).
7. A rejected request (`INVALID_REQUEST`, `UNKNOWN_LEARNER`, `ALREADY_*`, `PREREQ_NOT_MET`,
   `WAITLIST_FULL`, `NOT_ENROLLED`) leaves all state unchanged.

---

## 9. Concurrency

- Each request is a thread; all threads share **one** `EnrollmentService` instance.
- `enroll`, `drop`, `getEnrolled` and `getWaitlist` are `synchronized` on the service instance,
  which is the same object that owns `enrolled` and `waitlist`.
- The whole check-then-act sequence (validation → eligibility → capacity → write) runs
  under the one lock. Concurrent collections alone would not be enough, because they make
  single operations atomic, not the sequence.
- Promotion runs inside `drop` under the same lock, so drop + promotion is atomic.
- A thread that finds the lock held waits briefly; it is not rejected.
- `LearnerRepository` uses `ConcurrentHashMap` so it is safe on its own.
- **Fairness:** `synchronized` is not fair. FIFO order is the order in which requests are
  processed under the lock, not network arrival time.

`synchronized` was chosen over `ReentrantLock` for simplicity; `ReentrantLock` would be the
choice if a `tryLock` timeout or fair locking became a requirement.

**Scaling (not implemented):** multiple offerings → one lock per offering
(`ConcurrentHashMap<offeringId, CourseOffering>`); multiple app instances → move state to a
database and lock the offering row (`SELECT … FOR UPDATE`) or use optimistic locking with a
version column, plus a unique constraint on (offering, learner); notifications → publish an
event after commit.

---

## 10. Test plan

JUnit 5 unit tests against the real in-memory repository (no mocks needed: there are no
external dependencies). Every test asserts the result **and** the resulting state.
Unit tests use small fixtures (e.g. capacity 2, waitlist cap 1) via a `TestData` helper.

### `EnrollmentServiceTest` (with `@Nested` groups)

**Enroll**
- `enrollsWhenSeatAvailable`
- `rejectsWhenPrereqNotMet`
- `rejectsDuplicateEnrollment`
- `rejectsUnknownLearner`
- `rejectsNullOrBlankId`

**Waitlist**
- `waitlistsWhenCourseFull`
- `keepsFifoOrder`
- `rejectsDuplicateWaitlistRequest`
- `ineligibleLearnerNotWaitlisted`
- `returnsWaitlistFullAtCap`

**Drop**
- `dropPromotesEarliestEligible`
- `skipsIneligibleLearnerAtFront` (revokes a prerequisite via `LearnerRepository.save` while waiting)
- `seatStaysOpenWhenNoneEligible`
- `waitlistedLearnerCanLeave`
- `repeatedDropReturnsNotEnrolled`
- `unknownLearnerDropIsRejected`

### `EnrollmentServiceConcurrencyTest`
- `onlyCapacityLearnersEnrollUnderRace` — `@RepeatedTest(100)`; one shared service
  (capacity 5, cap 2); 10 threads released together by a `CountDownLatch` →
  exactly 5 `ENROLLED`, 2 `WAITLISTED`, 3 `WAITLIST_FULL`; no duplicates.
- `concurrentDropsPromoteEachOnce` — course full with 2 waitlisted; 2 enrolled learners
  drop concurrently → both waitlisted learners promoted, each exactly once.

### Acceptance criteria coverage

| Criterion | Tests |
|---|---|
| 1. Enroll with capacity + prerequisite | `enrollsWhenSeatAvailable` |
| 2. Full → waitlist once, no duplicates | `waitlistsWhenCourseFull`, `rejectsDuplicateWaitlistRequest`, `rejectsDuplicateEnrollment` |
| 3. Drop promotes earliest eligible | `dropPromotesEarliestEligible`, `keepsFifoOrder` |
| 4. Ineligible not enrolled/promoted; policy consistent | `rejectsWhenPrereqNotMet`, `ineligibleLearnerNotWaitlisted`, `skipsIneligibleLearnerAtFront`, `seatStaysOpenWhenNoneEligible` |
| 5. Invalid/duplicate drops don't corrupt state | `repeatedDropReturnsNotEnrolled`, `unknownLearnerDropIsRejected`, `rejectsNullOrBlankId` |
| 6. Concurrency | `EnrollmentServiceConcurrencyTest` |

---

## 11. Demo scenario (`Main`)

Course `CS201`, capacity 5, waitlist cap 2, requires `CS101`. Prints each request, its
result, and the enrolled list and waitlist after each step.

| Step | Request | Expected |
|---|---|---|
| 1 | enroll L001, L002, L003, L005, L006 | `ENROLLED` ×5 |
| 2 | enroll L004 (no CS101) | `PREREQ_NOT_MET` |
| 3 | enroll L007, L008 | `WAITLISTED` ×2 |
| 4 | enroll L009 | `WAITLIST_FULL` |
| 5 | enroll L001 again | `ALREADY_ENROLLED` |
| 6 | revoke CS101 for L007, then drop L002 | `DROPPED`, promoted L008, skipped [L007] |
| 7 | drop L002 again | `NOT_ENROLLED` |

---

## 12. Project setup

- Java 17 (`maven.compiler.release` = 17), Maven with the Maven Wrapper (`mvnw`, `mvnw.cmd`).
- JUnit 5 (Jupiter). `exec-maven-plugin` runs `Main`.
- No other dependencies.

```
src/main/java/com/courseseat/
  model/        Course, Learner, EnrollStatus, DropStatus, EnrollResult, DropResult
  repository/   LearnerRepository
  service/      EnrollmentService
  Main.java
src/test/java/com/courseseat/
  TestData.java
  service/      EnrollmentServiceTest, EnrollmentServiceConcurrencyTest
```

Commands: `mvnw.cmd test` (Windows) / `./mvnw test`; demo: `mvnw.cmd -q compile exec:java`.

---

## 13. Trade-offs and limitations

- **In-memory only:** state is lost on restart.
- **Single JVM, single offering:** see section 9 for how this would scale.
- **No `Enrollment` entity:** sets of IDs are enough without a database; a real system
  would store (learnerId, offeringId, status, timestamp) rows.
- **Lock fairness:** FIFO reflects processing order under the lock, not arrival time.
- **Coarse lock:** one lock per offering serializes all its operations; acceptable for one
  course, and the right granularity for per-offering scaling.
- **Waitlist cap is an assumption** (20% drop rate + 1), not derived from data.
- **No notifications:** promotion and skips are reported in `DropResult` only.
- **Post-enrollment eligibility changes** are not acted on.
- Input validation of courses and learners is assumed to be done upstream.
