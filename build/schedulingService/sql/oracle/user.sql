CREATE USER SCHEDULING
  IDENTIFIED BY oracle
  DEFAULT TABLESPACE SCHEDULING
  TEMPORARY TABLESPACE TEMP
  PROFILE DEFAULT
  ACCOUNT UNLOCK;
  -- 3 Roles for SCHEDULING 
  GRANT XDBADMIN TO SCHEDULING;
  GRANT MGMT_USER TO SCHEDULING;
  GRANT CONNECT TO SCHEDULING;
  ALTER USER SCHEDULING DEFAULT ROLE NONE;
  -- 17 System Privileges for SCHEDULING 
  GRANT CREATE JOB TO SCHEDULING;
  GRANT CREATE TRIGGER TO SCHEDULING;
  GRANT CREATE SEQUENCE TO SCHEDULING;
  GRANT CREATE TABLE TO SCHEDULING;
  GRANT DEBUG CONNECT SESSION TO SCHEDULING;
  GRANT EXECUTE ANY PROCEDURE TO SCHEDULING;
  GRANT CREATE VIEW TO SCHEDULING;
  GRANT CREATE SESSION TO SCHEDULING;
  GRANT CREATE SYNONYM TO SCHEDULING;
  GRANT UNLIMITED TABLESPACE TO SCHEDULING;
  GRANT ALTER SESSION TO SCHEDULING;
  GRANT DEBUG ANY PROCEDURE TO SCHEDULING;
  GRANT SELECT ANY DICTIONARY TO SCHEDULING;
  GRANT CREATE TYPE TO SCHEDULING;
  GRANT CREATE ANY DIRECTORY TO SCHEDULING;
  GRANT EXECUTE ANY TYPE TO SCHEDULING;
  GRANT CREATE PROCEDURE TO SCHEDULING;
  -- 1 Tablespace Quota for SCHEDULING 
  ALTER USER SCHEDULING QUOTA UNLIMITED ON SCHEDULING;
