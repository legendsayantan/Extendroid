package dev.legendsayantan.extendroid.services;

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
    String registerMotionEventListener();
}