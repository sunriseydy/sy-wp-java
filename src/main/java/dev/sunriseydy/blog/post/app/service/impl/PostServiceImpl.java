package dev.sunriseydy.blog.post.app.service.impl;

import dev.sunriseydy.blog.category.api.dto.CategoryDTO;
import dev.sunriseydy.blog.category.domain.repository.CategoryRepository;
import dev.sunriseydy.blog.common.utils.PageUtil;
import dev.sunriseydy.blog.common.vo.PageVO;
import dev.sunriseydy.blog.post.api.dto.PostDTO;
import dev.sunriseydy.blog.post.api.dto.PostMeta;
import dev.sunriseydy.blog.post.app.service.PostMetaService;
import dev.sunriseydy.blog.post.app.service.PostService;
import dev.sunriseydy.blog.post.domain.repository.PostRepository;
import dev.sunriseydy.blog.tag.api.dto.TagDTO;
import dev.sunriseydy.blog.tag.domain.repository.TagRepository;
import dev.sunriseydy.blog.user.domain.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author SunriseYDY
 * @date 2022-03-21 11:46
 */
@Slf4j
@Service
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;

    private final PostMetaService postMetaService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    private final UserRepository userRepository;

    public PostServiceImpl(PostMetaService postMetaService, CategoryRepository categoryRepository, TagRepository tagRepository, UserRepository userRepository, PostRepository postRepository, RedisTemplate<String, Object> redisTemplate) {
        this.postMetaService = postMetaService;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<Long> getPostIdList() {
        return postRepository.getPostIdList();
    }

    @Override
    public List<PostDTO> getPostList() {
        return this.postMetaService.getAllPostMetas()
                .stream()
                .map(PostMeta::getId)
                .map(postRepository::getPostById)
                .collect(Collectors.toList());
    }

    @Override
    public PageVO<PostDTO> getPostPage(int page, int pageSize) {
        PageVO<PostMeta> metaPage = postMetaService.getPostMetasByPage(page, pageSize);

        return PageVO.<PostDTO>builder()
                .page(metaPage.getPage())
                .pageSize(metaPage.getPageSize())
                .totalPages(metaPage.getTotalPages())
                .total(metaPage.getTotal())
                .content(metaPage.getContent().stream()
                        .map(postMeta -> this.getPostById(postMeta.getId()).clearContent())
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public PostDTO getPostById(Long id) {
        PostDTO postDTO = postRepository.getPostById(id);
        // 获取 category
        if (CollectionUtils.isNotEmpty(postDTO.getCategories())) {
            List<CategoryDTO> categories = postDTO.getCategories()
                    .stream()
                    .map(categoryRepository::getCategoryById)
                    .collect(Collectors.toList());
            postDTO.setCategoriesList(categories);
        }
        // 获取 tag
        if (CollectionUtils.isNotEmpty(postDTO.getTags())) {
            List<TagDTO> tags = postDTO.getTags()
                    .stream()
                    .map(tagRepository::getTagById)
                    .collect(Collectors.toList());
            postDTO.setTagsList(tags);
        }
        // 获取 user
        if (postDTO.getAuthor() != null) {
            postDTO.setAuthorDto(this.userRepository.getUserById(postDTO.getAuthor()));
        }
        return postDTO;
    }

    @Override
    public PostDTO getPostBySlug(String slug) {
        Long id = this.postMetaService.getPostIdBySlug(slug);
        return this.getPostById(id);
    }

    @Override
    public PageVO<PostDTO> getPostsByCategoryId(Long id, int page, int pageSize) {
        List<PostDTO> list = this.postMetaService.getPostMetasByCategoryId(id).stream()
                .map(postMeta -> this.getPostById(postMeta.getId()).clearContent())
                .collect(Collectors.toList());
        return PageUtil.doPage(page, pageSize, list);
    }

    @Override
    public PageVO<PostDTO> getPostsByTagId(Long id, int page, int pageSize) {
        List<PostDTO> list = this.postMetaService.getPostMetasByTagId(id).stream()
                .map(postMeta -> this.getPostById(postMeta.getId()).clearContent())
                .collect(Collectors.toList());
        return PageUtil.doPage(page, pageSize, list);
    }

    @Override
    public PageVO<PostDTO> searchPosts(String search, int page, int pageSize) {
        List<PostDTO> list = this.getPostList()
                .stream()
                .filter(postDTO -> postDTO.getContentString().toLowerCase().contains(search.toLowerCase()))
                .map(postDTO -> this.getPostById(postDTO.getId()).clearContent())
                .collect(Collectors.toList());
        return PageUtil.doPage(page, pageSize, list);
    }

    @Override
    public PostDTO updatePostById(Long id) {
        // 更新 post
        PostDTO postDTO = postRepository.updatePostById(id);
        // 更新 post meta
        postMetaService.updatePostMetaCache(postDTO);
        // 更新 category
        if (CollectionUtils.isNotEmpty(postDTO.getCategories())) {
            postDTO.getCategories().forEach(categoryRepository::updateCategoryById);
        }
        // 更新 tag
        if (CollectionUtils.isNotEmpty(postDTO.getTags())) {
            postDTO.getTags().forEach(tagRepository::updateTagById);
        }
        // 更新 user
        userRepository.updateUserById(postDTO.getAuthor());
        return postDTO;
    }

    @Override
    public void deletePostById(Long id) {
        postRepository.deletePostById(id);
    }

}
