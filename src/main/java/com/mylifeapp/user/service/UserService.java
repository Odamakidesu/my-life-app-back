package com.mylifeapp.user.service;

import com.mylifeapp.common.web.BadRequestException;
import com.mylifeapp.user.exception.DuplicateUsernameException;
import com.mylifeapp.user.exception.UserNotFoundException;
import com.mylifeapp.user.entity.User;
import com.mylifeapp.user.entity.UserRole;
import com.mylifeapp.user.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    /** 一覧で1度に返す上限 */
    public static final int MAX_PAGE_SIZE = 200;

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

    /** 一覧（管理画面用）。ID の昇順。 */
    @Transactional(readOnly = true)
    public List<User> findPage(int page, int size) {
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return userRepository.findPage(effectiveSize, (long) Math.max(page, 0) * effectiveSize);
    }

    @Transactional(readOnly = true)
    public long count() {
        return userRepository.count();
    }

    /** 権限の変更。管理者専用エンドポイントからのみ呼ばれる。 */
    @Transactional
    public User changeRole(Long id, UserRole role) {
        if (userRepository.updateRole(id, role.name()) == 0) {
            throw new UserNotFoundException(id);
        }
        return findById(id);
    }

    /** 有効・無効の切り替え。UserPrincipal が enabled を反映するため、無効化が即座に効く。 */
    @Transactional
    public User changeEnabled(Long id, boolean enabled) {
        if (userRepository.updateEnabled(id, enabled) == 0) {
            throw new UserNotFoundException(id);
        }
        return findById(id);
    }

    /**
     * 本人によるパスワード変更。現在のパスワードを確かめてから変え、
     * それまでに発行したトークン（他の端末のログインを含む）をすべて無効にする。
     *
     * @return 変更後のユーザー（新しいトークンの版を持つ）
     */
    @Transactional
    public User changePassword(Long id, String currentPassword, String newPassword) {
        User user = findById(id);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("現在のパスワードが正しくありません。");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("新しいパスワードは現在のパスワードと別のものにしてください。");
        }
        userRepository.updatePasswordAndRevokeTokens(id, passwordEncoder.encode(newPassword));
        return findById(id);
    }
}
