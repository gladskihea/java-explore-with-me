package ru.practicum.location.mapper;

import ru.practicum.location.dto.LocationDto;
import ru.practicum.location.model.Location;

public class LocationMapper {

    public static Location toEntity(LocationDto dto) {
        return Location.builder()
                .lat(dto.getLat())
                .lon(dto.getLon())
                .build();
    }

    public static LocationDto toDto(Location location) {
        return LocationDto.builder()
                .lat(location.getLat())
                .lon(location.getLon())
                .build();
    }
}