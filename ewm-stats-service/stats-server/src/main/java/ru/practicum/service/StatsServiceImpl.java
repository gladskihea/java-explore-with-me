package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.mapper.StatsMapper;
import ru.practicum.model.ViewStats;
import ru.practicum.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final StatsRepository repository;

    @Override
    @Transactional
    public void saveHit(EndpointHitDto dto) {
        repository.save(StatsMapper.toEntity(dto));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты конца");
        }

        List<ViewStats> result;

        if (uris == null || uris.isEmpty()) {
            if (unique) {
                result = repository.getAllUniqueStats(start, end);
            } else {
                result = repository.getAllStats(start, end);
            }
        } else {
            if (unique) {
                result = repository.getUniqueStatsByUris(start, end, uris);
            } else {
                result = repository.getStatsByUris(start, end, uris);
            }
        }

        return result.stream()
                .map(StatsMapper::toDto)
                .collect(Collectors.toList());
    }
}