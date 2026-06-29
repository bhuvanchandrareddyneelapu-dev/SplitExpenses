package com.splitwisemoney.service;

import com.splitwisemoney.entity.ActivityLog;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.repository.ActivityLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional
    public void log(User user, String action) {
        activityLogRepository.save(new ActivityLog(user, action));
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> getUserLogs(User user, Pageable pageable) {
        return activityLogRepository.findByUserId(user.getId(), pageable);
    }
}
