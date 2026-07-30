package ru.practicum.compilation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.dto.UpdateCompilationRequest;
import ru.practicum.compilation.mapper.CompilationMapper;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.compilation.repository.CompilationRepository;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CompilationDto addCompilation(NewCompilationDto dto) {
        Set<Event> events = new HashSet<>();
        if (dto.getEvents() != null && !dto.getEvents().isEmpty()) {
            events = eventRepository.findAllByIdIn(dto.getEvents());
        }

        Compilation compilation = CompilationMapper.toEntity(dto, events);
        try {
            Compilation savedCompilation = compilationRepository.save(compilation);
            log.info("Создана новая подборка: {}", savedCompilation.getTitle());
            return CompilationMapper.toDto(savedCompilation);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Подборка с таким названием уже существует");
        }
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        Compilation compilation = checkCompilationExists(compId);
        compilationRepository.delete(compilation);
        log.info("Подборка с id {} удалена", compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateRequest) {
        Compilation compilation = checkCompilationExists(compId);

        if (updateRequest.getEvents() != null) {
            Set<Event> events = eventRepository.findAllByIdIn(updateRequest.getEvents());
            compilation.setEvents(events);
        }
        if (updateRequest.getPinned() != null) {
            compilation.setPinned(updateRequest.getPinned());
        }
        if (updateRequest.getTitle() != null) {
            compilation.setTitle(updateRequest.getTitle());
        }

        try {
            Compilation updatedCompilation = compilationRepository.save(compilation);
            log.info("Подборка с id {} обновлена", compId);
            return CompilationMapper.toDto(updatedCompilation);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Подборка с таким названием уже существует");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);
        List<Compilation> compilations;

        if (pinned == null) {
            compilations = compilationRepository.findAll(pageable).getContent();
        } else {
            compilations = compilationRepository.findAllByPinned(pinned, pageable).getContent();
        }

        return compilations.stream()
                .map(CompilationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CompilationDto getCompilationById(Long compId) {
        Compilation compilation = checkCompilationExists(compId);
        return CompilationMapper.toDto(compilation);
    }

    private Compilation checkCompilationExists(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
    }
}