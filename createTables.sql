CREATE TABLE USERS(
  username VARCHAR(30) PRIMARY KEY,
  password text
);

CREATE TABLE userEmail(
  username varChar(30),
  Email varchar(30) PRIMARY KEY
)

CREATE TABLE PROJECT(
  ID INT PRIMARY KEY,
  creator VARCHAR(30),
  name VARCHAR(30),
  createdOn VARCHAR(30) --Day/Month/Year/hour(24hr)/min/sec
);

CREATE TABLE CODE(
  ID INT,
  FileName varchar(30),
  message varchar(50),
  code text,
  version int,
  PRIMARY KEY(ID, FileName)
);

CREATE TABLE Version(
  ID INT,
  FileName varchar(30),
  message varchar(50),
  code text,
  version int,
  PRIMARY KEY(ID, FileName)
);

CREATE TABLE Contributers(
  username VARCHAR(30),
  pid INT FOREIGN KEY PROJECT(ID)
)
