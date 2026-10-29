package com.expensesplitter.service;

import com.expensesplitter.dao.ExpenseDao;
import com.expensesplitter.dao.GroupDao;
import com.expensesplitter.dao.MemberDao;
import com.expensesplitter.dao.PaymentDao;
import com.expensesplitter.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GroupService {

    private final GroupDao groupDao;
    private final MemberDao memberDao;
    private final ExpenseDao expenseDao;
    private final PaymentDao paymentDao;
    private final SplitCalculationService splitCalculationService;

    public GroupService(GroupDao groupDao, MemberDao memberDao, ExpenseDao expenseDao,
                        PaymentDao paymentDao, SplitCalculationService splitCalculationService) {
        this.groupDao = groupDao;
        this.memberDao = memberDao;
        this.expenseDao = expenseDao;
        this.paymentDao = paymentDao;
        this.splitCalculationService = splitCalculationService;
    }

    public List<Group> getAllGroups() {
        return groupDao.findAll();
    }

    public List<Group> getGroupsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return groupDao.findAll();
        }
        return groupDao.findByUserId(userId);
    }

    public List<Group> getGroupsByStatus(GroupStatus status) {
        return groupDao.findByStatus(status);
    }

    public List<Group> getGroupsForUserAndStatus(String userId, GroupStatus status) {
        if (userId == null || userId.isBlank()) {
            return groupDao.findByStatus(status);
        }
        return groupDao.findByUserIdAndStatus(userId, status);
    }

    public Optional<Group> getGroupById(String id) {
        return groupDao.findById(id);
    }

    public void createGroup(Group group, List<String> memberIds) {
        groupDao.save(group);
        if (memberIds != null) {
            for (String memberId : memberIds) {
                groupDao.addMemberToGroup(group.getId(), memberId);
            }
        }
    }

    public void updateGroup(Group group) {
        groupDao.update(group);
    }

    public void deleteGroup(String id) {
        groupDao.delete(id);
    }

    public void archiveGroup(String id) {
        groupDao.findById(id).ifPresent(g -> {
            Group archived = new Group(g.getId(), g.getName(), g.getCurrency(), GroupStatus.ARCHIVED, g.getCreatedAt(), System.currentTimeMillis());
            groupDao.update(archived);
        });
    }

    public List<Member> getGroupMembers(String groupId) {
        return memberDao.findByGroupId(groupId);
    }

    public void addMemberToGroup(String groupId, String memberId) {
        groupDao.addMemberToGroup(groupId, memberId);
    }

    public void removeMemberFromGroup(String groupId, String memberId) {
        groupDao.removeMemberFromGroup(groupId, memberId);
    }
}

