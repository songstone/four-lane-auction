package com.fourlane.auction.session.domain;
import org.springframework.data.repository.Repository;
public interface SessionLaneRepository extends Repository<SessionLane,Long> { SessionLane save(SessionLane lane); }
