DROP TABLE IF EXISTS Experiment CASCADE;
CREATE TABLE IF NOT EXISTS Experiment (
                                          id SERIAL NOT NULL,
                                          description VARCHAR NOT NULL,
                                          created timestamp with time zone DEFAULT NOW(),
                                          PRIMARY KEY (id)
);

DROP TABLE IF EXISTS ExperimentError CASCADE;
CREATE TABLE IF NOT EXISTS ExperimentError(
                                            id SERIAL,
                                            time timestamp with time zone DEFAULT NOW(),
                                            experiment INTEGER NOT NULL,
                                            message VARCHAR NOT NULL,
                                            stacktrace VARCHAR NOT NULL,
                                            PRIMARY KEY (id),
                                            FOREIGN KEY (experiment)
                                                REFERENCES Experiment(id)
                                                ON DELETE CASCADE
);

DROP TABLE IF EXISTS App CASCADE;
CREATE TABLE IF NOT EXISTS App (
                                   app_id   VARCHAR NOT NULL,
                                   version VARCHAR NOT NULL,
                                   os      VARCHAR NOT NULL,
                                   PRIMARY KEY (app_id, version, os)
);

DROP TABLE IF EXISTS InterfaceAnalysis CASCADE;
CREATE TABLE IF NOT EXISTS InterfaceAnalysis (
                                                 id SERIAL,
                                                 experiment Integer,
                                                 app_id varchar,
                                                 app_version varchar,
                                                 app_os varchar,
                                                 description varchar,
                                                 start_time timestamp with time zone DEFAULT NOW() NOT NULL,
                                                 end_time timestamp with time zone,
                                                 success bool DEFAULT false,
                                                 PRIMARY KEY (id),
                                                 FOREIGN KEY (experiment)
                                                     REFERENCES Experiment(id)
                                                     ON DELETE CASCADE,
                                                 FOREIGN KEY (app_id,app_version,app_os)
                                                    REFERENCES App(app_id,version,os)
                                                    ON DELETE CASCADE
);


DROP TABLE IF EXISTS Interface CASCADE;
CREATE TABLE IF NOT EXISTS Interface (
                                         id SERIAL,
                                         analysis INT NOT NULL,
                                         comment VARCHAR,
                                         screenshot bytea,
                                         PRIMARY KEY(id),
                                         FOREIGN KEY (analysis)
                                             REFERENCES InterfaceAnalysis(id)
                                             ON DELETE CASCADE
);


DROP TABLE IF EXISTS InterfaceElement CASCADE;
CREATE TABLE IF NOT EXISTS InterfaceElement (
                                                id SERIAL,
                                                belongs_to INTEGER NOT NULL,
                                                text VARCHAR,
                                                clickable BOOLEAN,
                                                screenshot BYTEA,
                                                PRIMARY KEY(id),
                                                FOREIGN KEY (belongs_to)
                                                    REFERENCES Interface(id)
                                                    ON DELETE CASCADE
);

DROP TABLE IF EXISTS InterfaceElementInteraction CASCADE;
CREATE TABLE IF NOT EXISTS InterfaceElementInteraction(
                                                          id SERIAL,
                                                          action VARCHAR,
                                                          on_element Int,
                                                          leading_to INT,
                                                          PRIMARY KEY(id),
                                                          FOREIGN KEY (on_element)
                                                              REFERENCES InterfaceElement(id)
                                                              ON DELETE CASCADE,
                                                          FOREIGN KEY (leading_to)
                                                              REFERENCES Interface(id)
                                                              ON DELETE CASCADE
);


DROP TABLE IF EXISTS InterfaceAnalysisError CASCADE;
CREATE TABLE IF NOT EXISTS InterfaceAnalysisError (
                                                      id SERIAL,
                                                      analysis INT NOT NULL,
                                                      interface INT,
                                                      message VARCHAR,
                                                      stacktrace VARCHAR,
                                                      PRIMARY KEY (id),
                                                      FOREIGN KEY (analysis)
                                                          REFERENCES InterfaceAnalysis(id)
                                                          ON DELETE CASCADE,
                                                      FOREIGN KEY (interface)
                                                          REFERENCES Interface(id)
                                                          ON DELETE CASCADE
);

DROP TABLE IF EXISTS AppPreferences CASCADE;
CREATE TABLE IF NOT EXISTS AppPreferences (
                                              id SERIAL,
                                              analysis INTEGER NOT NULL,
                                              interface INTEGER,
                                              appid VARCHAR NOT NULL,
                                              version VARCHAR NOT NULL,
                                              os VARCHAR NOT NULL,
                                              comment VARCHAR NOT NULL,
                                              prefs VARCHAR NOT NULL,
                                              PRIMARY KEY (id),
                                              FOREIGN KEY (interface)
                                                  REFERENCES Interface(id)
                                                  ON DELETE CASCADE,
                                              FOREIGN KEY (analysis)
                                                  REFERENCES InterfaceAnalysis(id)
                                                  ON DELETE CASCADE
);


DROP TABLE IF EXISTS TrafficCollection CASCADE;
CREATE TABLE IF NOT EXISTS TrafficCollection (
                                                 id SERIAL,
                                                 analysis INT NOT NULL,
                                                 interface INT,
                                                 start timestamp with time zone DEFAULT NOW(),
                                                 stop timestamp with time zone,
                                                 comment varchar,
                                                 PRIMARY KEY (id),
                                                 FOREIGN KEY (analysis)
                                                     REFERENCES InterfaceAnalysis(id)
                                                     ON DELETE CASCADE,
                                                 FOREIGN KEY (interface)
                                                     REFERENCES Interface(id)
                                                     ON DELETE CASCADE
);

DROP TABLE IF EXISTS Request CASCADE;
CREATE TABLE IF NOT EXISTS Request (
                                       id Serial,
                                       run Integer,
                                       start_time timestamp with time zone,
                                       method varchar,
                                       host varchar,
                                       path varchar,
                                       content varchar,
                                       content_raw bytea,
                                       port Integer,
                                       scheme varchar,
                                       authority varchar,
                                       http_version varchar,
                                       error varchar,
                                       PRIMARY KEY(id),
                                       FOREIGN KEY (run)
                                           REFERENCES TrafficCollection(id)
                                           ON DELETE CASCADE
);


DROP TABLE IF EXISTS Header CASCADE;
CREATE TABLE IF NOT EXISTS Header (
                                      id SERIAL,
                                      request Integer,
                                      name varchar,
                                      values varchar,
                                      PRIMARY KEY (id),
                                      FOREIGN KEY (request)
                                          REFERENCES Request(id)
                                          ON DELETE CASCADE
);

DROP TABLE IF EXISTS Cookie CASCADE;
CREATE TABLE IF NOT EXISTS Cookie (
                                      id SERIAL,
                                      request Integer,
                                      name varchar,
                                      values varchar,
                                      PRIMARY KEY (id),
                                      FOREIGN KEY (request)
                                          REFERENCES Request(id)
                                          ON DELETE CASCADE
);

DROP TABLE IF EXISTS Trailer CASCADE;
CREATE TABLE IF NOT EXISTS Trailer(
                                      id SERIAL,
                                      request Integer,
                                      name varchar,
                                      values varchar,
                                      PRIMARY KEY (id),
                                      FOREIGN KEY (request)
                                          REFERENCES Request(id)
                                          ON DELETE CASCADE
);

DROP TABLE IF EXISTS Response CASCADE;
CREATE TABLE IF NOT EXISTS Response (
                                       id Serial,
                                       run Integer,
                                       request Integer,
                                       start_time timestamp with time zone,
                                       http_version varchar,
                                       status_code Integer,
                                       reason varchar,
                                       content_raw bytea,
                                       content varchar,
                                       error varchar,
                                       PRIMARY KEY(id),
                                       FOREIGN KEY (run)
                                           REFERENCES TrafficCollection(id)
                                           ON DELETE CASCADE,
                                       FOREIGN KEY (request)
                                            REFERENCES Request(id)
                                            ON DELETE CASCADE
);

DROP TABLE IF EXISTS ResponseHeader CASCADE;
CREATE TABLE IF NOT EXISTS ResponseHeader (
                                      id SERIAL,
                                      response Integer,
                                      name varchar,
                                      values varchar,
                                      PRIMARY KEY (id),
                                      FOREIGN KEY (response)
                                          REFERENCES Response(id)
                                          ON DELETE CASCADE
);

DROP TABLE IF EXISTS ResponseCookie CASCADE;
CREATE TABLE IF NOT EXISTS ResponseCookie (
                                      id SERIAL,
                                      response Integer,
                                      name varchar,
                                      values varchar,
                                      PRIMARY KEY (id),
                                      FOREIGN KEY (response)
                                          REFERENCES Response(id)
                                          ON DELETE CASCADE
);

DROP TABLE IF EXISTS ResponseTrailer CASCADE;
CREATE TABLE IF NOT EXISTS ResponseTrailer(
                                      id SERIAL,
                                      response Integer,
                                      name varchar,
                                      values varchar,
                                      PRIMARY KEY (id),
                                      FOREIGN KEY (response)
                                          REFERENCES Response(id)
                                          ON DELETE CASCADE
);
