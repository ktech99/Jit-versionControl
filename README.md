# Jit-Version Control

A basic version control program written in Java and SQL, running on Microsoft Azure Server.

This is an attempt to implement basic version control with basic functionality from git.

This project was done for learning purposes to apply the knowledge I learnt in the CSE414 class taken at the University of Washington.

This project uses highly modified version of code that is part of an assignment in CSE414, copyrighted by the University of Washington and in no way gives out the solution to any of the assignments in the course.

## Contribution guidelines

- Please make the commit messages useful, or contributions to this project may be rejected.

- If you find a bug, add it to the list of issues.

- Feel free to add features tho this project.

- Follow the examples of how SQL code is used in the Query.java file using prepared statements to prevent SQL injection

- Follow naming convention of the variables and prepare statements

- Make variable names as descriptive as possible

## Building/Running

Create a server on Microsoft Azure and fill in missing fields in dbconn.properties file

compiling the code:

```
javac -cp "lib/*" *.java
```

running the code:

```
java -cp "lib/*:." FlightService
```


