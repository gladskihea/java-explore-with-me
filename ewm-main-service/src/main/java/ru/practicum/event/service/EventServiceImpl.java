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
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final StatsClient statsClient;
    private final ru.practicum.request.repository.RequestRepository requestRepository;

    private static final String APP_NAME = "ewm-main-service";


    @Override
    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto newEventDto) {
        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConflictException("Событие не может быть раньше, чем через 2 часа");
        }
        User user = getUser(userId);
        Category category = getCategory(newEventDto.getCategory());
        Location location = locationRepository.save(LocationMapper.toEntity(newEventDto.getLocation()));

        Event event = EventMapper.toEntity(newEventDto, category, location, user);
        Event savedEvent = eventRepository.save(event);
        savedEvent.setViews(0L);
        savedEvent.setConfirmedRequests(0L);

        log.info("Создано событие: {}", savedEvent.getTitle());
        return EventMapper.toFullDto(savedEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getEventsByUser(Long userId, int from, int size) {
        getUser(userId);
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);
        setViewsAndConfirmedRequests(events);
        return events.stream().map(EventMapper::toShortDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getEventByIdByUser(Long userId, Long eventId) {
        getUser(userId);
        Event event = getEvent(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Это не ваше событие");
        }
        setViewsAndConfirmedRequests(List.of(event));
        return EventMapper.toFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        getUser(userId);
        Event event = getEvent(eventId);

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Это не ваше событие");
        }
        if (event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Изменить можно только отмененные события или события в ожидании");
        }
        if (updateRequest.getEventDate() != null && updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConflictException("Событие не может быть раньше, чем через 2 часа");
        }

        updateEventFields(event, updateRequest.getAnnotation(), updateRequest.getCategory(),
                updateRequest.getDescription(), updateRequest.getEventDate(), updateRequest.getLocation(),
                updateRequest.getPaid(), updateRequest.getParticipantLimit(), updateRequest.getRequestModeration(),
                updateRequest.getTitle());

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case SEND_TO_REVIEW:
                    event.setState(EventState.PENDING);
                    break;
                case CANCEL_REVIEW:
                    event.setState(EventState.CANCELED);
                    break;
            }
        }

        setViewsAndConfirmedRequests(List.of(event));
        return EventMapper.toFullDto(eventRepository.save(event));
    }


    @Override
    @Transactional(readOnly = true)
    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findEventsByAdmin(users, states, categories, rangeStart, rangeEnd, pageable);
        setViewsAndConfirmedRequests(events);
        return events.stream().map(EventMapper::toFullDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = getEvent(eventId);

        if (updateRequest.getEventDate() != null && updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ConflictException("Событие не может быть раньше, чем через 1 час от публикации");
        }

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction().name().equals("PUBLISH_EVENT") && !event.getState().equals(EventState.PENDING)) {
                throw new ConflictException("Событие можно публиковать, только если оно в ожидании");
            }
            if (updateRequest.getStateAction().name().equals("REJECT_EVENT") && event.getState().equals(EventState.PUBLISHED)) {
                throw new ConflictException("Событие можно отклонить, только если оно еще не опубликовано");
            }

            if (updateRequest.getStateAction().name().equals("PUBLISH_EVENT")) {
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (updateRequest.getStateAction().name().equals("REJECT_EVENT")) {
                event.setState(EventState.CANCELED);
            }
        }

        updateEventFields(event, updateRequest.getAnnotation(), updateRequest.getCategory(),
                updateRequest.getDescription(), updateRequest.getEventDate(), updateRequest.getLocation(),
                updateRequest.getPaid(), updateRequest.getParticipantLimit(), updateRequest.getRequestModeration(),
                updateRequest.getTitle());

        setViewsAndConfirmedRequests(List.of(event));
        return EventMapper.toFullDto(eventRepository.save(event));
    }


    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getPublishedEvents(String text, List<Long> categories, Boolean paid,
                                                  LocalDateTime rangeStart, LocalDateTime rangeEnd, Boolean onlyAvailable,
                                                  String sort, int from, int size, HttpServletRequest request) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Дата начала не может быть позже даты конца");
        }

        saveEndpointHit(request);

        Pageable pageable = PageRequest.of(from / size, size);
        if (rangeStart == null && rangeEnd == null) {
            rangeStart = LocalDateTime.now();
        }

        List<Event> events = eventRepository.findPublishedEvents(text, categories, paid, rangeStart, rangeEnd, pageable);
        setViewsAndConfirmedRequests(events);

        if (onlyAvailable != null && onlyAvailable) {
            events = events.stream()
                    .filter(e -> e.getParticipantLimit() == 0 || e.getParticipantLimit() > e.getConfirmedRequests())
                    .collect(Collectors.toList());
        }

        if (sort != null) {
            if (sort.equals("EVENT_DATE")) {
                events.sort(Comparator.comparing(Event::getEventDate));
            } else if (sort.equals("VIEWS")) {
                events.sort(Comparator.comparing(Event::getViews).reversed());
            }
        }

        return events.stream().map(EventMapper::toShortDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getPublishedEventById(Long eventId, HttpServletRequest request) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Событие не найдено или недоступно"));

        saveEndpointHit(request); // Сохраняем просмотр карточки
        setViewsAndConfirmedRequests(List.of(event));
        return EventMapper.toFullDto(event);
    }


    private void updateEventFields(Event event, String annotation, Long catId, String desc, LocalDateTime date,
                                   LocationDto locDto, Boolean paid, Integer limit, Boolean mod, String title) {
        if (annotation != null) event.setAnnotation(annotation);
        if (catId != null) event.setCategory(getCategory(catId));
        if (desc != null) event.setDescription(desc);
        if (date != null) event.setEventDate(date);
        if (locDto != null) {
            Location location = locationRepository.save(LocationMapper.toEntity(locDto));
            event.setLocation(location);
        }
        if (paid != null) event.setPaid(paid);
        if (limit != null) event.setParticipantLimit(limit);
        if (mod != null) event.setRequestModeration(mod);
        if (title != null) event.setTitle(title);
    }

    private void setViewsAndConfirmedRequests(List<Event> events) {
        if (events.isEmpty()) return;

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        List<ViewStatsDto> stats = statsClient.getStats(
                LocalDateTime.of(2020, 1, 1, 0, 0),
                LocalDateTime.now().plusHours(1),
                uris, true);

        Map<String, Long> viewsMap = stats.stream()
                .collect(Collectors.toMap(ViewStatsDto::getUri, ViewStatsDto::getHits));

        for (Event event : events) {
            event.setViews(viewsMap.getOrDefault("/events/" + event.getId(), 0L));
            // Пока нет модуля Requests, ставим 0
            Long confirmedCount = requestRepository.countByEventIdAndStatus(event.getId(), ru.practicum.request.model.RequestStatus.CONFIRMED);
            event.setConfirmedRequests(confirmedCount);
        }
    }

    private void saveEndpointHit(HttpServletRequest request) {
        statsClient.saveHit(EndpointHitDto.builder()
                .app(APP_NAME)
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build());
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    private Category getCategory(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие не найдено"));
    }
}