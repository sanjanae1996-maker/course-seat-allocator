# AI Usage Record

This file records how AI was used while building this feature: the prompts,
what the AI suggested, what I accepted, edited or rejected (and why), which
files or decisions it informed, and how I verified the result.

## Tools

| Tool | Model | Used for |
|---|---|---|
| Claude (chat) | Claude Opus 5.5 | Understanding the brief, design discussion, reviewing generated code, drafting DESIGN.md / CLAUDE.md, explaining concepts |
| Claude Code (desktop app, local folder) | Claude Opus 5.5 | Implementation, step by step, following DESIGN.md |

**How Claude Code was controlled:** `CLAUDE.md` required it to follow
`DESIGN.md`, build only the current step, run `mvnw.cmd test`, show the diff
and stop. It was not allowed to commit or push; I reviewed every diff and
made every commit myself.

No credentials, secrets or personal data were included in any prompt.

---

## 1. Understanding the brief (Claude chat)

- **Prompt (summary):** "What is the problem statement, what is expected,
  and what needs to be delivered?" (assessment PDF attached)
- **AI response (summary):** Explained the scope (enroll, drop, FIFO
  waitlist for one offering), the deliverables, and that the key open
  decision is skip vs retain for an ineligible learner at the front of the
  queue.
- **Decision:** Accepted as a starting point.
- **Informed:** overall plan; list of questions for the recruiter.
- **Follow-up I raised:** the brief refers to a starter project and a rubric
  file, but the link was not accessible. Decision: proceed with the
  in-memory fallback the brief allows, and inform the recruiter.

---

## 2. Design decisions (Claude chat)

### 2.1 Waitlist data structure
- **My question:** Could `LinkedList` or `ArrayDeque` be used for the
  waitlist instead?
- **AI response (summary):**
  - `ArrayDeque` / `LinkedList`: give FIFO order, but `contains()` and
    `remove(x)` are O(n), and they accept duplicates unless checked
    manually.
  - `LinkedHashSet`: FIFO iteration, O(1) `contains` / `remove(x)`, and
    duplicates rejected by design.
- **Decision:** **Rejected** `ArrayDeque` and `LinkedList`. **Accepted**
  `LinkedHashSet`, mainly because the same learner must never appear twice
  and a learner can leave from the middle of the queue.
- **Verified by:** understanding the internals (backed by `LinkedHashMap`,
  head pointer gives O(1) removal of the first element); tests
  `keepsFifoOrder`, `rejectsDuplicateWaitlistRequest`,
  `waitlistedLearnerCanLeave`.
- **Related decision:** `enrolled` also uses `LinkedHashSet`, so test and
  demo output order is stable.

### 2.2 Waitlist size (cap): my change
- **AI's initial suggestion:** an unbounded waitlist (documented as an
  assumption).
- **My challenge:** an unbounded queue can keep growing while capacity is
  small.
- **AI response (summary):** the queue cannot grow with request volume
  (each learner can appear only once), but a cap is still reasonable from a
  product point of view; `ArrayDeque`'s constructor argument is only an
  initial size, not a limit, so the cap must be a business rule checked in
  the service.
- **Decision:** **Edited (my change).** Added a per-course
  `maxWaitlistSize` and a new `WAITLIST_FULL` result. I chose the sizing
  rule: capacity 5, assumed 20% drop rate, +1 buffer → cap 2.
- **Informed:** `Course.maxWaitlistSize`, `EnrollStatus.WAITLIST_FULL`,
  DESIGN.md section 7.
- **Verified by:** `returnsWaitlistFullAtCap`; concurrency test expects
  exactly 2 `WAITLISTED` and 3 `WAITLIST_FULL`.

### 2.3 Ineligible learner at the front of the queue: skip vs retain
- **AI suggestion:** skip (remove from the waitlist, promote the next
  eligible learner), and re-check eligibility at promotion time because a
  learner's record can change while waiting.
- **Decision:** **Accepted.** I confirmed that the re-check is needed,
  because eligibility is already checked when joining the waitlist.
- **Informed:** `promoteNext()`, `DropResult.skippedIds`, DESIGN.md
  section 6.
- **Verified by:** `skipsIneligibleLearnerAtFront`,
  `seatStaysOpenWhenNoneEligible`.

### 2.4 Results instead of exceptions
- **AI suggestion:** return business outcomes as enum-based results; use
  exceptions only for programming errors.
- **Decision:** **Accepted.**
- **Informed:** `EnrollStatus`, `DropStatus`, `EnrollResult`, `DropResult`.

### 2.5 What `DropResult` contains
- **AI suggestion:** include `promotedId` (as `Optional<String>`) and
  `skippedIds`.
- **My challenge:** should the dropping learner see who was promoted? Could
  this just be logged?
- **Decision:** **Accepted with a clarification I added to the design:**
  `DropResult` is an internal service result; a public API response would
  return only the caller's status, and promotion and skip details would feed
  notifications and audit.
- **Informed:** DESIGN.md section 3.

### 2.6 `LearnerRepository` with `ConcurrentHashMap`, returning `Optional`
- **My questions:** why `ConcurrentHashMap`? Why `Optional` instead of
  returning `null` or throwing?
- **Decision:** **Accepted** after review: the repository can be written
  outside the service lock (e.g. revoking a prerequisite), so it must be
  thread-safe on its own; an unknown learner is an expected outcome
  (`UNKNOWN_LEARNER`), so it must not throw, and `Optional` makes "not
  found" explicit.

---

## 3. Implementation with Claude Code

Each step used a prompt that referenced `DESIGN.md`; Claude Code ran the
tests, showed the diff and stopped. I reviewed, ran `mvnw.cmd clean test`
myself, and committed.

### 3.1 Phase 1, project setup
- **Prompt:** Appendix A.1. Maven project, Java 17 release target (local
  JDK is 22), JUnit 5, exec plugin, Maven Wrapper, package folders,
  `.gitignore`.
- **Decision:** Accepted.
- **Verified by:** `mvnw.cmd test` → BUILD SUCCESS; demo placeholder ran.

### 3.2 Phase 1, domain model and repository
- **Prompt:** Appendix A.2. Records, enums, result types,
  `LearnerRepository`.
- **AI output included:** validation in the `Course` / `Learner`
  compact constructors, `Objects.requireNonNull` in the service constructor.
- **Decision:** **Rejected** both later (see 4.1 and 4.2). **Accepted** the
  defensive `Set.copyOf` copies.
- **Verified by:** tests that the copied sets cannot be modified.

### 3.3 Phase 2, enroll
- **Prompt:** Appendix A.3. Check order from DESIGN.md section 5;
  `@Nested` Enroll tests; every test asserts both result and state.
- **Decision:** Accepted, then edited the tests (see 4.3).
- **Verified by:** Enroll test group passes.

### 3.4 Phase 2, bounded FIFO waitlist
- **Prompt:** Appendix A.5. `WAITLISTED`, `ALREADY_WAITLISTED` (keeps its
  place), `WAITLIST_FULL`.
- **My improvement to the prompt:** extended the shared `setUp()` repository
  (eligible L001, L002, L003, L005, L006; ineligible L004) so tests use
  existing IDs instead of adding learners inside tests, and spelled out each
  scenario explicitly.
- **Decision:** Accepted.
- **Verified by:** Waitlist test group passes.

### 3.5 Phase 2, drop and promotion
- **Prompt:** Appendix A.6. Drop rules from DESIGN.md section 6;
  iterator-based `promoteNext()`; fresh repository lookup; skip policy.
- **My review:** traced where the dropped learner is removed from
  `enrolled`. It happens inside `if (!enrolled.remove(learnerId))`, which
  both removes and reports whether the learner was enrolled. Confirmed
  `iterator.remove()` is used (no `ConcurrentModificationException`), the
  lookup is fresh, and the loop stops after one promotion.
- **Gap I raised:** drop when the waitlist is empty. Confirmed it is already
  covered by the first drop in `repeatedDropReturnsNotEnrolled`, so no
  separate test was added.
- **Decision:** Accepted.
- **Verified by:** Drop test group passes.

### 3.6 Phase 3, thread safety and concurrency tests
- **Prompt:** Appendix A.9. `synchronized` on the four public methods;
  `EnrollmentServiceConcurrencyTest` with `ExecutorService`, latch start
  gate, `Future.get` timeouts, `@RepeatedTest(100)`, executor shutdown in
  `finally`.
- **Discussion:** I questioned the second (`ready`) latch. After working
  through the alternatives (a single `start` latch, `CyclicBarrier`), I
  **kept both latches**: `ready` makes the main thread wait until every
  worker is at the starting line, and `start` releases them together, so no
  thread joins the race late.
- **Edited (my naming):** test names changed so the scenario is clear:
  `simultaneousEnrollmentsDoNotOverbookCourse`,
  `sameLearnerEnrollingTwiceAtOnceIsEnrolledOnce`,
  `simultaneousDropsGiveEachSeatToDifferentLearner`. I rejected adding
  `@DisplayName`.
- **Verification (see section 5):** tests fail without `synchronized`.

### 3.7 Phase 3, demo
- **Prompt:** Appendix A.10. Scenario from DESIGN.md section 11, readable
  output.
- **Decision:** Accepted.
- **Verified by:** output matched the expected table, including the drop
  step (promoted L008, skipped [L007]).

---

## 4. AI output I edited or rejected

### 4.1 Validation in `Course` and `Learner` records: rejected
- **AI output:** compact constructors threw `IllegalArgumentException` for
  blank IDs, capacity < 1, negative waitlist size and null sets.
- **My reasoning:** creating and validating courses and learners belongs to
  the modules that own them (e.g. a course service validates before saving).
  This module is only about enrollment and assumes valid data.
- **Change:** removed the checks and their tests; kept `Set.copyOf` for
  immutability. Documented the assumption in DESIGN.md.
- **Prompt:** Appendix A.7.

### 4.2 `Objects.requireNonNull` in the service constructor: rejected
- **My reasoning:** the service is always wired by its callers (tests,
  `Main`, or dependency injection in a real app), consistent with 4.1.
- **Change:** removed.
- **Prompt:** Appendix A.8.

### 4.3 Overlapping enroll tests: edited
- Merged `rejectsNullOrBlankId` and `invalidRequestLeavesStateUnchanged`
  into one stronger test (`rejectsNullOrBlankId`) that checks null, empty
  and whitespace IDs against a course that already has an enrolled learner.
- Removed `learnerWithNoCompletedCoursesGetsPrereqNotMet` as covered by
  `rejectsWhenPrereqNotMet`.
- **Prompt:** Appendix A.4.

### 4.4 Kept after questioning
- `@BeforeEach` rebuilding the service for every test: kept, because tests
  change state and JUnit does not guarantee test order.
- State assertions after rejected requests: kept as regression guards.
- `@RepeatedTest(100)`: kept, because races are timing-dependent.

---

## 5. Verification evidence

| Check | Result |
|---|---|
| Full test suite (`mvnw.cmd clean test`) | `Tests run: 321, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS (21 functional tests + 3 concurrency tests × 100 repetitions) |
| Concurrency tests with `synchronized` | 300 runs (3 tests × 100), 0 failures |
| Concurrency tests **without** `synchronized` on `enroll` / `drop` | 300 runs: **62 failures, 20 errors** |
| Failure example | `simultaneousDropsGiveEachSeatToDifferentLearner:97 expected: <[L104, L103]> but was: <[L103]>`: two seats were freed but only one learner was promoted, so a seat was lost |
| Error example | `simultaneousDropsGiveEachSeatToDifferentLearner:85 -> runAtSameMoment:126 » ExecutionException: java.util.ConcurrentModificationException`: one thread iterated the waitlist during promotion while another thread modified it |
| `synchronized` restored | all tests pass again |
| Demo (`mvnw.cmd -q compile exec:java`) | output matches DESIGN.md section 11 |

---

## 6. Summary

| Area | Accepted | Edited | Rejected |
|---|---|---|---|
| Waitlist structure | `LinkedHashSet` | | `ArrayDeque`, `LinkedList` |
| Waitlist size | | Unbounded → capped (my sizing rule) | |
| Skip vs retain | Skip + re-check at promotion | | |
| Concurrency | `synchronized` on all public methods | | |
| `DropResult` | `Optional` promotedId, skippedIds | Clarified as internal-only | |
| Record validation | `Set.copyOf` copies | | Constructor validation |
| Service constructor | | | `requireNonNull` checks |
| Enroll tests | | Merged two tests | Removed one redundant test |
| Concurrency tests | Both latches, `@RepeatedTest(100)` | Renamed tests | `@DisplayName` |

---

## Appendix A: Claude Code prompts (as sent)

> **[verify each prompt against the Claude Code session history and
> correct any wording that differs, then delete this note]**

### A.1 Project setup
```text
Hi! Before anything else, please read DESIGN.md and CLAUDE.md in this
repo. They describe what we're building and how I'd like us to work.

Let's start with Phase 1, step 1: just the project setup. Please don't
write any application logic yet.

Here's what I need:

- A pom.xml for groupId com.courseseat, artifactId course-seat-allocator,
  version 1.0.0. I'm on JDK 22 locally, but please target Java 17
  (maven.compiler.release = 17) so reviewers on 17 can build it too.
  Use UTF-8 encoding.
- JUnit 5 for tests (via the junit-bom), plus a recent surefire plugin so
  the tests actually run.
- The exec-maven-plugin pointing at com.courseseat.Main, so the demo can
  run with one command.
- The Maven Wrapper (mvnw, mvnw.cmd and .mvn/wrapper), so nobody needs
  Maven installed.
- The package folders from section 12 of DESIGN.md, and a tiny
  placeholder Main.java that just prints "course-seat-allocator".
- A .gitignore that covers target/ and IDE folders (.idea/, *.iml, .vscode/).

Once that's in place, please run mvnw.cmd test (I expect BUILD SUCCESS
with 0 tests) and mvnw.cmd -q compile exec:java (I expect the
placeholder line). Show me both outputs, git status, and the pom.xml
diff, then stop. I'll review and commit myself.
```

### A.2 Domain model and repository
```text
Thanks, that looks good. Now Phase 1, step 2: the domain model and the
learner repository, exactly as described in section 2 of DESIGN.md.
Please hold off on any EnrollmentService logic for now.

In com.courseseat.model, I need:

- A Course record with id, capacity, maxWaitlistSize and
  requiredCourseIds. Please validate in the compact constructor: the id
  can't be blank, capacity must be at least 1, maxWaitlistSize can't be
  negative, and requiredCourseIds can't be null. Throw
  IllegalArgumentException for any of those. Copy the set with
  Set.copyOf so nobody can change it from outside.
- A Learner record with id, name and completedCourseIds. Same idea: the id
  can't be blank, the set can't be null, and copy it with Set.copyOf.
- An EnrollStatus enum: ENROLLED, WAITLISTED, ALREADY_ENROLLED,
  ALREADY_WAITLISTED, WAITLIST_FULL, PREREQ_NOT_MET, UNKNOWN_LEARNER,
  INVALID_REQUEST.
- A DropStatus enum: DROPPED, REMOVED_FROM_WAITLIST, NOT_ENROLLED,
  UNKNOWN_LEARNER, INVALID_REQUEST.
- An EnrollResult record (status, learnerId).
- A DropResult record (status, droppedId, Optional<String> promotedId,
  List<String> skippedIds). Please copy skippedIds with List.copyOf.

In com.courseseat.repository:

- A LearnerRepository backed by a ConcurrentHashMap<String, Learner>,
  with find(id) returning Optional<Learner> and save(learner) that inserts
  or replaces. find(null) should just return Optional.empty().
  The replace behaviour matters: that's how our tests will revoke a
  prerequisite while someone is waiting.

For tests:

- model/CourseTest: a valid course builds fine, each invalid input
  throws, and the required-courses set can't be modified from outside.
- repository/LearnerRepositoryTest: save then find works, an unknown id
  gives empty, and saving again replaces the learner.

Please keep everything small and easy to read. No Lombok, Spring or
Mockito. When you're done, run mvnw.cmd test, show me the results and the
diff, and stop so I can review.
```

### A.3 Enroll
```text
Great, Phase 1 is committed. Let's start Phase 2, step 3: the enroll
method. Please re-read sections 3, 4 and 5 of DESIGN.md before starting.

In com.courseseat.service, create EnrollmentService:

- Constructor takes a Course and a LearnerRepository. Reject nulls with
  Objects.requireNonNull.
- Two private fields: enrolled and waitlist, both LinkedHashSet<String>
  holding learner IDs.
- enroll(String learnerId) following the check order in section 5 of
  DESIGN.md. For this step, only handle checks 1 to 6: invalid ID,
  unknown learner, already enrolled, prerequisite not met, and enrolled
  when a seat is free. If the course is full, just return WAITLISTED for
  now without adding to the waitlist; the next step does the waitlist
  properly. Please leave a short TODO comment there.
- getEnrolled() and getWaitlist() returning List.copyOf(...).
- A private isEligible(Learner) helper:
  completedCourseIds containsAll requiredCourseIds.
- No synchronized yet; that comes in Phase 3.

Tests:
- Create src/test/java/com/courseseat/TestData.java with small helpers:
  course(capacity, maxWaitlist) for a course that requires CS101,
  eligible(id) and ineligible(id) for learners, and a way to build a
  repository with some learners in it.
- Create service/EnrollmentServiceTest with a @BeforeEach that builds a
  fresh service, and a @Nested class Enroll with these tests:
  enrollsWhenSeatAvailable, rejectsWhenPrereqNotMet,
  rejectsDuplicateEnrollment, rejectsUnknownLearner,
  rejectsNullOrBlankId, invalidRequestLeavesStateUnchanged, and
  learnerWithNoCompletedCoursesGetsPrereqNotMet.
- Every test should check both the returned result and the state
  (getEnrolled / getWaitlist).

Run mvnw.cmd test, show me the results and the diff, then stop.
```

### A.4 Enroll test clean-up
```text
Small cleanup in EnrollmentServiceTest, inside the Enroll group.

1. Merge rejectsNullOrBlankId and invalidRequestLeavesStateUnchanged into
   one test, keeping the name rejectsNullOrBlankId:
   - Start by enrolling L001, so the course isn't empty.
   - Check that enroll(null), enroll("") and enroll("   ") each return
     INVALID_REQUEST.
   - Check that getEnrolled() is still [L001] and getWaitlist() is still
     empty.
   - Delete invalidRequestLeavesStateUnchanged.

2. Delete learnerWithNoCompletedCoursesGetsPrereqNotMet. It overlaps with
   rejectsWhenPrereqNotMet.

3. Update DESIGN.md section 10: remove invalidRequestLeavesStateUnchanged
   from the Enroll list, and in the acceptance-criteria table point
   criterion 5 at rejectsNullOrBlankId instead.

Run mvnw.cmd test, show the results and the diff, then stop.
```

### A.5 Bounded FIFO waitlist
```text
Next up, Phase 2 step 4: the bounded FIFO waitlist. Please follow
checks 4, 7 and 8 in section 5 of DESIGN.md, and section 7 for the cap.

1. EnrollmentService.enroll

- Replace the TODO from the last step. When the course is full:
  if the waitlist still has room (size < maxWaitlistSize), add the
  learner to the back and return WAITLISTED. Otherwise return
  WAITLIST_FULL and change nothing.
- Add the ALREADY_WAITLISTED check (check 4) right after
  ALREADY_ENROLLED and before the prerequisite check. A learner who is
  already waiting keeps their place and must not move to the back.
- Keep the prerequisite check before the capacity checks, so an
  ineligible learner always gets PREREQ_NOT_MET, even when the course and
  the waitlist are both full.

2. Shared test setup

In EnrollmentServiceTest.setUp(), extend the repository so tests can use
existing IDs instead of saving new learners inside tests:
- eligible: L001, L002, L003, L005, L006
- ineligible: L004 (keep as it is)
Keep the default service as course(2, 1). Don't change any existing
Enroll tests.

3. Tests: add a @Nested class Waitlist with these scenarios

- waitlistsWhenCourseFull: enroll L001, L002 (course full), then L003
  → WAITLISTED. Enrolled is [L001, L002], waitlist is [L003].

- returnsWaitlistFullAtCap: enroll L001, L002, then L003 (waitlisted),
  then L005 → WAITLIST_FULL. Enrolled and waitlist are unchanged:
  [L001, L002] and [L003].

- ineligibleLearnerNotWaitlisted: enroll L001, L002, then L004
  → PREREQ_NOT_MET. Waitlist stays empty.

- keepsFifoOrder: create a separate service in the test with
  course(1, 3), using the same repository. Enroll L001, then L002, L003,
  L005 → all WAITLISTED. Waitlist is exactly [L002, L003, L005].

- rejectsDuplicateWaitlistRequest: create a separate service with
  course(1, 3). Enroll L001, then L002 and L003 (both waitlisted).
  Enroll L002 again → ALREADY_WAITLISTED. Waitlist is still
  [L002, L003], so L002 kept its place at the front.

Every test checks both the result and the state (getEnrolled and
getWaitlist).

Run mvnw.cmd test, show me the results and the diff, then stop.
```

### A.6 Drop with skip-policy promotion
```text
Now the most important step, Phase 2 step 5: drop with promotion.
Please re-read section 6 of DESIGN.md carefully first, especially the
skip policy.

1. EnrollmentService.drop(String learnerId)

Follow the table in section 6, in this order:
- null or blank ID → INVALID_REQUEST
- learner not in the repository → UNKNOWN_LEARNER
- learner is on the waitlist → remove them, return REMOVED_FROM_WAITLIST.
  No promotion, because no seat opened.
- learner not enrolled (including a second drop) → NOT_ENROLLED
- learner enrolled → remove them, then call promoteNext()

For every result other than DROPPED, promotedId is Optional.empty() and
skippedIds is an empty list.

2. private promoteNext(String droppedId)

- Walk the waitlist from the front with an Iterator, and remove entries
  with iterator.remove(). Please don't call waitlist.remove(id) inside a
  for-each loop; that throws ConcurrentModificationException.
- For each ID: remove it from the waitlist, look up the learner's CURRENT
  record in the repository (not a cached copy), and check eligibility.
  - Eligible → add to enrolled and stop.
  - Not eligible, or no longer in the repository → add the ID to a
    skipped list and keep going.
- If the waitlist runs out, the seat stays open.
- Return DropResult(DROPPED, droppedId, Optional promotedId, skippedIds).
- Add a short comment explaining that promotion happens inside drop so the
  freed seat can't be taken by a new request first, and that ineligible
  learners are skipped per the policy in DESIGN.md.

3. Test helper

In TestData, add a small helper that returns a learner with the same ID
but no completed courses (for example revoked(id)). Tests use it with
learners.save(...) to revoke CS101 while someone is waiting.

4. Tests: add a @Nested class Drop with these scenarios

Tests that need two or more people waiting build their own service with
course(1, 3) and the same repository.

- dropPromotesEarliestEligible (course(1, 3)): enroll L001; L002 and L003
  waitlisted. Drop L001 → DROPPED, promotedId L002, skippedIds empty.
  Enrolled [L002], waitlist [L003].

- skipsIneligibleLearnerAtFront (course(1, 3)): enroll L001; L002 and
  L003 waitlisted. Save revoked(L002). Drop L001 → DROPPED, promotedId
  L003, skippedIds [L002]. Enrolled [L003], waitlist empty.

- seatStaysOpenWhenNoneEligible (course(1, 3)): enroll L001; L002
  waitlisted. Save revoked(L002). Drop L001 → DROPPED, promotedId empty,
  skippedIds [L002]. Enrolled and waitlist both empty. Then enroll L003 →
  ENROLLED straight away, proving the seat was left open.

- waitlistedLearnerCanLeave (default course): enroll L001, L002; L003
  waitlisted. Drop L003 → REMOVED_FROM_WAITLIST, promotedId empty,
  skippedIds empty. Enrolled still [L001, L002], waitlist empty.

- repeatedDropReturnsNotEnrolled (default course): enroll L001, L002.
  Drop L001 → DROPPED. Drop L001 again → NOT_ENROLLED, and state is
  unchanged by the second call. Also drop L005, who was never enrolled
  → NOT_ENROLLED.

- unknownLearnerDropIsRejected (default course): enroll L001. Drop L999
  → UNKNOWN_LEARNER. Drop null, "" and "   " → INVALID_REQUEST.
  Enrolled is still [L001].

Every test checks the result (status, promotedId, skippedIds) and the
state (getEnrolled and getWaitlist).

Run mvnw.cmd test, show me the results and the diff, then stop.
```

### A.7 Move course and learner validation upstream
```text
Design change: course and learner validation is owned by the modules that
create them (a CourseService and LearnerService that validate before
saving), which are out of scope here. This module receives already-valid
data, so the records should be plain data carriers.

Please:

1. In Course and Learner, remove the validation checks (blank id,
   capacity < 1, negative maxWaitlistSize, null sets) from the compact
   constructors. Keep the defensive copies (Set.copyOf) so the sets stay
   unmodifiable.

2. In CourseTest (and any Learner tests), delete the tests that expect
   exceptions for invalid input. Keep the test that checks the
   required-courses set can't be modified from outside.

3. Keep Objects.requireNonNull in the EnrollmentService constructor.
   That's wiring, not data validation.

4. Update DESIGN.md:
   - Section 2: replace the "Validation of Course" paragraph with:
     "Courses and learners are created and validated by other modules
     (e.g. a CourseService and LearnerService that validate input before
     saving). They are out of scope here; this module assumes it receives
     valid data. Records copy their sets with Set.copyOf so they stay
     immutable."
   - Section 10: remove any construction-validation tests listed.
   - Section 13: add "Input validation of courses and learners is
     assumed to be done upstream."

Run mvnw.cmd test, show the results and the diff, then stop.
```

### A.8 Remove constructor null checks
```text
Small change in EnrollmentService: please remove the
Objects.requireNonNull checks from the constructor and just assign the
fields directly:

    this.course = course;
    this.learners = learners;

Reason: the service is always constructed with real dependencies (by the
tests, Main, and in a real app by dependency injection), so this module
doesn't need to guard its own wiring.

Also:
- Remove the java.util.Objects import if it's no longer used.
- If any test checks that a null course or repository throws, delete it.
- Don't change anything else.

Run mvnw.cmd test, show the results and the diff, then stop.
```

### A.9 Thread safety and concurrency tests
```text
Phase 2 is done, so let's start Phase 3, step 6: thread safety. Please
re-read section 9 of DESIGN.md first.

1. EnrollmentService

- Make enroll, drop, getEnrolled and getWaitlist synchronized. The
  getters need it too: they read the same sets that enroll and drop
  change.
- Add a short class-level comment explaining that one instance
  represents one course offering, all request threads share it, and
  synchronized locks this instance, which owns enrolled and waitlist.
  That makes each whole check-then-act sequence atomic, including the
  promotion inside drop.
- No other logic changes.

2. Test helper

In TestData, add repositoryWithEligible(int count), which builds a new
repository with eligible learners L101, L102, ... using a loop (so
repositoryWithEligible(10) gives L101 to L110).

3. New test class: service/EnrollmentServiceConcurrencyTest

General rules for all three tests:
- Build the repository and service inside each test method. No static
  fields, so every repetition starts fresh.
- Use an ExecutorService and a CountDownLatch as a start gate, so all
  threads call the service at the same moment.
- Collect results through Futures, and use a timeout on Future.get so a
  deadlock fails the test instead of hanging it.
- Shut the executor down in a finally block.
- Each test is @RepeatedTest(100).

Tests:

- simultaneousEnrollmentsDoNotOverbookCourse
  course(5, 2), repositoryWithEligible(10). All 10 learners enroll at the
  same moment. Expect exactly 5 ENROLLED, 2 WAITLISTED and
  3 WAITLIST_FULL. getEnrolled has 5 distinct IDs, getWaitlist has 2,
  and nobody appears in both.

- sameLearnerEnrollingTwiceAtOnceIsEnrolledOnce
  course(5, 2). The same learner, L101, sends 10 enroll requests at the
  same moment. Expect exactly 1 ENROLLED and 9 ALREADY_ENROLLED, and
  getEnrolled is [L101].

- simultaneousDropsGiveEachSeatToDifferentLearner
  course(2, 2), repositoryWithEligible(4). First, one at a time: L101 and
  L102 enroll, then L103 and L104 join the waitlist. Then L101 and L102
  drop at the same moment. Expect both results DROPPED, the two promoted
  IDs are L103 and L104 (one each, nobody promoted twice), getEnrolled
  contains exactly L103 and L104, and the waitlist is empty.

4. DESIGN.md section 10

Replace the concurrency test list with these three names and add one
line describing each. In the acceptance-criteria table, map criterion 6
to all three, and also map sameLearnerEnrollingTwiceAtOnceIsEnrolledOnce
to criterion 2 (no duplicate records).

Run mvnw.cmd test, show me the results and the diff, then stop.
```

### A.10 Runnable demo
```text
Phase 3, step 7: the runnable demo. Please replace the placeholder Main
with the scenario in section 11 of DESIGN.md.

- Course CS201, capacity 5, waitlist cap 2, requires CS101.
- Learners L001 to L009 with the names Asha, Bala, Chitra, Dev, Esha,
  Farah, Gopi, Hari, Isha. L004 (Dev) has only MA101; everyone else has
  CS101.
- Run the steps from section 11 in order, including revoking CS101 for
  L007 (by saving an updated learner to the repository) before L002
  drops.
- After each request, print one readable line with the request, the
  learner's name and the result, then the current enrolled list and
  waitlist on the next line, for example:

    enroll L004 (Dev)     -> PREREQ_NOT_MET
      enrolled: [L001, L002, L003, L005, L006]   waitlist: []

  For drops, also print who was promoted and who was skipped.
- Add a short header line before each group of steps, for example
  "--- Course full: waitlist and cap ---", so the output reads like a
  story.
- Keep Main simple and readable: plain System.out, no logging framework,
  no extra classes.

Run mvnw.cmd -q compile exec:java and show me the full output, then
stop.
```
