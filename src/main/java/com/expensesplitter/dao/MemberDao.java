package com.expensesplitter.dao;

import com.expensesplitter.model.Member;

import java.util.List;
import java.util.Optional;

public interface MemberDao {
    void save(Member member);
    void update(Member member);
    void delete(String id);
    Optional<Member> findById(String id);
    Optional<Member> findByUserId(String userId);
    List<Member> findAll();
    List<Member> findByGroupId(String groupId);
}

