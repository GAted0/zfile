package im.zhaojun.common.controller;

import im.zhaojun.common.model.StorageConfig;
import im.zhaojun.common.model.dto.ResultBean;
import im.zhaojun.common.model.dto.SystemConfigDTO;
import im.zhaojun.common.model.enums.StorageTypeEnum;
import im.zhaojun.common.service.AbstractFileService;
import im.zhaojun.common.service.FileAsyncCacheService;
import im.zhaojun.common.service.StorageConfigService;
import im.zhaojun.common.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 后台管理
 * @author zhaojun
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Resource
    private StorageConfigService storageConfigService;

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private FileAsyncCacheService fileAsyncCacheService;

    /**
     * 获取系统配置
     */
    @GetMapping("/config")
    public ResultBean getConfig() {
        SystemConfigDTO systemConfigDTO = systemConfigService.getSystemConfig();
        return ResultBean.success(systemConfigDTO);
    }

    /**
     * 更新系统配置
     */
    @PostMapping("/config")
    public ResultBean updateConfig(SystemConfigDTO systemConfigDTO) throws Exception {
        StorageTypeEnum currentStorageStrategy = systemConfigService.getCurrentStorageStrategy();

        systemConfigDTO.setId(1);
        systemConfigService.updateSystemConfig(systemConfigDTO);

        if (!Objects.equals(currentStorageStrategy, systemConfigDTO.getStorageStrategy())) {
            log.info("已将存储策略由 {} 切换为 {}", currentStorageStrategy, systemConfigDTO.getStorageStrategy());
            refreshStorageStrategy();
        }

        return ResultBean.success();
    }

    /**
     * 修改管理员登陆密码
     */
    @PostMapping("/update-pwd")
    public ResultBean updatePwd(String username, String password) {
        systemConfigService.updateUsernameAndPwd(username, password);
        return ResultBean.success();
    }

    /**
     * 获取指定存储策略的设置
     * @param storageType   存储策略
     * @return              所有设置
     */
    @GetMapping("/strategy-form")
    public ResultBean getFormByStorageType(StorageTypeEnum storageType) {
        List<StorageConfig> storageConfigList = storageConfigService.selectStorageConfigByType(storageType);
        return ResultBean.success(storageConfigList);
    }


    /**
     * 保存存储策略
     * @param storageStrategyConfig     保存表单值
     * @param storageStrategy           所属策略
     * @return                          操作结果
     * @throws Exception                表单解析出错异常
     */
    @PostMapping("/storage-strategy")
    public ResultBean save(@RequestParam Map<String, String> storageStrategyConfig, StorageTypeEnum storageStrategy) throws Exception {
        List<StorageConfig> storageConfigList = storageConfigService.selectStorageConfigByType(storageStrategy);
        for (StorageConfig storageConfig : storageConfigList) {
            String key = storageConfig.getKey();
            String value = storageStrategyConfig.get(key);
            storageConfig.setValue(value);
        }
        storageConfigService.updateStorageConfig(storageConfigList);

        StorageTypeEnum currentStorageStrategy = systemConfigService.getCurrentStorageStrategy();
        if (Objects.equals(storageStrategy, currentStorageStrategy)) {
            if (log.isDebugEnabled()) {
                log.debug("检测到更新了当前启用的存储策略 {}, 已清理缓存.", currentStorageStrategy);
            }

            AbstractFileService fileService = systemConfigService.getCurrentFileService();
            fileService.clearFileCache();
            fileService.init();
            fileAsyncCacheService.cacheGlobalFile();
        }

        return ResultBean.success();
    }

    /**
     * 更新存储策略
     */
    public void refreshStorageStrategy() {
        StorageTypeEnum storageStrategy = systemConfigService.getCurrentStorageStrategy();
        refreshStorageStrategy(storageStrategy);
    }

    /**
     * 更新存储策略
     */
    public void refreshStorageStrategy(StorageTypeEnum storageStrategy) {
        if (storageStrategy == null) {
            log.info("尚未配置存储策略.");
        } else {
            AbstractFileService fileService = systemConfigService.getCurrentFileService();
            fileService.init();
            log.info("切换至存储类型: {}", storageStrategy.getDescription());

            fileAsyncCacheService.cacheGlobalFile();
        }
    }

}
