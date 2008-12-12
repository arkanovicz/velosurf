DROP TABLE IF EXISTS empty;
DROP TABLE IF EXISTS validation;
DROP TABLE IF EXISTS localized;
DROP TABLE IF EXISTS book;
DROP TABLE IF EXISTS author;
DROP TABLE IF EXISTS publisher;
DROP TABLE IF EXISTS user;

CREATE TABLE user (
	id INTEGER IDENTITY NOT NULL PRIMARY KEY,
	login VARCHAR(50) NOT NULL,
	password VARCHAR(50) NOT NULL
);

CREATE TABLE publisher (
	publisher_id INTEGER NOT NULL PRIMARY KEY,
	name VARCHAR(128) NOT NULL
);

CREATE TABLE author (
	author_id INTEGER IDENTITY,
	first_name VARCHAR(128) NOT NULL,
	last_name VARCHAR(128) NOT NULL
);

CREATE TABLE book (
	book_id INTEGER NOT NULL PRIMARY KEY,
	title VARCHAR(255) NOT NULL,
	isbn VARCHAR(24) NOT NULL,
	publisher_id INTEGER NOT NULL,
	author_id INTEGER NOT NULL,
    FOREIGN KEY(publisher_id) REFERENCES publisher(publisher_id),
    FOREIGN KEY(author_id) REFERENCES author(author_id)
);

CREATE TABLE localized (
  id VARCHAR(100) NOT NULL,
  locale VARCHAR(50) NOT NULL,
  string VARCHAR(255) default NULL,
  PRIMARY KEY  (id,locale)
);


CREATE TABLE validation (
  id INTEGER IDENTITY,
  string VARCHAR(255),
  string2 VARCHAR(255),
  number INTEGER,
  oneof VARCHAR(255),
  mydate DATE,
  email VARCHAR(100),
  email2 VARCHAR(100),
  book_id INTEGER,
  PRIMARY KEY(id),
  FOREIGN KEY(book_id) REFERENCES book(book_id)
);

CREATE TABLE empty (
  id INTEGER IDENTITY,
  data VARCHAR(255)
);

INSERT INTO publisher (publisher_id,name) VALUES (1,'Addison Wesley Professional');

INSERT INTO author (author_id,first_name,last_name) VALUES (1,'Joshua','Bloch');
INSERT INTO author (author_id,first_name,last_name) VALUES (2,'W.','Stevens');

INSERT INTO book (book_id,title,isbn,publisher_id,author_id) VALUES (1,'Effective Java','0-618-12902-2',1,1);
INSERT INTO book (book_id,title,isbn,publisher_id,author_id) VALUES (2,'TCP/IP Illustrated, Volume 1','0-201-63346-9',1,2);

INSERT INTO localized (id,locale,string) VALUES ('welcome','en','Hello, Dude!');
INSERT INTO localized (id,locale,string) VALUES ('welcome','fr','Salut mon gars !');
INSERT INTO localized (id,locale,string) VALUES ('login','en','Who are you, by the way?');
INSERT INTO localized (id,locale,string) VALUES ('login','fr','T''es qui toi, d''abord ?');
INSERT INTO localized (id,locale,string) VALUES ('internalError','en','Houston? We''ve got a problem here...');
INSERT INTO localized (id,locale,string) VALUES ('internalError','fr','Houston? On a un problème...');
INSERT INTO localized (id,locale,string) VALUES ('badLogin','en','Bad login or password...');
INSERT INTO localized (id,locale,string) VALUES ('badLogin','fr','Mauvais login ou mot de passe...');

INSERT INTO user (id,login,password) VALUES (1,'foo','bar');

