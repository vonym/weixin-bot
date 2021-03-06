package com.maweiming.wechat.bot.service;

import com.alibaba.fastjson.JSON;
import com.maweiming.wechat.bot.model.contact.ContactList;
import com.maweiming.wechat.bot.model.contact.ContactMemberModel;
import com.maweiming.wechat.bot.model.core.WechatCore;
import com.maweiming.wechat.bot.model.group.GroupModel;
import com.maweiming.wechat.bot.model.initialization.SyncKeyModel;
import com.maweiming.wechat.bot.model.login.LoginModel;
import com.maweiming.wechat.bot.model.scan.ScanCode;
import com.maweiming.wechat.bot.model.initialization.InitModel;
import com.maweiming.wechat.bot.model.initialization.UserModel;
import com.maweiming.wechat.bot.utils.IdGenerate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * maweiming.com
 * Copyright (C) 1994-2018 All Rights Reserved.
 *
 * @author CoderMa
 * @version WechatRunService.java, v 0.1 2018-10-31 00:46
 */
@Service
public class WechatRunService implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatRunService.class);

    @Autowired
    private WechatService wechatService;

    @Autowired
    private ListenMessageService listenMessageService;

    @Autowired
    private QRCodeService qrCodeService;

    @Override
    public void run(ApplicationArguments args) {
        String deviceId = IdGenerate.getDeviceId();
        //1、获取uuid
        String uuid = this.wechatService.getUUID();
        //2、渲染登陆二维码
        this.qrCodeService.show(uuid);
        //3、等待扫码
        ScanCode scanCode = null;
        while (null == scanCode) {
            //4、判断是否扫码
            scanCode = wechatService.waitForLogin(uuid, 1);
        }
        //5、关闭二维码窗口
        this.qrCodeService.close();
        //6、登陆
        LoginModel loginModel = wechatService.login(scanCode);
        if (null == loginModel) {
            return;
        }
        //save 设备id
        loginModel.setDeviceId(deviceId);
        //7、初始化数据
        InitModel initModel = wechatService.initialization(scanCode, loginModel);
        if (null == initModel) {
            return;
        }
        SyncKeyModel syncKey = initModel.getSyncKey();
        UserModel toUser = initModel.getToUser();
        //设置当前用户信息
        WechatCore.setToUserData(toUser);
        //8、开启状态通知
        String msgId = wechatService.statusNotify(scanCode, loginModel, toUser);
        //9、获取联系人列表
        ContactList contactList = this.wechatService.getContactList(scanCode, loginModel);
        if (null == contactList) {
            return;
        }
        LOGGER.info("contactLis={}", JSON.toJSONString(contactList));
        //设置联系人信息
        List<ContactMemberModel> friendList = contactList.getFriendList();
        List<ContactMemberModel> groupList = contactList.getGroupList();
        List<ContactMemberModel> publicList = contactList.getPublicList();
        List<ContactMemberModel> specialList = contactList.getSpecialList();
        WechatCore.setContactData(friendList);
        WechatCore.setContactData(groupList);
        WechatCore.setContactData(publicList);
        WechatCore.setContactData(specialList);
        LOGGER.info(String.format("你一共有%s个好友,加入了%s个群聊,关注了%s个公众号", friendList.size(), groupList.size(), publicList.size()));
        //10、获取群信息
        GroupModel groupInfo = wechatService.getGroupInfo(scanCode, loginModel, contactList);
        //设置群会员信息
        WechatCore.setContactData(groupInfo.getContactList());
        WechatCore.setScanCode(scanCode);
        WechatCore.setLoginModel(loginModel);
        WechatCore.setSyncKey(syncKey);
        //11、监听消息
        listenMessageService.listen(scanCode,loginModel,syncKey);
    }
}
