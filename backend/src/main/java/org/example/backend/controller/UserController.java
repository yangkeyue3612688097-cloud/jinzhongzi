package org.example.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.Result;
import org.example.backend.dto.LoginDTO;
import org.example.backend.dto.RegisterDTO;
import org.example.backend.dto.UserVO;
import org.example.backend.entity.OperationLog;
import org.example.backend.entity.User;
import org.example.backend.mapper.OperationLogMapper;
import org.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto) {
        Map<String, Object> result = userService.login(dto);
        return Result.success(result);
    }

    @GetMapping("/info")
    public Result<UserVO> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserVO user = userService.getUserInfo(userId);
        return Result.success(user);
    }

    @GetMapping("/search")
    public Result<List<Map<String, Object>>> searchUsers(@RequestParam String keyword) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.like("username", keyword).or().like("email", keyword);
        wrapper.last("LIMIT 10");
        List<User> users = userService.list(wrapper);
        
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("email", u.getEmail());
            return map;
        }).collect(Collectors.toList());
        
        return Result.success(result);
    }

    @PostMapping("/update-password")
    public Result<Void> updatePassword(HttpServletRequest request, @RequestBody Map<String, String> params) {
        Long userId = (Long) request.getAttribute("userId");
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        
        userService.updatePassword(userId, oldPassword, newPassword);
        saveOperationLog(userId, "修改密码", "用户修改了登录密码");
        
        return Result.success();
    }

    @PostMapping("/update-wallet")
    public Result<Void> updateWallet(HttpServletRequest request, @RequestBody Map<String, String> params) {
        Long userId = (Long) request.getAttribute("userId");
        String walletAddress = params.get("walletAddress");
        
        userService.updateWalletAddress(userId, walletAddress);
        saveOperationLog(userId, "尝试绑定钱包地址", "钱包地址已改为平台托管身份，人工绑定申请未生效");
        
        return Result.success();
    }

    @PostMapping("/attest-identity")
    public Result<UserVO> attestIdentity(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserVO vo = userService.attestIdentity(userId);
        saveOperationLog(userId, "身份签名", "完成平台托管身份签名：" + vo.getBlockchainAddress());
        return Result.success(vo);
    }

    @PostMapping("/upload-avatar")
    public Result<String> uploadAvatar(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        Long userId = (Long) request.getAttribute("userId");
        String avatarUrl = userService.uploadAvatar(userId, file);
        saveOperationLog(userId, "上传头像", "更新了个人头像");
        return Result.success(avatarUrl);
    }

    @GetMapping("/operation-logs")
    public Result<List<OperationLog>> getOperationLogs(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        
        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time").last("LIMIT 50");
        List<OperationLog> logs = operationLogMapper.selectList(wrapper);
        
        return Result.success(logs);
    }

    private void saveOperationLog(Long userId, String operation, String detail) {
        try {
            OperationLog log = new OperationLog();
            log.setUserId(userId);
            log.setOperation(operation);
            log.setDetail(detail);
            operationLogMapper.insert(log);
        } catch (Exception e) {
            // 日志记录失败不影响主流程
        }
    }
}
