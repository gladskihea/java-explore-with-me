package ru.practicum.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.client.StatsClient;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.location.dto.LocationDto;
import ru.practicum.location.mapper.LocationMapper;
import ru.practicum.location.model.Location;
import ru.practicum.location.repository.LocationRepository;
import ru.practicum.request.model.RequestStatus;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final RequestRepository requestRepository;
    private final StatsClient statsClient;

    private static final String APP_NAME = "ewm-main-service";

    @Override
    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto newEventDto) {
        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Событие не может быть раньше, чем через 2 часа");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category not found"));
        Location location = locationRepository.save(LocationMapper.toEntity(newEventDto.getLocation()));

        Event event = EventMapper.toEntity(newEventDto, category, location, user);
        Event savedEvent = eventRepository.save(event);
        savedEvent.setViews(0L);
        savedEvent.setConfirmedRequests(0L);

        return EventMapper.toFullDto(savedEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getEventsByUser(Long userId, int from, int size) {
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Pageable pageable = createPageable(from, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);
        fillData(events);
        return events.stream().map(EventMapper::toShortDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getEventByIdByUser(Long userId, Long eventId) {
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Это не ваше событие");
        }
        fillData(List.of(event));
        return EventMapper.toFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Изменить можно только отмененные события или события в ожидании");
        }
        if (updateRequest.getEventDate() != null && updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Событие не может быть раньше, чем через 2 часа");
        }

        updateFields(event, updateRequest.getAnnotation(), updateRequest.getCategory(),
                updateRequest.getDescription(), updateRequest.getEventDate(), updateRequest.getLocation(),
                updateRequest.getPaid(), updateRequest.getParticipantLimit(), updateRequest.getRequestModeration(),
                updateRequest.getTitle());

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == ru.practicum.event.model.StateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else if (updateRequest.getStateAction() == ru.practicum.event.model.StateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            }
        }

        fillData(List.of(event));
        return EventMapper.toFullDto(eventRepository.save(event));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size) {
        Pageable pageable = createPageable(from, size);

        boolean hasUsers = users != null && !users.isEmpty();
        List<Long> validUsers = hasUsers ? users : List.of(0L);

        boolean hasStates = states != null && !states.isEmpty();
        List<EventState> validStates = hasStates ? states : List.of(EventState.PUBLISHED);

        boolean hasCategories = categories != null && !categories.isEmpty();
        List<Long> validCategories = hasCategories ? categories : List.of(0L);

        LocalDateTime start = (rangeStart == null) ? LocalDateTime.now().minusYears(100) : rangeStart;
        LocalDateTime end = (rangeEnd == null) ? LocalDateTime.now().plusYears(100) : rangeEnd;

        List<Event> events = eventRepository.findEventsByAdmin(hasUsers, validUsers, hasStates, validStates, hasCategories, validCategories, start, end, pageable);
        fillData(events);
        return events.stream().map(EventMapper::toFullDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));

        if (updateRequest.getEventDate() != null && updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ValidationException("Дата начала события должна быть не ранее чем за час от даты публикации.");
        }

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == ru.practicum.event.model.StateAction.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Событие в состоянии ожидания публикации");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (updateRequest.getStateAction() == ru.practicum.event.model.StateAction.REJECT_EVENT) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Событие можно отклонить, только если оно еще не опубликовано");
                }
                event.setState(EventState.CANCELED);
            }
        }

        updateFields(event, updateRequest.getAnnotation(), updateRequest.getCategory(),
                updateRequest.getDescription(), updateRequest.getEventDate(), updateRequest.getLocation(),
                updateRequest.getPaid(), updateRequest.getParticipantLimit(), updateRequest.getRequestModeration(),
                updateRequest.getTitle());

        fillData(List.of(event));
        return EventMapper.toFullDto(eventRepository.save(event));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getPublishedEvents(String text, List<Long> categories, Boolean paid,
                                                  LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                  Boolean onlyAvailable, String sort, int from, int size,
                                                  HttpServletRequest request) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Дата начала не может быть позже даты конца");
        }

        LocalDateTime start = (rangeStart == null) ? LocalDateTime.now() : rangeStart;
        LocalDateTime end = (rangeEnd == null) ? LocalDateTime.now().plusYears(100) : rangeEnd;

        boolean hasCategories = categories != null && !categories.isEmpty();
        List<Long> validCategories = hasCategories ? categories : List.of(0L);

        // Готовим поисковый текст в Java, чтобы избежать багов конкатенации в SQL при null
        String searchText = (text != null && !text.isBlank()) ? "%" + text.toLowerCase() + "%" : null;

        Pageable pageable = createPageable(from, size);
        List<Event> events = eventRepository.findPublishedEvents(searchText, hasCategories, validCategories, paid, start, end, pageable);

        saveEndpointHit(request);

        if (events.isEmpty()) return Collections.emptyList();

        fillData(events);

        List<Event> result = new ArrayList<>(events);
        if (onlyAvailable != null && onlyAvailable) {
            result = result.stream()
                    .filter(e -> e.getParticipantLimit() == 0 || e.getParticipantLimit() > e.getConfirmedRequests())
                    .collect(Collectors.toList());
        }

        if (sort != null) {
            if (sort.equals("EVENT_DATE")) {
                result.sort(Comparator.comparing(Event::getEventDate));
            } else if (sort.equals("VIEWS")) {
                result.sort(Comparator.comparing(Event::getViews).reversed());
            }
        }

        return result.stream().map(EventMapper::toShortDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getPublishedEventById(Long eventId, HttpServletRequest request) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Событие не найдено"));

        saveEndpointHit(request);
        fillData(List.of(event));
        return EventMapper.toFullDto(event);
    }

    private Pageable createPageable(int from, int size) {
        if (size <= 0) return PageRequest.of(0, 10);
        return PageRequest.of(from / size, size);
    }

    private void updateFields(Event event, String annotation, Long catId, String desc, LocalDateTime date,
                              LocationDto locDto, Boolean paid, Integer limit, Boolean mod, String title) {
        if (annotation != null) event.setAnnotation(annotation);
        if (catId != null) {
            Category cat = categoryRepository.findById(catId).orElseThrow(() -> new NotFoundException("Category not found"));
            event.setCategory(cat);
        }
        if (desc != null) event.setDescription(desc);
        if (date != null) event.setEventDate(date);
        if (locDto != null) event.setLocation(locationRepository.save(LocationMapper.toEntity(locDto)));
        if (paid != null) event.setPaid(paid);
        if (limit != null) event.setParticipantLimit(limit);
        if (mod != null) event.setRequestModeration(mod);
        if (title != null) event.setTitle(title);
    }

    private void fillData(List<Event> events) {
        if (events == null || events.isEmpty()) return;

        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());

        List<Object[]> confirmedRequestsCounts = requestRepository.countConfirmedRequests(eventIds, RequestStatus.CONFIRMED);
        Map<Long, Long> confirmedRequestsMap = confirmedRequestsCounts.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<String> uris = eventIds.stream().map(id -> "/events/" + id).collect(Collectors.toList());
        Map<String, Long> viewsMap = new HashMap<>();

        try {
            List<ViewStatsDto> stats = statsClient.getStats(LocalDateTime.now().minusYears(10), LocalDateTime.now().plusYears(10), uris, true);
            if (stats != null) {
                stats.forEach(s -> viewsMap.put(s.getUri(), s.getHits()));
            }
        } catch (Exception e) {
            log.error("Statistics unavailable: {}", e.getMessage());
        }

        for (Event event : events) {
            event.setConfirmedRequests(confirmedRequestsMap.getOrDefault(event.getId(), 0L));
            event.setViews(viewsMap.getOrDefault("/events/" + event.getId(), 0L));
        }
    }

    private void saveEndpointHit(HttpServletRequest request) {
        try {
            statsClient.saveHit(EndpointHitDto.builder()
                    .app(APP_NAME)
                    .uri(request.getRequestURI())
                    .ip(request.getRemoteAddr())
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("Hit not saved: {}", e.getMessage());
        }
    }
}