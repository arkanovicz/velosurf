
---- For Oracle (add an attribute << schema="BOOKSTORE" >> to the database element in velosurf.xml)
-- CREATE USER BOOKSTORE IDENTIFIED GLOBALLY AS '' DEFAULT TABLESPACE USERS;
-- ALTER USER BOOKSTORE QUOTA 1M ON USERS;
-- ALTER SESSION SET CURRENT_SCHEMA = BOOKSTORE;

---- Some databases (like Postgresql) don't like the CONCAT function, but only like the concatenation operator '||' (not recognized by others like mysql...)
---- So you may have to adapt this.

CREATE TABLE publisher (
	publisher_id INTEGER NOT NULL PRIMARY KEY,
	name VARCHAR(128) NOT NULL
);

CREATE TABLE author (
	author_id INTEGER NOT NULL PRIMARY KEY,
	first_name VARCHAR(128) NOT NULL,
	last_name VARCHAR(128) NOT NULL
);

CREATE TABLE book (
	book_id INTEGER NOT NULL PRIMARY KEY,
	title VARCHAR(255) NOT NULL,
	isbn VARCHAR(24) NOT NULL,
	publisher_id INTEGER NOT NULL REFERENCES publisher(publisher_id),
	author_id INTEGER NOT NULL REFERENCES author(author_id)
);

INSERT INTO publisher (publisher_id,name) VALUES (1,'Addison Wesley Professional');

INSERT INTO author (author_id,first_name,last_name) VALUES (1,'Joshua','Bloch');
INSERT INTO author (author_id,first_name,last_name) VALUES (2,'W.','Stevens');

INSERT INTO book (book_id,title,isbn,publisher_id,author_id) VALUES (1,'Effective Java','0-618-12902-2',1,1);
INSERT INTO book (book_id,title,isbn,publisher_id,author_id) VALUES (2,'TCP/IP Illustrated, Volume 1','0-201-63346-9',1,2);
