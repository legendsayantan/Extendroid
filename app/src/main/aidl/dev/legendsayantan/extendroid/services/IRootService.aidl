package dev.legendsayantan.extendroid.services;

import dev.legendsayantan.extendroid.IEventCallback;

interface IRootService {
    String executeCommand(String cmd);
    boolean isRunningAsShell();
    String launchAppOnDisplay(String packageName, int displayId);
    String grantPermissions(in List<String> perms);
    String exitTasks(String packageName);
    List<String> getTopTenApps();
    String dispatchKey(int keyCode,int action, int displayID, int metaState);
    String dispatch(in MotionEvent event, int displayID);
    String setBuiltInDisplayPowerMode(int mode);
    String registerMotionEventListener(IEventCallback callback);
    String unregisterMotionEventListener();
    boolean goToSleep();
    boolean isDisplayActive();
    boolean wakeUp();
    int createVirtualDisplay(String name, int width, int height, int dpi, in Surface surface);
    void resizeVirtualDisplay(int displayId, int width, int height, int dpi);
    void updateVirtualDisplaySurface(int displayId, in Surface surface);
    void destroyVirtualDisplay(int displayId);

    int dummy();
}