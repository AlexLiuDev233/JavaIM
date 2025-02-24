package org.yuezhikong.newServer.plugin.event.events;

import org.yuezhikong.newServer.UserData.user;
import org.yuezhikong.utils.CustomVar;

@SuppressWarnings("unused")
public class UserCommandEvent implements Event{
    private boolean Cancel = false;
    private final user UserData;
    private final CustomVar.Command Command;
    public UserCommandEvent(user UserData, CustomVar.Command Command)
    {
        this.UserData = UserData;
        this.Command = Command;
    }

    public void setCancel(boolean cancel) {
        Cancel = cancel;
    }

    public boolean isCancel() {
        return Cancel;
    }

    public user getUserData() {
        return UserData;
    }

    public CustomVar.Command getCommand() {
        return Command;
    }
}
