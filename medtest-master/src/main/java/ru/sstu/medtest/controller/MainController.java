package ru.sstu.medtest.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ru.sstu.medtest.config.jwt.JwtTokenUtil;
import ru.sstu.medtest.entity.Stat;
import ru.sstu.medtest.entity.UserEntity;
import ru.sstu.medtest.entity.auth.JwtRequest;
import ru.sstu.medtest.entity.auth.JwtResponse;
import ru.sstu.medtest.repository.RoleRepository;
import ru.sstu.medtest.repository.StatRepository;
import ru.sstu.medtest.repository.UserRepository;

import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
public class MainController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    StatRepository statRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtTokenUtil jwtUtils;

    @GetMapping("")
    public String welcome() {
        return "home";
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody JwtRequest user) {
        try {
            Optional<UserEntity> userAttempt = userRepository.findByLogin(user.getLogin());
            if (userAttempt.isEmpty()) {
                return ResponseEntity
                        .badRequest()
                        .body("Ошибка! Пользователь не найден");
            } else if (!encoder.matches(user.getPassword(), userAttempt.get().getPassword())) {
                return ResponseEntity
                        .badRequest()
                        .body("Ошибка! Неверный пароль");
            }

            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            return ResponseEntity.ok().body(new JwtResponse(jwt, userAttempt.get().getLogin(), userAttempt.get().getRoles().stream().findFirst().get(), userAttempt.get().getId()));
        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .body("Что-то пошло не так! Обратитесь к администратору");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserEntity user) {
        try {
            if (userRepository.findByLogin(user.getLogin()).isPresent()) {
                return ResponseEntity
                        .badRequest()
                        .body("Ошибка! Данный логин уже используется");
            }

            if (!user.getPassword().equals(user.getPasswordAccept())) {
                return ResponseEntity
                        .badRequest()
                        .body("Ошибка! Пароли не совпадают");
            }

            System.out.println(userRepository.count());
            user.setId(userRepository.count()+1);
            user.setPassword(encoder.encode(user.getPassword()));
            user.setPasswordAccept(null);
            user.setRoles(Collections.singleton(roleRepository.findBySystemName("USER")));
            user.setActive(true);

            System.out.println(user);
            var instance = userRepository.save(user);
            System.out.println(instance);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Произошла ошибка при регистрации! Обратитесь к администратору");
        }

        return ResponseEntity.ok("");
    }

    @GetMapping("/profile")
    public ResponseEntity<?> profile() {
        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok().body(user);
    }

    @GetMapping("/stat")
    public ResponseEntity<?> stat() {
        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok().body(statRepository.findAll().stream()
                .sorted(Comparator.comparing(Stat::getLastPass).reversed())
                .collect(Collectors.toList()));
    }

}
