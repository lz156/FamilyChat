package com.lwk.familycontact.project.contact.view;

import com.lwk.familycontact.storage.db.user.UserBean;

import java.util.List;

/**
 * Created by LWK
 * TODO 通讯录界面View
 * 2016/8/9
 */
public interface ContactImpl
{
    void refreshAllUsersSuccess(List<UserBean> allUserList);

    void refreshAllUsersFail(int errorMsgId);

    void refreshContactNum();

    void scrollToTop();
}
