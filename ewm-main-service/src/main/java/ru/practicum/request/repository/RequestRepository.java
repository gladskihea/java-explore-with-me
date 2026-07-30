package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.request.model.Request;
import ru.practicum.request.model.RequestStatus;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findAllByRequesterId(Long requesterId);

    List<Request> findAllByEventId(Long eventId);

    Boolean existsByEventIdAndRequesterId(Long eventId, Long requesterId);

    Long countByEventIdAndStatus(Long eventId, RequestStatus status);

    @Query("SELECT r.event.id, COUNT(r.id) FROM Request r " +
            "WHERE r.event.id IN :eventIds AND r.status = :status " +
            "GROUP BY r.event.id")
    List<Object[]> countConfirmedRequests(@Param("eventIds") List<Long> eventIds,
                                          @Param("status") RequestStatus status);
}