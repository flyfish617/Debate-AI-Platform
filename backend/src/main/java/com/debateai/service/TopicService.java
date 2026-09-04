package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.dto.CreateTopicRequest;
import com.debateai.dto.PageResponse;
import com.debateai.dto.TopicResponse;
import com.debateai.entity.Topic;
import com.debateai.mapper.TopicMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 话题业务服务
 */
@Service
public class TopicService {

    private static final long MAX_PAGE_SIZE = 50;

    private final TopicMapper topicMapper;

    public TopicService(TopicMapper topicMapper) {
        this.topicMapper = topicMapper;
    }

    /**
     * 获取列表
     * @param page
     * @param size
     * @param category
     * @return 业务结果
     */
    public PageResponse<TopicResponse> list(long page, long size, String category) {
        long normalizedPage = Math.max(page, 1);
        long normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        LambdaQueryWrapper<Topic> wrapper = new LambdaQueryWrapper<Topic>()
                .eq(Topic::getStatus, "visible")
                .isNull(Topic::getDeletedAt)
                .orderByDesc(Topic::getCreatedAt);

        if (category != null && !category.isBlank()) {
            wrapper.eq(Topic::getCategory, categoryCode(category));
        }

        long total = topicMapper.selectCount(wrapper);
        long offset = (normalizedPage - 1) * normalizedSize;

        List<TopicResponse> list = topicMapper.selectList(wrapper.last("LIMIT " + offset + ", " + normalizedSize)).stream()
                .map(TopicResponse::from)
                .toList();

        return new PageResponse<>(list, total, normalizedPage, normalizedSize);
    }

    /**
     * 获取详情
     * @param id
     * @return 业务结果
     */
    public TopicResponse detail(Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null || topic.getDeletedAt() != null || !"visible".equals(topic.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "话题不存在");
        }
        return TopicResponse.from(topic);
    }

    /**
     * 创建话题
     * @param request
     * @param creatorId
     * @return 业务结果
     */
    @Transactional
    public TopicResponse create(CreateTopicRequest request, Long creatorId) {
        Topic topic = new Topic();
        topic.setTitle(request.title().trim());
        topic.setDescription(normalizeDescription(request.description()));
        topic.setCategory(categoryCode(request.category()));
        topic.setCreatorId(creatorId);
        topic.setStatus("visible");
        topic.setDebateCount(0);
        topic.setViewCount(0);
        topicMapper.insert(topic);
        return TopicResponse.from(topic);
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private String categoryCode(String category) {
        String value = category == null ? "" : category.trim();
        return switch (value) {
            case "科技", "tech" -> "tech";
            case "社会", "society" -> "society";
            case "哲学", "philosophy" -> "philosophy";
            case "教育", "education" -> "education";
            case "娱乐", "entertainment" -> "entertainment";
            case "其他", "other" -> "other";
            default -> value;
        };
    }
}
