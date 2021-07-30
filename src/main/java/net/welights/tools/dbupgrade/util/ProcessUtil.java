package net.welights.tools.dbupgrade.util;

import net.welights.tools.dbupgrade.common.StorageModuleVo;

import org.apache.commons.lang.StringUtils;

import java.util.List;

/**
 * @author welights
 */
public class ProcessUtil {
    public static boolean readyToContinue(String inputModule, List<StorageModuleVo> storageModuleVoList, List<String> requireUpgradeList) {
        if (StringUtils.isBlank(inputModule)) {
            for (int i = 0; i < storageModuleVoList.size(); i++) {
                requireUpgradeList.add(i + "");
            }
        } else if (StringUtils.isNumeric(inputModule)) {
            if (Integer.parseInt(inputModule) < storageModuleVoList.size()) {
                requireUpgradeList.add(inputModule);
                return true;
            } else {
                System.err.println("请输入正确的数字");
            }
        } else if (inputModule.contains(",")) {
            String[] numberArray = inputModule.split(",");
            for (String number : numberArray) {
                if (StringUtils.isNumeric(number) && Integer.parseInt(number) < storageModuleVoList.size()) {
                    requireUpgradeList.add(number);
                    return true;
                } else {
                    System.err.println("请输入正确的数字");
                }
            }
        } else if (inputModule.equals("q")) {
            System.exit(0);
        } else {
            System.err.println("请检查你的输入");
        }
        return false;
    }

}
