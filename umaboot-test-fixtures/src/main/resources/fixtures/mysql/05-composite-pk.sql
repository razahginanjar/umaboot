-- 05-composite-pk (MySQL)
-- Mirror of postgres/05-composite-pk.sql. Composite-PK FK reference is
-- declared as an explicit table-level FOREIGN KEY clause in MySQL.
-- Re-runnable.

DROP TABLE IF EXISTS enrollment_grades;
DROP TABLE IF EXISTS enrollments;

CREATE TABLE enrollments (
    student_id   BIGINT  NOT NULL,
    course_id    BIGINT  NOT NULL,
    enrolled_on  DATE    NOT NULL DEFAULT (CURRENT_DATE),
    grade_letter CHAR(1),
    PRIMARY KEY (student_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE enrollment_grades (
    enrollment_student_id BIGINT       NOT NULL,
    enrollment_course_id  BIGINT       NOT NULL,
    semester              VARCHAR(20)  NOT NULL,
    points                DECIMAL(5,2) NOT NULL,
    PRIMARY KEY (enrollment_student_id, enrollment_course_id, semester),
    FOREIGN KEY (enrollment_student_id, enrollment_course_id)
        REFERENCES enrollments(student_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
