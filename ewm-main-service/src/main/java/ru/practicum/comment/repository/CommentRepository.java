package ru.practicum.comment.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.comment.model.Comment;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Получить все комментарии к конкретному событию с пагинацией
    List<Comment> findAllByEventId(Long eventId, Pageable pageable);
}