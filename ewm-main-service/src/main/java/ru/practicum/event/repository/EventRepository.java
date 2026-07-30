package ru.practicum.event.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByInitiatorId(Long initiatorId, Pageable pageable);

    Optional<Event> findByIdAndState(Long id, EventState state);

    @Query("SELECT e FROM Event e " +
            "WHERE (:hasUsers = false OR e.initiator.id IN :users) " +
            "AND (:hasStates = false OR e.state IN :states) " +
            "AND (:hasCategories = false OR e.category.id IN :categories) " +
            "AND e.eventDate >= :rangeStart " +
            "AND e.eventDate <= :rangeEnd")
    List<Event> findEventsByAdmin(@Param("hasUsers") Boolean hasUsers,
                                  @Param("users") List<Long> users,
                                  @Param("hasStates") Boolean hasStates,
                                  @Param("states") List<EventState> states,
                                  @Param("hasCategories") Boolean hasCategories,
                                  @Param("categories") List<Long> categories,
                                  @Param("rangeStart") LocalDateTime rangeStart,
                                  @Param("rangeEnd") LocalDateTime rangeEnd,
                                  Pageable pageable);

    @Query("SELECT e FROM Event e " +
            "WHERE e.state = 'PUBLISHED' " +
            "AND (:searchText IS NULL OR LOWER(e.annotation) LIKE :searchText OR LOWER(e.description) LIKE :searchText) " +
            "AND (:hasCategories = false OR e.category.id IN :categories) " +
            "AND (:paid IS NULL OR e.paid = :paid) " +
            "AND e.eventDate >= :rangeStart " +
            "AND e.eventDate <= :rangeEnd")
    List<Event> findPublishedEvents(@Param("searchText") String searchText,
                                    @Param("hasCategories") Boolean hasCategories,
                                    @Param("categories") List<Long> categories,
                                    @Param("paid") Boolean paid,
                                    @Param("rangeStart") LocalDateTime rangeStart,
                                    @Param("rangeEnd") LocalDateTime rangeEnd,
                                    Pageable pageable);

    Set<Event> findAllByIdIn(Set<Long> ids);
}