package ru.practicum.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.comment.dto.CommentDto;
import ru.practicum.comment.dto.NewCommentDto;
import ru.practicum.comment.mapper.CommentMapper;
import ru.practicum.comment.model.Comment;
import ru.practicum.comment.repository.CommentRepository;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CommentDto addComment(Long userId, Long eventId, NewCommentDto dto) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Нельзя комментировать неопубликованное событие");
        }

        Comment comment = CommentMapper.toEntity(dto, author, event);
        Comment savedComment = commentRepository.save(comment);
        log.info("Пользователь с id {} оставил комментарий к событию с id {}", userId, eventId);
        return CommentMapper.toDto(savedComment);
    }

    @Override
    @Transactional
    public CommentDto updateComment(Long userId, Long commentId, NewCommentDto dto) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ConflictException("Вы можете редактировать только свои комментарии");
        }

        comment.setText(dto.getText());
        Comment updatedComment = commentRepository.save(comment);
        log.info("Пользователь с id {} обновил комментарий с id {}", userId, commentId);
        return CommentMapper.toDto(updatedComment);
    }

    @Override
    @Transactional
    public void deleteCommentByUser(Long userId, Long commentId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ConflictException("Вы можете удалять только свои комментарии");
        }

        commentRepository.delete(comment);
        log.info("Пользователь с id {} удалил комментарий с id {}", userId, commentId);
    }

    @Override
    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
        commentRepository.delete(comment);
        log.info("Администратор удалил комментарий с id {}", commentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> getCommentsByEvent(Long eventId, int from, int size) {
        eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        Pageable pageable = PageRequest.of(from / size, size);
        return commentRepository.findAllByEventId(eventId, pageable).stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }
}