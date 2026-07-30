package ru.practicum.comment.service;

import ru.practicum.comment.dto.CommentDto;
import ru.practicum.comment.dto.NewCommentDto;

import java.util.List;

public interface CommentService {
    CommentDto addComment(Long userId, Long eventId, NewCommentDto dto);

    CommentDto updateComment(Long userId, Long commentId, NewCommentDto dto);

    void deleteCommentByUser(Long userId, Long commentId);

    void deleteCommentByAdmin(Long commentId);

    List<CommentDto> getCommentsByEvent(Long eventId, int from, int size);
}