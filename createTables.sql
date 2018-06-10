-- add all your SQL setup statements here.
CREATE TABLE Users(
  username varchar(20) PRIMARY KEY,
  password varchar(20),
  balance int
);
Create index userIndex on Users(username);

CREATE TABLE Reservations(
  rid int,
  username varchar(20),
  day int,
  fid int,
  paid int,
  price int,
  PRIMARY KEY(rid, fid)
);

Create index Res on Reservations(rid, fid);

CREATE TABLE FlightCapacity(
  fid int PRIMARY KEY,
  capacity int
);

INSERT into FlightCapacity
SELECT fid, capacity
FROM Flights;

Create index FlightCapacityIndex on FlightCapacity(fid);

--booking

-- You can assume that the following base table has been created with data loaded for you when we test your submission
-- (you still need to create and populate it in your instance however),
-- although you are free to insert extra ALTER COLUMN ... statements to change the column
-- names / types if you like.

Create table FLIGHTS (fid int,
  month_id int,        -- 1-12
  day_of_month int,    -- 1-31
  day_of_week_id int,  -- 1-7, 1 = Monday, 2 = Tuesday, etc
  carrier_id varchar(7),
  flight_num int,
  origin_city varchar(34),
  origin_state varchar(47),
  dest_city varchar(34),
  dest_state varchar(46),
  departure_delay int, -- in mins
  taxi_out int,        -- in mins
  arrival_delay int,   -- in mins
  canceled int,        -- 1 means canceled
  actual_time int,     -- in mins
  distance int,        -- in miles
  capacity int,
  price int            -- in $
);

Create index FlightIndex on FLIGHTS(origin_city, dest_city);
