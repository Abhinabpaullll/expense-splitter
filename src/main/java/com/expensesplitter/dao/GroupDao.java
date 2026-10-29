package com.expensesplitter.dao;

import com.expensesplitter.model.Group;
import com.expensesplitter.model.GroupStatus;

import java.util.List;
import java.util.Optional;

public interface GroupDao {
    void save(Group group);
    void update(Group group);
    void delete(String id);
    Optional<Group> findById(String id);
    List<Group> findAll();
    List<Group> findByStatus(GroupStatus status);
    List<Group> findByUserId(String userId);
    List<Group> findByUserIdAndStatus(String userId, GroupStatus status);
    void addMemberToGroup(String groupId, String memberId);
    void removeMemberFromGroup(String groupId, String memberId);
    boolean isMemberInGroup(String groupId, String memberId);
}

