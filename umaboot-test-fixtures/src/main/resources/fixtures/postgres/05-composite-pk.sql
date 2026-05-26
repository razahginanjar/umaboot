-- 05-composite-pk (Postgres)
-- Composite primary keys force per-table fallback for cursor pagination
-- (cursor pagination only works on single-column PKs in v1.2+).
--
-- Two tables:
--   * enrollments : composite PK (student_id, course_id) + non-FK columns
--                   makes it a domain entity, not a junction.
--   * enrollment_grades : composite PK whose two leading columns FK back to enrollments
--                         (composite FK pointing at composite PK).
-- Re-runnable.

DROP TABLE IF EXISTS enrollment_grades CASCADE;
DROP TABLE IF EXISTS enrollments       CASCADE;

CREATE TABLE enrollments (
    student_id   BIGINT  NOT NULL,
    course_id    BIGINT  NOT NULL,
    enrolled_on  DATE    NOT NULL DEFAULT CURRENT_DATE,
    grade_letter CHAR(1),
    PRIMARY KEY (student_id, course_id)
);

CREATE TABLE enrollment_grades (
    enrollment_student_id BIGINT       NOT NULL,
    enrollment_course_id  BIGINT       NOT NULL,
    semester              VARCHAR(20)  NOT NULL,
    points                NUMERIC(5,2) NOT NULL,
    PRIMARY KEY (enrollment_student_id, enrollment_course_id, semester),
    FOREIGN KEY (enrollment_student_id, enrollment_course_id)
        REFERENCES enrollments(student_id, course_id)
);
