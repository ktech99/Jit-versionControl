CREATE TABLE USER(
  username VARCHAR(30) PRIMARY KEY,
  password VARCHAR(50)
);

CREATE TABLE project(
  ID INT PRIMARY KEY,
  creator VARCHAR(30),
  name VARCHAR(30),
  createdOn VARCHAR(30) --Day/Month/Year/hour(24hr)/min/sec
);
