package com.mylifeapp.user.service;

import com.mylifeapp.user.exception.DuplicateUsernameException;
import com.mylifeapp.user.exception.UserNotFoundException;
import com.mylifeapp.user.model.User;
import com.mylifeapp.user.model.UserRole;
import com.mylifeapp.user.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // BCrypt

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 一般ユーザーとして登録する。役割は常に USER で固定する。
     *
     * <p>重複チェックは事前の userExists と一意制約違反の両方で受ける。
     * 事前チェックだけでは、チェックと INSERT の間に別リクエストが割り込むと
     * 一意制約違反が 500 として漏れるため。
     */
    @Transactional
    public User registerUser(String username, String rawPassword) {
        if (userExists(username)) {
            throw new DuplicateUsernameException();
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
        user.setRole(UserRole.USER);
        try {
            return userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateUsernameException();
        }
    }

    @Transactional(readOnly = true)
    public boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    /** 権限の変更。管理者専用エンドポイントからのみ呼ばれる。 */
    @Transactional
    public User changeRole(Long id, UserRole role) {
        User user = findById(id);
        user.setRole(role);
        return userRepository.save(user);
    }

    /** 有効・無効の切り替え。UserPrincipal が enabled を反映するため、無効化が即座に効く。 */
    @Transactional
    public User changeEnabled(Long id, boolean enabled) {
        User user = findById(id);
        user.setEnabled(enabled);
        return userRepository.save(user);
    }
}
