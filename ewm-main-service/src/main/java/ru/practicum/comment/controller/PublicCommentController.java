package ru.practicum.comment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.comment.dto.CommentDto;
import ru.practicum.comment.service.CommentService;

import java.util.List;

@RestController
@RequestMapping(path = "/events/{eventId}/comments")
@RequiredArgsConstructor
@Slf4j
public class PublicCommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentDto> getCommentsByEvent(@PathVariable Long eventId,
                                               @RequestParam(defaultValue = "0") int from,
                                               @RequestParam(defaultValue = "10") int size) {
        log.info("Public: Запрос комментариев к событию {}", eventId);
        return commentService.getCommentsByEvent(eventId, from, size);
    }
}