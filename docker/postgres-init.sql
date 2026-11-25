-- One Postgres instance, one database per microservice (DB-per-service pattern).
-- Runs automatically on first container start (mounted into /docker-entrypoint-initdb.d).
CREATE DATABASE ridehail_user;
CREATE DATABASE ridehail_driver;
CREATE DATABASE ridehail_ride;
CREATE DATABASE ridehail_notification;
CREATE DATABASE ridehail_admin;
CREATE DATABASE ridehail_helperdesk;
CREATE DATABASE ridehail_socket;
