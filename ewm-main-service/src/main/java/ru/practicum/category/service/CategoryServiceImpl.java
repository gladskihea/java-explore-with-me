package ru.practicum.category.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.dto.NewCategoryDto;
import ru.practicum.category.mapper.CategoryMapper;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryDto addCategory(NewCategoryDto newCategoryDto) {
        Category category = CategoryMapper.toEntity(newCategoryDto);
        try {
            Category savedCategory = categoryRepository.save(category);
            log.info("Добавлена новая категория: {}", savedCategory.getName());
            return CategoryMapper.toDto(savedCategory);
        } catch (DataIntegrityViolationException e) {
            log.error("Категория с таким именем уже существует: {}", newCategoryDto.getName());
            throw new ConflictException("Category with name " + newCategoryDto.getName() + " already exists.");
        }
    }

    @Override
    @Transactional
    public void deleteCategory(Long catId) {
        Category category = checkCategoryExists(catId);
        try {
            categoryRepository.delete(category);
            log.info("Категория с id {} успешно удалена", catId);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("The category is not empty");
        }
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto categoryDto) {
        Category category = checkCategoryExists(catId);

        if (categoryDto.getName().equals(category.getName())) {
            return CategoryMapper.toDto(category);
        }

        category.setName(categoryDto.getName());
        try {
            Category updatedCategory = categoryRepository.save(category);
            log.info("Категория с id {} успешно обновлена", catId);
            return CategoryMapper.toDto(updatedCategory);
        } catch (DataIntegrityViolationException e) {
            log.error("Категория с таким именем уже существует: {}", categoryDto.getName());
            throw new ConflictException("Category with name " + categoryDto.getName() + " already exists.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories(int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);
        return categoryRepository.findAll(pageable).stream()
                .map(CategoryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDto getCategoryById(Long catId) {
        Category category = checkCategoryExists(catId);
        return CategoryMapper.toDto(category);
    }

    private Category checkCategoryExists(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }
}