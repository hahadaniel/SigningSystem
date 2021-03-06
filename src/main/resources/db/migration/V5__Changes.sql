CREATE TABLE week_plan (
  id             BIGINT       NOT NULL,
  week_name      TEXT         NOT NULL,
  create_at      TIMESTAMP    NOT NULL,
  PRIMARY KEY (id)
) WITHOUT OIDS;

CREATE TABLE plan_record (
  id             BIGINT   NOT NULL,
  plan_id        BIGINT   NOT NULL,
  student_id     BIGINT   NOT NULL,
  plan           TEXT     NOT NULL,
  achievement    TEXT,
  tutor_feedback TEXT,
  PRIMARY KEY (id),
  FOREIGN KEY (plan_id) REFERENCES week_plan (id)
) WITHOUT OIDS;

