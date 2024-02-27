package org.example.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.LoginAccount;
import org.example.entity.TaskQueue;
import org.example.service.FollowersService;
import org.example.service.IgUserService;
import org.example.service.InstagramService;
import org.example.service.TaskQueueService;
import org.example.utils.FollowerCrawlingUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Eric.Lee
 * Date: 2024/2/27
 */
@Slf4j
@Service("getFollowerStrategy")
public class GetFollowerStrategy extends TaskStrategyBase implements TaskStrategy {
    @Autowired
    TaskQueueService taskQueueService;
    @Autowired
    InstagramService instagramService;
    @Autowired
    FollowersService followersService;
    @Autowired
    IgUserService igUserService;

    @Override
    @Transactional
    public void executeTask(TaskQueue taskQueue, LoginAccount loginAccount) {
        log.info("開始執行任務:{} ,帳號:{}", taskQueue.getTaskConfig().getTaskType(), loginAccount);
        //登入、檢查結果並更新登入帳號狀態
        loginAndUpdateAccountStatus(loginAccount);
        //執行爬蟲任務
        performTaskWithAccount(taskQueue);
        //結束任務，依條件判斷更新任務狀態
        finalizeTask(taskQueue);
    }


    //private

    /**
     * 使用帳號執行任務
     *
     * @param task 任務
     */
    private void performTaskWithAccount(TaskQueue task) {
        instagramService.searchTargetUserFollowersAndSave(task, task.getNextIdForSearch());
    }

    /**
     * 結束任務判斷
     *
     * @param task 任務
     */
    private void finalizeTask(TaskQueue task) {
        updateTaskStatusBasedOnCondition(task);
        taskQueueService.save(task);
        log.info("任務已儲存:{}", task);
    }

    /**
     * 根據條件更新任務狀態
     *
     * @param task 任務
     */
    private void updateTaskStatusBasedOnCondition(TaskQueue task) {
        if (task.getNextIdForSearch() == null && checkFollowerAmount(task)) {
            task.completeTask();
        } else if (task.getNextIdForSearch() != null) {
            task.pauseTask();
        } else {
            task.pendingTask();
        }
    }

    /**
     * 檢查爬取數量是否已達到結束排成標準
     *
     * @param task 任務
     * @return 是否已達到結束任務的標準
     */
    private boolean checkFollowerAmount(TaskQueue task) {
        int crawlerAmount = followersService.countFollowersByIgUserName(task.getUserName());
        int dbAmount = igUserService.findUserByIgUserName(task.getUserName()).getFollowerCount();
        log.info("任務:{} ,取追蹤者數量:{},資料庫追蹤者數量:{}", task, dbAmount, crawlerAmount);
        return FollowerCrawlingUtil.isCrawlingCloseToRealFollowerCount(crawlerAmount, dbAmount);
    }
}
