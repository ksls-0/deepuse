package org.example.deepusecode.character.service;

import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.entity.User;
import org.example.deepusecode.character.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批量把 userId 解析成账号，避免列表接口 N+1 查库。
 */
@Component
@RequiredArgsConstructor
public class UserAccountResolver {

    private final UserRepository userRepository;

    public Map<Long, String> resolve(Collection<Long> userIds) {
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>(ids.size());
        for (User user : userRepository.findAllById(ids)) {
            result.put(user.getId(), user.getAccount());
        }
        return result;
    }

    public String resolveOne(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getAccount).orElse(null);
    }
}
