@echo off
REM Create all required Kafka topics for UAV-AMS
REM Topic naming: all use dot-separated, matching Go/Java code

set KAFKA_CMD=docker exec uav-kafka kafka-topics --bootstrap-server localhost:9092

echo   Creating topics...

REM === Data ingest topics ===
%KAFKA_CMD% --create --if-not-exists --topic uav.telemetry              --partitions 3 --replication-factor 1 2>nul >nul

REM === Alarm / Conflict / Resolution topics ===
%KAFKA_CMD% --create --if-not-exists --topic uav.alarm.event            --partitions 3 --replication-factor 1 2>nul >nul
%KAFKA_CMD% --create --if-not-exists --topic uav.conflict.alert         --partitions 3 --replication-factor 1 2>nul >nul
%KAFKA_CMD% --create --if-not-exists --topic uav.signboard.conflict     --partitions 3 --replication-factor 1 2>nul >nul
%KAFKA_CMD% --create --if-not-exists --topic uav.signboard.resolution   --partitions 3 --replication-factor 1 2>nul >nul
%KAFKA_CMD% --create --if-not-exists --topic uav.ops-seat.resolution    --partitions 3 --replication-factor 1 2>nul >nul

REM === Track fusion topics ===
%KAFKA_CMD% --create --if-not-exists --topic uav.track.raw              --partitions 3 --replication-factor 1 2>nul >nul
%KAFKA_CMD% --create --if-not-exists --topic uav.track.fitted           --partitions 3 --replication-factor 1 2>nul >nul

echo   Topics OK: uav.telemetry uav.alarm.event uav.conflict.alert uav.signboard.conflict uav.signboard.resolution uav.ops-seat.resolution uav.track.raw uav.track.fitted
