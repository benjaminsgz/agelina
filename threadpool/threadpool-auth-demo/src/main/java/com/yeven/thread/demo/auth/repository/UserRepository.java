package com.yeven.thread.demo.auth.repository;

import com.yeven.thread.demo.common.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) ->
            new User(rs.getLong("id"), rs.getString("username"), rs.getString("password"));

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public User findByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, username, password FROM users WHERE username = ?",
                    USER_ROW_MAPPER,
                    username
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
