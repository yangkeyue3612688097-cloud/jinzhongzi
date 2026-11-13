package org.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.backend.dto.LoginDTO;
import org.example.backend.dto.RegisterDTO;
import org.example.backend.dto.UserVO;
import org.example.backend.entity.User;

import java.util.Map;

public interface UserService extends IService<User> {
    void register(RegisterDTO dto);
    Map<String, Object> login(LoginDTO dto);
    UserVO getUserInfo(Long userId);
    void updatePassword(Long userId, String oldPassword, String newPassword);
    String uploadAvatar(Long userId, org.springframework.web.multipart.MultipartFile file);
    void updateWalletAddress(Long userId, String walletAddress);
    UserVO attestIdentity(Long userId);
}
