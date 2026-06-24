package com.artverse.config;

import cn.dev33.satoken.stp.StpInterface;
import com.artverse.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserRepository userRepository;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return userRepository.findById((Long) loginId)
                .map(u -> List.of(u.getRole().name()))
                .orElseGet(ArrayList::new);
    }
}
