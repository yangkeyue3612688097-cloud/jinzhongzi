package org.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.BusinessException;
import org.example.backend.dto.LoginDTO;
import org.example.backend.dto.RegisterDTO;
import org.example.backend.dto.UserVO;
import org.example.backend.entity.User;
import org.example.backend.mapper.UserMapper;
import org.example.backend.service.UserService;
import org.example.backend.util.JwtUtil;
import org.example.backend.util.SigningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SigningService signingService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void register(RegisterDTO dto) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", dto.getUsername());
        User existing = baseMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        initializeIdentity(user);
        baseMapper.insert(user);
        ensureIdentity(user);
        log.info("用户注册成功：{}", dto.getUsername());
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", dto.getUsername());
        User user = baseMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        ensureIdentity(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("token", token);
        
        log.info("用户登录成功：{}", dto.getUsername());
        return result;
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        ensureIdentity(user);

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setBlockchainAddress(user.getBlockchainAddress());
        vo.setPublicKey(user.getPublicKey());
        vo.setIdentitySignature(user.getIdentitySignature());
        vo.setIdentityAttestedAt(user.getIdentityAttestedAt());
        vo.setIdentityAttested(user.getIdentitySignature() != null && !user.getIdentitySignature().isEmpty());
        vo.setWalletManaged(true);
        return vo;
    }

    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        baseMapper.updateById(user);
        log.info("用户 {} 修改了密码", userId);
    }

    @Override
    public void updateWalletAddress(Long userId, String walletAddress) {
        throw new BusinessException("钱包地址由平台托管身份生成，合约账户接通后再替换地址来源");
    }

    @Override
    public UserVO attestIdentity(Long userId) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        ensureIdentity(user);

        String payload = "aic-identity-attestation|" + userId + "|" + user.getBlockchainAddress()
                + "|" + System.currentTimeMillis();
        String signature = signingService.sign(user.getPrivateKeyEnc(), payload);
        user.setIdentitySignature(signature);
        user.setIdentityAttestedAt(java.time.LocalDateTime.now());
        baseMapper.updateById(user);

        log.info("用户 {} 完成了本地托管身份签名", userId);
        return getUserInfo(userId);
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        try {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new BusinessException("只能上传图片文件");
            }

            if (file.getSize() > 2 * 1024 * 1024) {
                throw new BusinessException("图片大小不能超过 2MB");
            }

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
            String filename = "avatar_" + userId + "_" + System.currentTimeMillis() + extension;

            String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File dest = new File(uploadDir + filename);
            file.transferTo(dest);

            String avatarUrl = "/uploads/avatars/" + filename;
            user.setAvatar(avatarUrl);
            baseMapper.updateById(user);

            log.info("用户 {} 更新了头像：{}", userId, avatarUrl);
            return avatarUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("头像上传失败", e);
            throw new BusinessException("头像上传失败");
        }
    }

    private void initializeIdentity(User user) {
        KeyPair keyPair = signingService.generateKeyPair();
        String publicKey = signingService.encodePublicKey(keyPair.getPublic());
        user.setPublicKey(publicKey);
        user.setPrivateKeyEnc(signingService.encryptPrivateKey(keyPair.getPrivate().getEncoded()));
        user.setBlockchainAddress(signingService.deriveAddress(user.getId(), publicKey));
    }

    private void ensureIdentity(User user) {
        if (user.getPublicKey() != null && !user.getPublicKey().isEmpty()
                && user.getPrivateKeyEnc() != null && !user.getPrivateKeyEnc().isEmpty()) {
            if (user.getId() != null) {
                refreshAddress(user);
            }
            return;
        }
        initializeIdentity(user);
        // 注册流程随后会 insert；老用户登录或读取信息时则立即补齐。
        if (user.getId() != null) {
            refreshAddress(user);
        }
    }

    private void refreshAddress(User user) {
        String address = signingService.deriveAddress(user.getId(), user.getPublicKey());
        if (!address.equals(user.getBlockchainAddress())) {
            user.setBlockchainAddress(address);
            if (user.getIdentitySignature() != null && !user.getIdentitySignature().isEmpty()) {
                user.setIdentitySignature(null);
                user.setIdentityAttestedAt(null);
            }
            baseMapper.updateById(user);
        }
    }
}
